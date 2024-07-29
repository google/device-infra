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
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.shared.logging.MobileHarnessHostLogManager;
import com.google.devtools.mobileharness.shared.logging.MobileHarnessHostLogManagerModule;
import com.google.devtools.mobileharness.shared.logging.parameter.LogManagerParameters;
import com.google.devtools.mobileharness.shared.logging.parameter.LogProject;
import com.google.devtools.mobileharness.shared.logging.parameter.StackdriverLogUploaderParameters;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.logging.flogger.FloggerFormatter;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode.Lab;
import com.google.wireless.qa.mobileharness.shared.util.ArrayUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Launcher of lab server. */
public class LabServerLauncher {

  static {
    FloggerFormatter.initialize();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final SystemUtil SYSTEM_UTIL = new SystemUtil();

  private volatile LabServer labServer;

  private final String[] labArgs;
  private final EventBus globalInternalBus = new EventBus(new SubscriberExceptionLoggingHandler());

  private LabServerLauncher(String[] labArgs) {
    this.labArgs = labArgs;
  }

  /**
   * Entry point to start the lab server.
   *
   * @param args command line arguments, we are using flags only
   */
  public static void main(String[] args) throws InterruptedException, IOException {
    // Adds extra flags.
    List<String> extraArgs = new ArrayList<>();
    // Make sure the new ResUtil runs in a different directory.
    extraArgs.add("--udcluster_res_dir_name=mobileharness_res_files");
    String[] allLabArgs = ArrayUtil.join(extraArgs.toArray(new String[0]), args);

    // Parses flags.
    Flags.parse(allLabArgs);

    if (Flags.instance().printLabStats.getNonNull()) {
      System.out.println("version: " + Version.LAB_VERSION);
      SYSTEM_UTIL.exit(ExitCode.Shared.OK);
    }

    // Sleeps forever for no-op lab server.
    if (Flags.instance().noOpLabServer.getNonNull()) {
      Thread.currentThread().join();
    }

    try {
      // Initializes lab server.
      LabServerLauncher labServerLauncher = new LabServerLauncher(allLabArgs);

      // Adds shutdown hook.
      Runtime.getRuntime().addShutdownHook(new Thread(labServerLauncher::onShutdown));

      // Runs lab server.
      labServerLauncher.run();

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

  /** Initializes and runs lab server. */
  public void run() throws MobileHarnessException, InterruptedException, IOException {
    Injector injector = Guice.createInjector(new LabServerModule(labArgs, globalInternalBus));
    initializeEnv(injector);

    labServer.run();
  }

  private void onShutdown() {
    logger.atInfo().log("Lab server is shutting down.");
    labServer.onShutdown();
  }

  /** Initializes environment by setting appropriate env vars and setting logger. */
  private void initializeEnv(Injector injector) throws MobileHarnessException {
    LabServer.initializeEnv();
    labServer = injector.getInstance(LabServer.class);

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

    logger.atInfo().log("Lab server arguments: %s", Arrays.toString(labArgs));
  }
}
