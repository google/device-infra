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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/**
 * Starts an LSNexus service for each DUT and adds the service address to the test properties.
 *
 * <p>LSNexus is a local service that provides DUT information to other tools. This decorator
 * ensures that an LSNexus instance is running for each device under test and that its address is
 * available to the test via test properties.
 */
@DecoratorAnnotation(help = "Starts LSNexus service for each DUT.")
public class CrosLsNexusDecorator extends CrosBaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String LSNEXUS_PORT_FILE = "lsnexus_port";
  private static final Duration LSNEXUS_STARTUP_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration LSNEXUS_PORT_FILE_POLL_INTERVAL = Duration.ofSeconds(2);

  private final CommandExecutor commandExecutor;

  /** Map of LSNexus ports to their running processes. */
  private final Map<Integer, CommandProcess> lsnexusServices = new HashMap<>();

  /**
   * @param driver the decorated driver that this decorator wraps
   * @param testInfo the context of the current test, used for logging and property management
   * @param commandExecutor for starting the LSNexus service as a background process
   */
  @Inject
  CrosLsNexusDecorator(Driver driver, TestInfo testInfo, CommandExecutor commandExecutor) {
    super(driver, testInfo);
    this.commandExecutor = commandExecutor;
  }

  /**
   * Starts an LSNexus service for the DUT and adds its address to the test properties.
   *
   * <p>This method orchestrates the setup of an LSNexus service for the DUT. The process involves
   * the following steps:
   *
   * <ol>
   *   <li>Determines the log directory for the LSNexus service.
   *   <li>Starts the LSNexus service as a background process, configured to use a random available
   *       port.
   *   <li>Waits for the service to write its assigned port to a file in the log directory.
   *   <li>Stores the service address in the test properties for other tools to use.
   * </ol>
   *
   * @param testInfo the context of the current test, used to store the LSNexus service address as a
   *     test property
   * @throws MobileHarnessException if there is an error during service creation or initialization.
   * @throws InterruptedException if the thread is interrupted during service startup.
   */
  @Override
  public void prepare(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("CrosLsNexusDecorator is running on device: %s", getDevice().getDeviceUuid());

    final String deviceName = deviceName(deviceId());
    testInfo.log().atInfo().alsoTo(logger).log("Creating LSNexus service for DUT: %s", deviceName);

    final String serviceLogDir = createLsNexusServiceLogDir(testInfo, deviceName);
    final CommandProcess service = startLsNexusService(testInfo, deviceName, serviceLogDir);
    final int servicePort =
        readPortFromLsNexusService(testInfo, service, serviceLogDir, LSNEXUS_STARTUP_TIMEOUT);

    lsnexusServices.put(servicePort, service);
    final String serviceAddress = lsnexusAddress(servicePort);
    testInfo.log().atInfo().alsoTo(logger).log("LSNexus service started: %s", serviceAddress);
    // Populate service address for device config generation.
    testInfo.properties().add(LSNEXUS_PARAM_SUFFIX + deviceName, serviceAddress);
  }

  /**
   * Stops all LSNexus service instances started by this decorator.
   *
   * <p>This method stops each LSNexus service instance, terminating the underlying process to
   * ensure all resources are properly released.
   *
   * @param testInfo the context of the current test, used for logging service shutdown messages
   */
  @Override
  protected void tearDown(TestInfo testInfo) {
    for (Map.Entry<Integer, CommandProcess> entry : lsnexusServices.entrySet()) {
      final int port = entry.getKey();
      final CommandProcess process = entry.getValue();
      process.stop();
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Shutting down LSNexus service : %s", lsnexusAddress(port));
    }
    lsnexusServices.clear();
  }

  /**
   * Determines the directory path for LSNexus service logs.
   *
   * @param testInfo the context of the current test, used to determine the base directory for logs
   * @param dutName the name of the DUT for which the service is being created
   * @return the absolute path of the log directory
   * @throws MobileHarnessException if the base generated file directory cannot be retrieved or
   *     created.
   */
  String createLsNexusServiceLogDir(TestInfo testInfo, String dutName)
      throws MobileHarnessException {
    final String logDir = String.format("%s/lsnexus_%s", testInfo.getGenFileDir(), dutName);
    testInfo.log().atInfo().alsoTo(logger).log("Collecting LSNexus logs in: %s", logDir);
    return logDir;
  }

  /**
   * Starts an LSNexus service instance for a given DUT.
   *
   * <p>This method constructs the command to start the LSNexus service and starts it as a
   * background process.
   *
   * @param testInfo the test context, used for logging
   * @param dutName the name of the DUT for which the service is being created
   * @param logDir the directory where the LSNexus service will write its logs and port file
   * @return a {@link CommandProcess} for the running service
   * @throws MobileHarnessException if there is an error during command execution.
   */
  @VisibleForTesting
  CommandProcess startLsNexusService(TestInfo testInfo, String dutName, String logDir)
      throws MobileHarnessException {
    final List<String> args = new ArrayList<>();
    args.add(LSNEXUS_CIPD_PATH);
    args.add("server");
    args.add("-port");
    args.add("0");
    args.add("-logs");
    args.add(logDir);
    args.add("-dut");
    args.add(dutName);
    args.add("-labservice");
    args.add(getInventoryServiceAddress());
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Starting LSNexus with command: %s", String.join(" ", args));

    return commandExecutor.start(Command.of(args));
  }

  /**
   * Waits for the LSNexus service to start and create a port file, then reads the port from it.
   *
   * <p>The LSNexus service, when started with port 0, will pick an available port and write it to a
   * file named {@link #LSNEXUS_PORT_FILE} in its log directory. This method polls for the existence
   * of that file, reads the port, and returns it.
   *
   * @param testInfo the test context, used for logging
   * @param service the running LSNexus service process
   * @param logDir the directory where the LSNexus service writes its port file
   * @param timeout the maximum duration to wait for the port file to be created and read
   * @return the port number on which the LSNexus service is listening
   * @throws MobileHarnessException if the port file is not created within the timeout, or if the
   *     port cannot be read or parsed
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  int readPortFromLsNexusService(
      TestInfo testInfo, CommandProcess service, String logDir, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    final String portFile = Path.of(logDir, LSNEXUS_PORT_FILE).toString();
    final Instant deadline = Instant.now().plus(timeout);
    final LocalFileUtil fileUtil = new LocalFileUtil();

    while (Instant.now().isBefore(deadline)) {
      if (Files.exists(Path.of(portFile))) {
        try {
          final String portStr = fileUtil.readFile(portFile).trim();
          if (!portStr.isEmpty()) {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("LSNexus port file found. Port: %s", portStr);
            return Integer.parseInt(portStr);
          }
        } catch (MobileHarnessException | NumberFormatException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .withCause(e)
              .log("Failed to read or parse LSNexus port from file: %s. Retrying...", portFile);
        }
      }
      Thread.sleep(LSNEXUS_PORT_FILE_POLL_INTERVAL.toMillis());
    }

    // Timeout reached
    service.kill();
    throw new MobileHarnessException(
        BasicErrorId.SYSTEM_PICK_USED_PORT_ERROR,
        String.format(
            "LSNexus service failed to start and create port file %s within %s.",
            portFile, LSNEXUS_STARTUP_TIMEOUT));
  }

  /** Returns the address of the LSNexus service, formatted as "localhost:port". */
  String lsnexusAddress(int port) {
    return String.format("localhost:%d", port);
  }
}
