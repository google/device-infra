/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess;

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.PluginWorkerServiceGrpc;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.PluginWorkerServiceGrpc.PluginWorkerServiceBlockingStub;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.ShutdownRequest;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/** Manages the lifecycle of an isolated plugin worker subprocess and its gRPC channel. */
public final class SubprocessPluginLauncher implements AutoCloseable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(1);
  private static final String WORKER_MAIN_CLASS =
      "com.google.devtools.mobileharness.infra.controller.plugin.subprocess.worker.PluginWorkerMain";

  private final ImmutableList<String> pluginJarPaths;
  @Nullable private final ImmutableList<String> pluginClasses;
  @Nullable private final String customWorkerRunnerJar;
  @Nullable private final String hermeticWorkerBinary;

  private CommandProcess process;
  private ManagedChannel channel;
  private PluginWorkerServiceBlockingStub stub;

  public SubprocessPluginLauncher(
      Collection<String> pluginJarPaths,
      @Nullable Collection<String> pluginClasses,
      @Nullable String customWorkerRunnerJar) {
    this(pluginJarPaths, pluginClasses, customWorkerRunnerJar, /* hermeticWorkerBinary= */ null);
  }

  public SubprocessPluginLauncher(
      Collection<String> pluginJarPaths,
      @Nullable Collection<String> pluginClasses,
      @Nullable String customWorkerRunnerJar,
      @Nullable String hermeticWorkerBinary) {
    this.pluginJarPaths = ImmutableList.copyOf(pluginJarPaths);
    this.pluginClasses = pluginClasses == null ? null : ImmutableList.copyOf(pluginClasses);
    this.customWorkerRunnerJar = customWorkerRunnerJar;
    this.hermeticWorkerBinary = hermeticWorkerBinary;
  }

  /** Starts the worker subprocess and establishes a gRPC client connection. */
  public synchronized void start() throws MobileHarnessException {
    if (process != null) {
      return;
    }

    SystemUtil systemUtil = new SystemUtil();
    List<String> commandArgs = new ArrayList<>();

    boolean useHermeticWorker =
        !Strings.isNullOrEmpty(hermeticWorkerBinary)
            && new File(hermeticWorkerBinary).exists()
            && systemUtil.isOnLinux();

    if (useHermeticWorker) {
      commandArgs.add(hermeticWorkerBinary);
      commandArgs.add("--port=0");
      if (pluginClasses != null && !pluginClasses.isEmpty()) {
        commandArgs.add("--plugin_classes=" + Joiner.on(',').join(pluginClasses));
      }
      if (!pluginJarPaths.isEmpty()) {
        commandArgs.add("--plugin_jars=" + Joiner.on(',').join(pluginJarPaths));
      }
    } else {
      String javaBin = systemUtil.getJavaBin();

      List<String> classpathElements = new ArrayList<>();
      if (!Strings.isNullOrEmpty(customWorkerRunnerJar)) {
        classpathElements.add(customWorkerRunnerJar);
      } else {
        String currentClasspath = JAVA_CLASS_PATH.value();
        if (!Strings.isNullOrEmpty(currentClasspath)) {
          classpathElements.add(currentClasspath);
        }
      }
      classpathElements.addAll(pluginJarPaths);

      String combinedClasspath = Joiner.on(File.pathSeparator).join(classpathElements);

      commandArgs.add(javaBin);
      commandArgs.add("-cp");
      commandArgs.add(combinedClasspath);
      commandArgs.add(WORKER_MAIN_CLASS);
      commandArgs.add("--port=0");
      if (pluginClasses != null && !pluginClasses.isEmpty()) {
        commandArgs.add("--plugin_classes=" + Joiner.on(',').join(pluginClasses));
      }
      if (!pluginJarPaths.isEmpty()) {
        commandArgs.add("--plugin_jars=" + Joiner.on(',').join(pluginJarPaths));
      }
    }

    SettableFuture<Integer> portFuture = SettableFuture.create();
    StringBuilder output = new StringBuilder();

    Command command =
        Command.of(commandArgs)
            .redirectStderr(true)
            .onStdout(
                LineCallback.does(
                    line -> {
                      output.append(line).append('\n');
                      if (line.startsWith("SERVER_STARTED_PORT=")) {
                        try {
                          int port =
                              Integer.parseInt(
                                  line.substring("SERVER_STARTED_PORT=".length()).trim());
                          portFuture.set(port);
                        } catch (NumberFormatException e) {
                          portFuture.setException(e);
                        }
                      }
                    }))
            .onExit(
                result -> {
                  if (!portFuture.isDone()) {
                    portFuture.setException(
                        new MobileHarnessException(
                            BasicErrorId.NON_MH_EXCEPTION,
                            "Plugin worker subprocess terminated prematurely with exit code "
                                + result.exitCode()
                                + ". Process output:\n"
                                + output));
                  }
                });

    try {
      logger.atInfo().log("Launching plugin worker subprocess (%s)", WORKER_MAIN_CLASS);
      logger.atFine().log("Plugin worker command: %s", command);
      process = new CommandExecutor().start(command);

      int boundPort = portFuture.get(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      logger.atInfo().log("Plugin worker subprocess started successfully on port %d", boundPort);
      process.stopReadingOutput();

      channel =
          Grpc.newChannelBuilderForAddress(
                  "127.0.0.1", boundPort, InsecureChannelCredentials.create())
              .build();
      stub = PluginWorkerServiceGrpc.newBlockingStub(channel);
    } catch (TimeoutException e) {
      if (process != null) {
        process.killForcibly();
      }
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION,
          "Timed out waiting for plugin worker subprocess to start within "
              + STARTUP_TIMEOUT.toMillis()
              + "ms. Process output:\n"
              + output,
          e);
    } catch (ExecutionException e) {
      if (process != null) {
        process.killForcibly();
      }
      Throwable cause = e.getCause();
      if (cause instanceof MobileHarnessException mobileHarnessException) {
        throw mobileHarnessException;
      }
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION,
          "Failed to launch plugin worker subprocess",
          cause != null ? cause : e);
    } catch (InterruptedException e) {
      if (process != null) {
        process.killForcibly();
      }
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION, "Interrupted while launching plugin worker subprocess", e);
    } catch (MobileHarnessException e) {
      if (process != null) {
        process.killForcibly();
      }
      throw e;
    }
  }

  /** Returns the gRPC blocking stub for communicating with the worker subprocess. */
  public synchronized PluginWorkerServiceBlockingStub getStub() throws MobileHarnessException {
    if (stub == null) {
      start();
    }
    return stub;
  }

  @Override
  public synchronized void close() {
    if (stub != null) {
      try {
        stub.withDeadlineAfter(Duration.ofSeconds(2))
            .shutdown(ShutdownRequest.getDefaultInstance());
      } catch (RuntimeException e) {
        // Ignore errors during shutdown request
      }
      stub = null;
    }
    if (channel != null) {
      channel.shutdownNow();
      channel = null;
    }
    if (process != null) {
      try {
        process.await(Duration.ofSeconds(2));
      } catch (CommandException | TimeoutException e) {
        process.killForcibly();
      } catch (InterruptedException e) {
        process.killForcibly();
        Thread.currentThread().interrupt();
      }
      process = null;
    }
  }
}
