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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.sponge.TestXmlParser;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Driver for running standard Selenium WebDriver tests on Android devices. */
@DriverAnnotation(help = "For running Selenium tests on Android devices.")
@TestAnnotation(
    required = false,
    help = "Leave it empty and Mobile Harness will simply use your job name as test name.")
public class SeleniumWebDriver extends BaseWebTestDriver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @FileAnnotation(required = true, help = "The Selenium test binary or wrapper script.")
  public static final String TAG_SELENIUM_TEST_FILE = "selenium_test_file";

  @Inject
  SeleniumWebDriver(Device device, TestInfo testInfo) {
    super(device, testInfo);
  }

  SeleniumWebDriver(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      TestXmlParser testXmlParser) {
    super(device, testInfo, cmdExecutor, localFileUtil, testXmlParser);
  }

  @Override
  protected String getTestFileTag() {
    return TAG_SELENIUM_TEST_FILE;
  }

  @Override
  protected String getLogPrefix() {
    return "[Selenium]";
  }

  @Override
  protected void populateEnvironment(TestInfo testInfo, Map<String, String> extraEnv)
      throws MobileHarnessException {
    getSeleniumAddress(testInfo)
        .ifPresent(
            address -> {
              extraEnv.put("SELENIUM_ADDRESS", address);
              testInfo.log().atInfo().alsoTo(logger).log("Set SELENIUM_ADDRESS to %s", address);
            });

    getBaseUrl(testInfo)
        .ifPresent(
            url -> {
              extraEnv.put("BASE_URL", url);
              testInfo.log().atInfo().alsoTo(logger).log("Set BASE_URL to %s", url);
            });
  }

  @Override
  protected void populateCommandArgs(TestInfo testInfo, List<String> commandList)
      throws MobileHarnessException {
    getSeleniumAddress(testInfo)
        .ifPresent(address -> commandList.add("--seleniumAddress=" + address));
    getBaseUrl(testInfo).ifPresent(url -> commandList.add("--baseUrl=" + url));
  }
}
