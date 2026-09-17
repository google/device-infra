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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.sponge.TestXmlParser;
import java.util.List;
import javax.inject.Inject;

/**
 * Driver for running Protractor tests on Android devices via CDP or WebDriver.
 *
 * <p>For the overall design and architecture, see <a
 * href="http://go/waddi-omnilab#heading=h.wv3r8ewb03pi">Integrating Web Testing Bridge into OmniLab
 * & FTL</a>.
 */
@DriverAnnotation(help = "For running Protractor tests on Android devices.")
@TestAnnotation(
    required = false,
    help = "Leave it empty and Mobile Harness will simply use your job name as test name.")
public class ProtractorWebDriver extends BaseWebTestDriver {

  @FileAnnotation(required = true, help = "The Protractor test binary or wrapper script.")
  public static final String TAG_PROTRACTOR_TEST_FILE = "protractor_test_file";

  @ParamAnnotation(required = false, help = "Comma-separated Protractor spec files to execute.")
  public static final String PARAM_SPECS = "specs";

  @ParamAnnotation(
      required = false,
      help = "The target type of web test. Valid values: 'browser', 'webview'.")
  public static final String PARAM_TARGET_TYPE = "target_type";

  @ParamAnnotation(
      required = false,
      help = "The package name of the app under test (specifically for WebViews).")
  public static final String PARAM_PACKAGE_NAME = "package_name";

  @Inject
  ProtractorWebDriver(Device device, TestInfo testInfo) {
    super(device, testInfo);
  }

  ProtractorWebDriver(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      TestXmlParser testXmlParser) {
    super(device, testInfo, cmdExecutor, localFileUtil, testXmlParser);
  }

  @Override
  protected String getTestFileTag() {
    return TAG_PROTRACTOR_TEST_FILE;
  }

  @Override
  protected String getLogPrefix() {
    return "[Protractor]";
  }

  @Override
  protected void populateCommandArgs(TestInfo testInfo, List<String> commandList)
      throws MobileHarnessException {
    getSeleniumAddress(testInfo)
        .ifPresent(address -> commandList.add("--seleniumAddress=" + address));
    getDebuggerAddress(testInfo)
        .ifPresent(
            address -> {
              commandList.add("--params.debuggerAddress=" + address);
              commandList.add("--capabilities.chromeOptions.debuggerAddress=" + address);
            });
    getBaseUrl(testInfo).ifPresent(url -> commandList.add("--params.baseUrl=" + url));

    String specs = testInfo.jobInfo().params().get(PARAM_SPECS, null);
    if (specs != null) {
      commandList.add("--specs=" + specs);
    }

    String targetType = testInfo.jobInfo().params().get(PARAM_TARGET_TYPE, null);
    if (targetType != null) {
      commandList.add("--params.target_type=" + targetType);
    }
    String packageName = testInfo.jobInfo().params().get(PARAM_PACKAGE_NAME, null);
    if (packageName != null) {
      commandList.add("--params.package_name=" + packageName);
    }
    String deviceId = getDevice().getDeviceId();
    if (deviceId != null && !deviceId.isEmpty()) {
      commandList.add("--params.device_id=" + deviceId);
    }
  }
}
