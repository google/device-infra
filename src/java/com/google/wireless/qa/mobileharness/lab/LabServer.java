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

package com.google.wireless.qa.mobileharness.lab;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.infra.lab.UnifiedTestRunServer;
import com.google.devtools.mobileharness.infra.lab.UnifiedTestRunServerModule;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode.Lab;
import com.google.wireless.qa.mobileharness.shared.util.ArrayUtil;
import java.util.ArrayList;
import java.util.List;

/** Starting point of a MobileHarness lab server. */
public class LabServer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final SystemUtil SYSTEM_UTIL = new SystemUtil();

  private volatile UnifiedTestRunServer utrs;

  private final String[] labArgs;
  private final EventBus globalInternalBus;

  private LabServer(String[] labArgs) {
    this(labArgs, new EventBus(new SubscriberExceptionLoggingHandler()));
  }

  @VisibleForTesting
  LabServer(EventBus globalInternalBus) {
    this(new String[0], globalInternalBus);
  }

  private LabServer(String[] labArgs, EventBus globalInternalBus) {
    this.labArgs = labArgs;
    this.globalInternalBus = globalInternalBus;
  }

  /**
   * Entry point to start the lab server.
   *
   * @param args command line arguments, we are using flags only
   */
  public static void main(String[] args) throws InterruptedException {
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

    try {
      // Initializes lab server.
      LabServer labServer = new LabServer(allLabArgs);

      // Adds shutdown hook.
      Runtime.getRuntime().addShutdownHook(new Thread(labServer::onShutdown));

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

  /** Initializes and runs lab server. */
  public void run() throws MobileHarnessException, InterruptedException {
    initializeEnv();

    utrs.run();
    logger.atInfo().log("Lab Server successfully started");
  }

  private void onShutdown() {
    logger.atInfo().log("Lab server is shutting down.");
    utrs.onShutdown();
  }

  /** Initializes environment by setting appropriate env vars and setting logger. */
  private void initializeEnv() throws MobileHarnessException {
    Injector injector =
        Guice.createInjector(new UnifiedTestRunServerModule(labArgs, globalInternalBus));
    UnifiedTestRunServer.initializeEnv();
    utrs = injector.getInstance(UnifiedTestRunServer.class);
  }
}
