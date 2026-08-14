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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess.worker;

import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.plugin.PluginLoader;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestMessageDetail;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone entry point for the Mobile Harness isolated plugin worker process. */
public final class PluginWorkerMain {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws Exception {
    Thread stdinWatchdog =
        new Thread(
            () -> {
              try {
                while (System.in.read() != -1) {}
              } catch (java.io.IOException e) {
                // Stdin closed
              }
              System.exit(0);
            },
            "subprocess-plugin-worker-watchdog");
    stdinWatchdog.setDaemon(true);
    stdinWatchdog.start();

    int port = 0;
    List<String> explicitPluginClasses = new ArrayList<>();
    List<String> pluginJars = new ArrayList<>();
    PluginType pluginType = PluginType.LAB;

    for (String arg : args) {
      if (arg.startsWith("--port=")) {
        port = Integer.parseInt(arg.substring("--port=".length()));
      } else if (arg.startsWith("--plugin_classes=")) {
        explicitPluginClasses.addAll(
            Splitter.on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(arg.substring("--plugin_classes=".length())));
      } else if (arg.startsWith("--plugin_jars=")) {
        pluginJars.addAll(
            Splitter.on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(arg.substring("--plugin_jars=".length())));
      } else if (arg.startsWith("--plugin_type=")) {
        pluginType = PluginType.valueOf(arg.substring("--plugin_type=".length()));
      }
    }

    List<TestMessageDetail> outgoingMessages = Collections.synchronizedList(new ArrayList<>());
    try {
      TestMessageManager.createInstance(
          testId ->
              Optional.of(
                  new TestMessagePoster() {
                    @Override
                    public void postTestMessage(TestMessageInfo testMessageInfo) {
                      outgoingMessages.add(
                          TestMessageDetail.newBuilder()
                              .setRootTestId(testMessageInfo.rootTestId())
                              .addAllSubTestIdChain(testMessageInfo.subTestIdChain())
                              .putAllMessage(testMessageInfo.message())
                              .build());
                    }

                    @Override
                    public String getTestId() {
                      return testId;
                    }
                  }));
    } catch (IllegalStateException e) {
      // Already created
    }

    List<Object> pluginInstances = loadPlugins(explicitPluginClasses, pluginJars, pluginType);
    logger.atInfo().log("Loaded %d plugin instances in worker process", pluginInstances.size());

    WorkerEventDispatcher dispatcher = new WorkerEventDispatcher(pluginInstances, outgoingMessages);

    AtomicReference<Server> serverHolder = new AtomicReference<>();
    PluginWorkerServiceImpl service =
        new PluginWorkerServiceImpl(
            dispatcher,
            () -> {
              logger.atInfo().log("Shutting down worker gRPC server...");
              Server runningServer = serverHolder.get();
              if (runningServer != null) {
                runningServer.shutdown();
              }
            });

    Server server =
        Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
            .addService(service)
            .build()
            .start();
    serverHolder.set(server);

    int boundPort = server.getPort();
    logger.atInfo().log("Plugin worker started on port %d", boundPort);

    // Write handshake token to standard output for the parent process to connect
    System.out.println("SERVER_STARTED_PORT=" + boundPort);
    System.out.flush();

    server.awaitTermination();
    logger.atInfo().log("Plugin worker process exiting cleanly.");
  }

  private static List<Object> loadPlugins(
      List<String> explicitClasses, List<String> pluginJars, PluginType targetType) {
    List<Object> instances = new ArrayList<>();
    Injector injector = Guice.createInjector();

    ClassLoader pluginClassLoader = PluginWorkerMain.class.getClassLoader();
    if (!pluginJars.isEmpty()) {
      try {
        pluginClassLoader = PluginLoader.createClassLoader(pluginJars, pluginClassLoader);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to create classloader for plugin jars: %s", pluginJars);
      }
    }

    if (!explicitClasses.isEmpty()) {
      for (String className : explicitClasses) {
        try {
          Class<?> clazz = Class.forName(className, true, pluginClassLoader);
          instances.add(injector.getInstance(clazz));
          logger.atInfo().log("Instantiated explicit plugin class: %s", className);
        } catch (RuntimeException | LinkageError | ReflectiveOperationException e) {
          logger.atWarning().withCause(e).log("Failed to instantiate plugin class: %s", className);
        }
      }
      return instances;
    }

    // Auto-scan specified plugin jars in order for @Plugin classes matching targetType
    for (String jarPath : pluginJars) {
      ClassGraph classGraph =
          new ClassGraph()
              .enableAnnotationInfo()
              .ignoreClassVisibility()
              .overrideClassLoaders(pluginClassLoader)
              .overrideClasspath(jarPath);
      try (ScanResult scanResult = classGraph.scan()) {
        for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Plugin.class.getName())) {
          try {
            Class<?> clazz = classInfo.loadClass();
            Plugin annotation = clazz.getAnnotation(Plugin.class);
            if (annotation != null
                && (annotation.type() == targetType
                    || annotation.type() == PluginType.UNSPECIFIED)) {
              instances.add(injector.getInstance(clazz));
              logger.atInfo().log(
                  "Discovered and instantiated @Plugin class: %s from %s",
                  clazz.getName(), jarPath);
            }
          } catch (RuntimeException | LinkageError e) {
            logger.atWarning().withCause(e).log(
                "Failed to instantiate plugin class: %s", classInfo.getName());
          }
        }
      }
    }

    return instances;
  }

  private PluginWorkerMain() {}
}
