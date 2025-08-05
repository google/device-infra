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

package com.google.devtools.mobileharness.infra.client.longrunningservice;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerDirs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.inject.CommonModule;
import com.google.devtools.mobileharness.shared.util.logging.flogger.FloggerFormatter;
import com.google.devtools.mobileharness.shared.util.signal.Signals;
import com.google.devtools.mobileharness.shared.util.system.SystemInfoPrinter;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.constant.DirPreparer;
import java.time.Instant;

/** OLC server. */
public class OlcServer {

  static {
    FloggerFormatter.initialize();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws MobileHarnessException, InterruptedException {
    // Parses flags.
    Flags.parse(args);

    // Creates the server runner.
    Instant serverStartTime = Instant.now();
    boolean enableDatabase = validateDatabase();
    Injector injector =
        Guice.createInjector(
            new OlcServerModule(
                Flags.instance().enableAtsMode.getNonNull(),
                serverStartTime,
                Flags.instance().enableCloudPubsubMonitoring.getNonNull(),
                enableDatabase,
                Flags.instance().enableGrpcRelay.getNonNull()),
            new CommonModule());
    OlcServerRunner serverRunner = injector.getInstance(OlcServerRunner.class);
    LogManager<LogRecords> logManager = injector.getInstance(new Key<>() {});
    SystemInfoPrinter systemInfoPrinter = injector.getInstance(SystemInfoPrinter.class);

    // Prepares dirs.
    DirPreparer.prepareDirRoots();

    // Initializes logger.
    MobileHarnessLogger.init(
        OlcServerDirs.getLogDir(),
        ImmutableList.of(logManager.getLogHandler()),
        /* disableConsoleHandler= */ false);

    // Initializes log recorder.
    LogRecorder.getInstance().initialize(logManager.getLogRecorderBackend());

    // Logs arguments and system info.
    if (args.length > 0) {
      logger.atInfo().log("Args: %s", args);
    }
    systemInfoPrinter.printSystemInfo(DEBUG);

    // Adds shutdown hook.
    Runtime.getRuntime().addShutdownHook(new Thread(serverRunner::onShutdown));

    // Monitors known signals.
    if (Flags.instance().monitorSignals.getNonNull()) {
      Signals.monitorKnownSignals();
    }

    // Runs the server in standalone mode.
    serverRunner.runBasic();
    serverRunner.runStandaloneMode();
    serverRunner.awaitTermination();

    // Exits the server.
    logger.atInfo().log("Exiting...");
    System.exit(0);
  }

  /** Validates whether the database can be enabled. */
  private static boolean validateDatabase() {
    String jdbcUrl = Flags.instance().olcDatabaseJdbcUrl.get();
    if (Strings.isNullOrEmpty(jdbcUrl)) {
      return false;
    }
    if (jdbcUrl.startsWith("jdbc:mysql:")) {
      if (!checkClassExist("com.mysql.jdbc.Driver")) {
        return false;
      }
    }

    String socketFactoryClassName =
        Flags.instance().olcDatabaseJdbcProperty.getNonNull().get("socketFactory");
    if (!Strings.isNullOrEmpty(socketFactoryClassName)) {
      if (!checkClassExist(socketFactoryClassName)) {
        return false;
      }
    }

    return true;
  }

  private static boolean checkClassExist(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException e) {
      logger.atWarning().withCause(e).log("Failed to load class %s.", className);
      return false;
    }
  }
}
