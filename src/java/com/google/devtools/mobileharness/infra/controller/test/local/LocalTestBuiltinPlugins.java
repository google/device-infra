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

package com.google.devtools.mobileharness.infra.controller.test.local;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner.EventScope;
import com.google.devtools.mobileharness.infra.controller.test.PluginLoadingResult.PluginItem;
import com.google.devtools.mobileharness.infra.controller.test.util.TestCommandHistorySaver;
import com.google.devtools.mobileharness.infra.controller.test.util.testlogcollector.TestLogCollectorPlugin;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.plugin.NonTradefedReportGenerator;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil;
import com.google.inject.Guice;
import com.google.inject.ProvisionException;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Objects;

class LocalTestBuiltinPlugins {

  private final ReflectionUtil reflectionUtil = new ReflectionUtil();

  /** Loads built-in plugins. Should be invoked when a test is initializing. */
  ImmutableList<PluginItem<?>> loadBuiltInPlugin(TestInfo testInfo, DirectTestRunner testRunner)
      throws MobileHarnessException {
    // Loads built-in plugins.
    ImmutableList.Builder<PluginItem<?>> builtinPluginsBuilder = ImmutableList.builder();
    if (isXtsDynamicDownloaderEnabled(testInfo)) {
      builtinPluginsBuilder.add(
          PluginItem.create(
              createBuiltinPlugin(
                  "com.google.devtools.mobileharness.infra.controller.test.util.xtsdownloader.MctsDynamicDownloadPlugin"),
              EventScope.INTERNAL_PLUGIN));
    }
    // This should be registered before AtsFileServerUploaderPlugin to make sure collected logs
    // are uploaded to the client.
    if (isTestLogCollectorEnabled()) {
      builtinPluginsBuilder.add(
          PluginItem.create(new TestLogCollectorPlugin(), EventScope.CLASS_INTERNAL));
    }
    if (isAtsFileServerUploaderEnabled(testInfo)) {
      builtinPluginsBuilder.add(
          PluginItem.create(
              createBuiltinPlugin(
                  "com.google.devtools.mobileharness.infra.controller.test.util.atsfileserveruploader.AtsFileServerUploaderPlugin"),
              EventScope.CLASS_INTERNAL));
    }
    if (isAtsModeEnabled()) {
      builtinPluginsBuilder.add(
          PluginItem.create(
              createBuiltinPlugin(
                  "com.google.devtools.mobileharness.platform.android.xts.plugin.AtsDeviceRecoveryPlugin"),
              EventScope.CLASS_INTERNAL));
    }
    builtinPluginsBuilder.add(
        PluginItem.create(new TestCommandHistorySaver(), EventScope.CLASS_INTERNAL),
        PluginItem.create(new NonTradefedReportGenerator(), EventScope.INTERNAL_PLUGIN));
    if (isXtsDeviceCompatibilityCheckerEnabled(testInfo.jobInfo())) {
      builtinPluginsBuilder.add(
          PluginItem.create(
              createBuiltinPlugin(
                  "com.google.devtools.mobileharness.platform.android.xts.plugin.XtsDeviceCompatibilityChecker"),
              EventScope.INTERNAL_PLUGIN));
    }

    ImmutableList<PluginItem<?>> builtinPlugins = builtinPluginsBuilder.build();
    for (PluginItem<?> pluginItem : builtinPlugins) {
      testRunner.registerTestEventSubscriber(pluginItem.plugin(), pluginItem.scope());
      testRunner.registerTestEventSubscriber(pluginItem.plugin(), EventScope.TEST_MESSAGE);
    }
    return builtinPlugins;
  }

  /**
   * Instantiates a built-in plugin by its class name.
   *
   * <p>The class should be able to be injected by Guice.
   *
   * @throws MobileHarnessException if the class name is not found or an error occurs when
   *     instantiating the class
   */
  private Object createBuiltinPlugin(String className) throws MobileHarnessException {
    Class<?> clazz;
    try {
      clazz = reflectionUtil.loadClass(className, Object.class, getClass().getClassLoader());
    } catch (
        @SuppressWarnings("UnusedException") // Info in ClassNotFoundException is useless.
        ClassNotFoundException e) {
      throw new MobileHarnessException(
          InfraErrorId.TR_PLUGIN_BUILTIN_PLUGIN_CLASS_NOT_FOUND_ERROR,
          String.format(
              "Built-in plugin [%s] is not found. Is it added to runtime_deps of LocalMode or"
                  + " LabServer?",
              className));
    }
    try {
      return Guice.createInjector().getInstance(clazz);
    } catch (ProvisionException e) {
      throw new MobileHarnessException(
          InfraErrorId.TR_PLUGIN_BUILTIN_PLUGIN_INSTANTIATION_ERROR,
          String.format("Failed to instantiate built-in plugin [%s]", className),
          e);
    }
  }

  /** Returns {@code true} if xts dynamic downloader is enabled. */
  private static boolean isXtsDynamicDownloaderEnabled(TestInfo testInfo) {
    return testInfo
        .jobInfo()
        .properties()
        .getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED)
        .orElse(false);
  }

  private static boolean isAtsFileServerUploaderEnabled(TestInfo testInfo) {
    return isAtsModeEnabled()
        && Objects.equals(testInfo.jobInfo().properties().get(Job.CLIENT_TYPE), "olc");
  }

  private static boolean isAtsModeEnabled() {
    return Flags.instance().enableAtsMode.getNonNull();
  }

  private static boolean isTestLogCollectorEnabled() {
    return Flags.instance().enableTestLogCollector.getNonNull();
  }

  private static boolean isXtsDeviceCompatibilityCheckerEnabled(JobInfo jobInfo) {
    return jobInfo.properties().getBoolean(XtsPropertyName.Job.IS_XTS_TF_JOB).orElse(false)
        || jobInfo.properties().getBoolean(XtsPropertyName.Job.IS_XTS_NON_TF_JOB).orElse(false);
  }
}
