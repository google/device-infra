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

package com.google.devtools.mobileharness.infra.lab;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ServiceManager;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.constant.BuiltinFlags;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.shared.logging.MobileHarnessHostLogManager;
import com.google.devtools.mobileharness.shared.logging.MobileHarnessHostLogManagerModule;
import com.google.devtools.mobileharness.shared.logging.parameter.LogManagerParameters;
import com.google.devtools.mobileharness.shared.logging.parameter.LogProject;
import com.google.devtools.mobileharness.shared.logging.parameter.StackdriverLogUploaderParameters;
import com.google.devtools.mobileharness.shared.util.concurrent.ExternalServiceManager;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.logging.flogger.FloggerFormatter;
import com.google.devtools.mobileharness.shared.util.system.ShutdownHookManager;
import com.google.devtools.mobileharness.shared.util.system.SystemPropertiesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode.Lab;

/** Launcher of lab server. */
public class LabServerLauncher {

  static {
    FloggerFormatter.initialize();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final SystemUtil SYSTEM_UTIL = new SystemUtil();

  public static void main(String[] args) throws InterruptedException {
    // Pre-processes main arguments.
    ImmutableList<String> mainArgs = preprocessMainArgs(args);

    // Parses flags.
    Flags.parse(mainArgs);

    // Initializes system properties.
    LabServer.initSystemProperties();

    // Gets system properties.
    ImmutableMap<String, String> systemProperties = SystemPropertiesUtil.getSystemProperties();

    // Runs in print_lab_stats mode.
    if (Flags.instance().printLabStats.getNonNull()) {
      System.out.println("version: " + Version.LAB_VERSION);
      SYSTEM_UTIL.exit(ExitCode.Shared.OK);
    }

    // Runs in no_op_lab_server mode.
    if (Flags.instance().noOpLabServer.getNonNull()) {
      Thread.currentThread().join();
    }

    try {
      // Initializes logger.
      LabServer.initLogger();

      // Initializes cloud logging.
      initializeCloudLogging();

      // Prints main arguments.
      logger.atInfo().log("Lab server arguments: %s", mainArgs);

      // Creates global internal bus.
      EventBus globalInternalBus = new EventBus(new SubscriberExceptionLoggingHandler());

      // Creates injector.
      ImmutableList<Module> modules = createModules(mainArgs, systemProperties, globalInternalBus);
      Injector injector = Guice.createInjector(modules);

      // Creates lab server instance.
      LabServer labServer = injector.getInstance(LabServer.class);

      // Adds shutdown hook.
      ShutdownHookManager.getInstance()
          .addShutdownHook(labServer::onShutdown, "lab-server-shutdown");

      // Starts external services.
      ServiceManager externalServiceManager =
          injector.getInstance(Key.get(ServiceManager.class, ExternalServiceManager.class));
      externalServiceManager.startAsync().awaitHealthy();

      // Runs lab server.
      labServer.run();

      // Sometimes some unexpected non-daemon threads may be blocked here, so we use System.exit(0)
      // to make sure the process is terminated.
      SYSTEM_UTIL.exit(ExitCode.Shared.OK);
    } catch (MobileHarnessException | RuntimeException | Error e) {
      SYSTEM_UTIL.exit(
          Lab.INIT_ERROR,
          String.format(
              "Lab Server Failed to Start!!!\nError Message: %s\nStack Trace: %s",
              e.getMessage(), Throwables.getStackTraceAsString(e)));
    }
  }

  /** Pre-processes main arguments. */
  private static ImmutableList<String> preprocessMainArgs(String[] args) {
    ImmutableList.Builder<String> mainArgs = ImmutableList.builder();

    // Adds built-in flags for ATS lab server.
    String atsLabServerType = System.getProperty(BuiltinFlags.ATS_LAB_SERVER_TYPE_PROPERTY_KEY);
    if (atsLabServerType != null) {
      ImmutableList<String> atsLabServerBuiltinFlags =
          BuiltinFlags.atsLabServerFlags(atsLabServerType);
      mainArgs.addAll(atsLabServerBuiltinFlags);
    }

    return mainArgs.add(args).build();
  }

  private static ImmutableList<Module> createModules(
      ImmutableList<String> mainArgs,
      ImmutableMap<String, String> systemProperties,
      EventBus globalInternalBus)
      throws MobileHarnessException {
    ImmutableList.Builder<Module> modules = ImmutableList.builder();
    modules.add(
        new LabServerModule(mainArgs, System.getenv(), systemProperties, globalInternalBus));
    return modules.build();
  }

  private static void initializeCloudLogging() {
    if (Flags.instance().enableCloudLogging.getNonNull()) {
      MobileHarnessHostLogManager mobileHarnessHostLogManager =
          Guice.createInjector(
                  new MobileHarnessHostLogManagerModule(
                      LogManagerParameters.newBuilder()
                          .setLogProject(LogProject.LAB_SERVER)
                          .setLogUploaderParameters(
                              StackdriverLogUploaderParameters.of(LogProject.LAB_SERVER))
                          .build()))
              .getInstance(MobileHarnessHostLogManager.class);
      try {
        mobileHarnessHostLogManager.init();
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to initialize the MobileHarnessHostLogManager.");
        // Does nothing
      }
    }
  }

  // NoOpService is used so the ServiceManager has at least one service when starting up. Otherwise,
  // it will write an exception to stdout, which is forbidden in the ATS tests.
  private static class NoOpService extends AbstractIdleService {
    @Override
    protected void startUp() {}

    @Override
    protected void shutDown() {}
  }
}
