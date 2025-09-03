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
import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyTestSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Decorator for generating device config using CIPD and adding parameters for test execution. */
@DecoratorAnnotation(
    help = "Generates device config using CIPD and adds parameters for test execution.")
public class CrosDutTopologyDecorator extends CrosBaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final CommandExecutor commandExecutor;

  @Inject
  @VisibleForTesting
  CrosDutTopologyDecorator(
      Driver decoratedDriver, TestInfo testInfo, CommandExecutor commandExecutor) {
    super(decoratedDriver, testInfo);
    this.commandExecutor = commandExecutor;
  }

  @Override
  public void prepare(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("CrosDutTopologyDecorator is running on device: %s", deviceId());
    List<String> dutNames = new ArrayList<>();
    String deviceName = deviceName(deviceId());
    dutNames.add(deviceName);
    Map<String, String> lsnexusServiceAddresses = collectLsNexusAddresses(testInfo, dutNames);

    Command cipdCommand = generateCommand(testInfo, dutNames, lsnexusServiceAddresses);

    CommandResult commandResult = this.commandExecutor.exec(cipdCommand);
    String output = commandResult.stdout();
    testInfo.log().atInfo().alsoTo(logger).log("Converter output: \n%s", output);
    // Parse the output and override device config.
    updateTestInfo(output, testInfo);
  }

  @Override
  protected void tearDown(TestInfo testInfo) {
    // Do nothing.
  }

  /**
   * Updates the test info with the output from the dt-converter CIPD tool.
   *
   * @param output The output from the dt-converter CIPD tool.
   * @param testInfo The {@link TestInfo} object for the current test.
   * @throws MobileHarnessException if there is an error during parsing.
   */
  @VisibleForTesting
  void updateTestInfo(String output, TestInfo testInfo) throws MobileHarnessException {
    // Implement the logic to add parameters for test execution.
    // For example, parse the output and add parameters to testInfo.jobInfo().params()
    List<Map.Entry<String, String>> parsedParams = parseOutput(output);
    for (Map.Entry<String, String> paramEntry : parsedParams) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Getting param from output: %s=%s", paramEntry.getKey(), paramEntry.getValue());
      if (paramEntry.getKey().equals("-c")) {
        // Mobly Driver accepts the config override if provided as a filepath.
        testInfo.properties().add(MoblyTestSpec.FILE_MOBLY_CONFIG, paramEntry.getValue());
      } else {
        testInfo.properties().add(paramEntry.getKey(), paramEntry.getValue());
      }
    }
  }

  /**
   * Collects the DUT names and LSNexus service addresses from the list of DUTs.
   *
   * @param testInfo The {@link TestInfo} object for the current test.
   * @param deviceNames The list of DUT names.
   * @return a map with the DUT name as key and the LSNexus service address as value.
   */
  @VisibleForTesting
  Map<String, String> collectLsNexusAddresses(TestInfo testInfo, List<String> deviceNames) {
    Map<String, String> lsnexusAddresses = new HashMap<>();
    for (String deviceName : deviceNames) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Getting LSNexus service address for DUT: %s", deviceName);
      String lsnexusParamKey = String.format("%s%s", LSNEXUS_PARAM_SUFFIX, deviceName);
      // Not all DUTs can have LSNexus service.
      if (getTest().properties().has(lsnexusParamKey)) {
        String lsnexusServiceAddress = getTest().properties().get(lsnexusParamKey);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Found LSNexus service address %s for DUT: %s", lsnexusServiceAddress, deviceName);
        lsnexusAddresses.put(deviceName, lsnexusServiceAddress);
      }
    }
    return lsnexusAddresses;
  }

  /**
   * Generates the command to run the dt-converter CIPD tool.
   *
   * @param testInfo The {@link TestInfo} object for the current test.
   * @param dutNames The list of DUT names.
   * @param lsnexusServiceAddresses The list of LSNexus service addresses.
   * @return The command to run the dt-converter CIPD tool.
   */
  @VisibleForTesting
  Command generateCommand(
      TestInfo testInfo, List<String> dutNames, Map<String, String> lsnexusServiceAddresses)
      throws MobileHarnessException {
    String deviceName = deviceName(deviceId());
    List<String> args = new ArrayList<>();
    args.add(DT_CONVERTER_CIPD_PATH);
    args.add("convert");
    for (String dutName : dutNames) {
      args.add("-unit");
      args.add(dutName);
    }
    for (Map.Entry<String, String> entry : lsnexusServiceAddresses.entrySet()) {
      args.add("-lsnexus");
      String dutName = entry.getKey();
      String lsnexusAddress = entry.getValue();
      args.add(String.format("%s=%s", dutName, lsnexusAddress));
    }
    args.add("-labservice");
    args.add(getInventoryServiceAddress());
    args.add("-logdir");
    args.add(testInfo.getGenFileDir());
    args.add("-config_name");
    args.add("config_" + deviceName);
    args.add("-mh"); // Only request MH specific data.

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Generating device config with command: %s", String.join(" ", args));
    return Command.of(args);
  }

  /**
   * Parses the output from the dt-converter CIPD tool and returns a list of key-value parameters.
   *
   * <p>The output is expected to be a series of key-value pairs, separated by newlines. Each line
   * is split by the first whitespace to separate the key and value. The output can contain
   * parameters with the same key but different values, and they are treated as separate entries.
   */
  @VisibleForTesting
  List<Map.Entry<String, String>> parseOutput(String output) {
    logger.atInfo().log("Parsing CIPD output: %s", output);
    List<Map.Entry<String, String>> params = new ArrayList<>();
    for (String line : Splitter.on('\n').split(output)) {
      String[] parts = line.trim().split("\\s+", 2);
      if (parts.length == 2) {
        params.add(new AbstractMap.SimpleEntry<>(parts[0], parts[1]));
      }
    }
    return params;
  }
}
