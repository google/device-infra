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

import static com.google.devtools.mobileharness.shared.util.port.PortProber.pickUnusedPort;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Decorator for starting LSNexus service for each DUT and populating test info properties with
 * service details.
 */
@DecoratorAnnotation(help = "Starts LSNexus service for each DUT.")
public final class CrosLsNexusDecorator extends CrosBaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CommandExecutor commandExecutor;
  private final List<ServiceInfo> lsnexusServices = new ArrayList<>();
  private final Provider<ServiceInfo.Builder> serviceInfoBuilderProvider;

  /**
   * Constructs a new LSNexus decorator instance.
   *
   * @param driver The decorated driver that this decorator wraps.
   * @param testInfo The context of the current test, used for logging and property management.
   * @param commandExecutor The executor for starting the LSNexus service as a background process.
   * @param serviceInfoBuilderProvider A provider for creating {@link ServiceInfo.Builder} objects
   *     to track running LSNexus instances.
   */
  @Inject
  CrosLsNexusDecorator(
      Driver driver,
      TestInfo testInfo,
      CommandExecutor commandExecutor,
      Provider<ServiceInfo.Builder> serviceInfoBuilderProvider) {
    super(driver, testInfo);
    this.commandExecutor = commandExecutor;
    this.serviceInfoBuilderProvider = serviceInfoBuilderProvider;
  }

  /**
   * Sets up an LSNexus service for the DUT before running the decorated driver.
   *
   * <p>This method orchestrates the setup of an LSNexus service for the DUT associated with the
   * current device. The process involves the following steps:
   *
   * <ol>
   *   <li>Picks an unused port and starts an LSNexus service instance for the DUT.
   *   <li>Adds the address of the LSNexus service to the test properties.
   *   <li>Initializes the LSNexus service with the DUT information.
   * </ol>
   *
   * @param testInfo The context of the current test, used to store the LSNexus service address as a
   *     test property.
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

    final ServiceInfo serviceInfo = startLsNexusService(testInfo, deviceName);
    lsnexusServices.add(serviceInfo);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("LSNexus service started: %s", serviceInfo.getAddress());
    // Populate service address for device config generation.
    testInfo.properties().add(LSNEXUS_PARAM_SUFFIX + deviceName, serviceInfo.getAddress());
  }

  /**
   * Tears down LSNexus services after test execution is done.
   *
   * <p>This method stops each LSNexus service instance, terminating the underlying process to
   * ensure all resources are properly released.
   *
   * @param testInfo The context of the current test, used for logging service shutdown messages.
   */
  @Override
  protected void tearDown(TestInfo testInfo) {
    for (ServiceInfo serviceInfo : lsnexusServices) {
      try (serviceInfo) {
        getTest()
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Shutting down LSNexus service : %s", serviceInfo.getAddress());
      }
    }
    lsnexusServices.clear();
  }

  /**
   * Starts an LSNexus service instance for a given DUT.
   *
   * <p>This method picks a free port, constructs the command to start the LSNexus service, and
   * starts it. It then returns a {@link ServiceInfo} object that encapsulates the service details.
   *
   * @param testInfo The test context, used for logging and to determine the log directory for the
   *     LSNexus service.
   * @param dutName The name of the DUT for which the service is being created.
   * @return A {@link ServiceInfo} object containing the new service's details.
   * @throws MobileHarnessException if there is an error during command execution.
   * @throws InterruptedException if the thread is interrupted.
   */
  @VisibleForTesting
  ServiceInfo startLsNexusService(TestInfo testInfo, String dutName)
      throws MobileHarnessException, InterruptedException {
    int lsnexusPort = getFreePort();

    String logDir = String.format("%s/lsnexus_%s", getTest().getGenFileDir(), dutName);
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

    return serviceInfoBuilderProvider
        .get()
        .setPort(lsnexusPort)
        .setDutName(dutName)
        .setProcess(commandExecutor.start(Command.of(args)))
        .build();
  }

  /**
   * Finds and returns an unused port on the host machine.
   *
   * @throws MobileHarnessException if an unused port cannot be found.
   * @throws InterruptedException if the port picking process is interrupted.
   */
  int getFreePort() throws MobileHarnessException, InterruptedException {
    int port;
    try {
      port = pickUnusedPort();
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROD_FOREGROUND_SERVICE_MESSENGER_INIT_ERROR,
          "Failed to pick an unused port.",
          e);
    }
    return port;
  }

  /**
   * Stores information about a LSNexus service instance.
   *
   * <p>This class encapsulates details about a running LSNexus service, including its port, DUT
   * name, and the underlying process. It also manages the connection to the service.
   */
  @AutoValue
  public abstract static class ServiceInfo implements AutoCloseable {
    public abstract int getPort();

    public abstract String getDutName();

    public abstract CommandProcess getProcess();

    /** Returns the address of the LSNexus service, formatted as "localhost:port". */
    public String getAddress() {
      return String.format("localhost:%d", getPort());
    }

    /** Stops the LSNexus service process. */
    @Override
    public void close() {
      if (getProcess() != null) {
        getProcess().stop();
      }
    }

    /** Creates a new {@link Builder} for {@link ServiceInfo}. */
    public static Builder builder() {
      return new AutoValue_CrosLsNexusDecorator_ServiceInfo.Builder();
    }

    /** Builder for {@link ServiceInfo}. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder setPort(int port);

      abstract Builder setDutName(String dutName);

      abstract Builder setProcess(CommandProcess process);

      abstract ServiceInfo build();
    }
  }
}
