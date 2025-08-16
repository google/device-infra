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
import com.google.devtools.mobileharness.shared.util.port.PortPicker;
import com.google.devtools.mobileharness.shared.util.port.UnableToFindPortException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Starts an LSNexus service for each DUT and adds the service address to the test properties. */
@DecoratorAnnotation(help = "Starts LSNexus service for each DUT.")
public class CrosLsNexusDecorator extends CrosBaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CommandExecutor commandExecutor;
  private final PortPicker portPicker;

  /** Map of LSNexus ports to their running processes. */
  private final Map<Integer, CommandProcess> lsnexusProcesses = new HashMap<>();

  /**
   * Constructs a new {@link CrosLsNexusDecorator}.
   *
   * @param driver the decorated driver that this decorator wraps
   * @param testInfo The context of the current test, used for logging and property management.
   * @param commandExecutor for starting the LSNexus service as a background process
   * @param portPicker for finding an unused port for the LSNexus service
   */
  @Inject
  CrosLsNexusDecorator(
      Driver driver, TestInfo testInfo, CommandExecutor commandExecutor, PortPicker portPicker) {
    super(driver, testInfo);
    this.commandExecutor = commandExecutor;
    this.portPicker = portPicker;
  }

  /**
   * Starts an LSNexus service for the DUT and adds its address to the test properties.
   *
   * <p>This method orchestrates the setup of an LSNexus service for the DUT associated with the
   * current device. The process involves the following steps:
   *
   * <ol>
   *   <li>Picks an unused port and starts an LSNexus service instance for the DUT.
   *   <li>Initializes the LSNexus service with the DUT information.
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

    String deviceName = deviceName(deviceId());
    testInfo.log().atInfo().alsoTo(logger).log("Creating LSNexus service for DUT: %s", deviceName);

    int lsnexusPort;
    try {
      lsnexusPort = portPicker.pickUnusedPort();
    } catch (UnableToFindPortException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_PICK_UNUSED_PORT_ERROR,
          "Unable to find an unused port for the LSNexus service.",
          e);
    }
    final CommandProcess process = startLsNexusService(testInfo, deviceName, lsnexusPort);
    lsnexusProcesses.put(lsnexusPort, process);
    var serviceAddress = lsnexusAddress(lsnexusPort);
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
    for (Map.Entry<Integer, CommandProcess> entry : lsnexusProcesses.entrySet()) {
      int port = entry.getKey();
      CommandProcess process = entry.getValue();
      process.stop();
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Shutting down LSNexus service : %s", lsnexusAddress(port));
    }
    lsnexusProcesses.clear();
  }

  /**
   * Starts an LSNexus service instance for a given DUT.
   *
   * <p>This method constructs the command to start the LSNexus service and starts it. It then
   * returns a {@link CommandProcess} object for the running service.
   *
   * @param testInfo the test context, used for logging and to determine the log directory for the
   *     LSNexus service
   * @param dutName the name of the DUT for which the service is being created
   * @param lsnexusPort the port to be used by the LSNexus service
   * @return a {@link CommandProcess} for the running service
   * @throws MobileHarnessException if there is an error during command execution.
   */
  @VisibleForTesting
  CommandProcess startLsNexusService(TestInfo testInfo, String dutName, int lsnexusPort)
      throws MobileHarnessException {

    String logDir = String.format("%s/lsnexus_%s", testInfo.getGenFileDir(), dutName);
    testInfo.log().atInfo().alsoTo(logger).log("Collecting LSNexus logs in: %s", logDir);

    List<String> args = new ArrayList<>();
    args.add(LSNEXUS_CIPD_PATH);
    args.add("server");
    args.add("-port");
    args.add(String.valueOf(lsnexusPort));
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

  /** Returns the address of the LSNexus service, formatted as "localhost:port". */
  String lsnexusAddress(Integer port) {
    return String.format("localhost:%d", port);
  }
}
