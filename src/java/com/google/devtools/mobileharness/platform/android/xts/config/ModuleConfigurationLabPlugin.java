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

package com.google.devtools.mobileharness.platform.android.xts.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;

/** Lab plugin for parsing xTS module configuration. */
@Plugin(type = Plugin.PluginType.LAB)
public class ModuleConfigurationLabPlugin {
  @FileAnnotation(
      required = true,
      help = "The package from Android Build that contains test artifacts.")
  public static final String AB_TEST_PACKAGE = "ab_test_package";

  @ParamAnnotation(
      required = false,
      help = "The module to test. If not provided, will run the first module.")
  public static final String MODULE_NAME = "module_name";

  private final ConfigurationUtil configurationUtil;
  private final ModuleConfigurationHelper moduleConfigurationHelper;

  public ModuleConfigurationLabPlugin() {
    this(new ConfigurationUtil(), new ModuleConfigurationHelper());
  }

  @VisibleForTesting
  ModuleConfigurationLabPlugin(
      ConfigurationUtil configurationUtil, ModuleConfigurationHelper moduleConfigurationHelper) {
    this.configurationUtil = configurationUtil;
    this.moduleConfigurationHelper = moduleConfigurationHelper;
  }

  @Subscribe
  public void onTestStarting(TestStartingEvent event)
      throws MobileHarnessException, SkipTestException, InterruptedException {
    TestInfo testInfo = event.getTest();

    File testPackage = new File(testInfo.jobInfo().files().getSingle(AB_TEST_PACKAGE));
    ImmutableMap<String, Configuration> configs =
        configurationUtil.getConfigsFromDirs(ImmutableList.of(testPackage));
    Configuration targetConfig =
        testInfo
            .jobInfo()
            .params()
            .getOptional(MODULE_NAME)
            .map(
                s ->
                    configs.values().stream()
                        .filter(config -> config.getMetadata().getXtsModule().equals(s))
                        .findFirst())
            .orElseGet(() -> configs.values().stream().findFirst())
            .orElseThrow(
                () ->
                    SkipTestException.create(
                        String.format("Module configuration not found in %s", testPackage),
                        DesiredTestResult.ERROR,
                        BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND));

    moduleConfigurationHelper.updateJobInfo(
        testInfo.jobInfo(),
        targetConfig,
        ImmutableList.of(testPackage, new File(testInfo.jobInfo().setting().getTmpFileDir())));
  }
}
