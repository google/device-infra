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

import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/** Abstract base class for Web Test drivers supporting standard environments. */
public abstract class BaseWebTestDriver extends BaseDriver {

  protected static final String PARAM_SELENIUM_ADDRESS = "SELENIUM_ADDRESS";
  protected static final String PARAM_DEBUGGER_ADDRESS = "DEBUGGER_ADDRESS";
  protected static final String PARAM_BASE_URL = "BASE_URL";

  protected BaseWebTestDriver(Device device, TestInfo testInfo) {
    super(device, testInfo);
  }

  /**
   * Gets the Selenium hub address from test parameters or properties.
   *
   * @param testInfo the test information container
   * @return an Optional containing the Selenium address, or empty if not configured
   */
  protected Optional<String> getSeleniumAddress(TestInfo testInfo) {
    String seleniumAddress = testInfo.jobInfo().params().get(PARAM_SELENIUM_ADDRESS, null);
    if (seleniumAddress == null) {
      seleniumAddress = testInfo.properties().get(PARAM_SELENIUM_ADDRESS);
    }
    return Optional.ofNullable(seleniumAddress);
  }

  /**
   * Gets the debugger address (e.g., CDP socket address) from test parameters or properties.
   *
   * @param testInfo the test information container
   * @return an Optional containing the debugger address, or empty if not configured
   */
  protected Optional<String> getDebuggerAddress(TestInfo testInfo) {
    String debuggerAddress = testInfo.jobInfo().params().get(PARAM_DEBUGGER_ADDRESS, null);
    if (debuggerAddress == null) {
      debuggerAddress = testInfo.properties().get(PARAM_DEBUGGER_ADDRESS);
    }
    return Optional.ofNullable(debuggerAddress);
  }

  /**
   * Gets the base URL under test from test parameters or properties.
   *
   * @param testInfo the test information container
   * @return an Optional containing the base URL, or empty if not configured
   */
  protected Optional<String> getBaseUrl(TestInfo testInfo) {
    String baseUrl = testInfo.jobInfo().params().get(PARAM_BASE_URL, null);
    if (baseUrl == null) {
      baseUrl = testInfo.properties().get(PARAM_BASE_URL);
    }
    return Optional.ofNullable(baseUrl);
  }
}
