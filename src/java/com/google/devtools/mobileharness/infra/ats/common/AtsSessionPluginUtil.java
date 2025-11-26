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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Utility class for ATS session plugins. */
public final class AtsSessionPluginUtil {

  private AtsSessionPluginUtil() {}

  /**
   * Copies test properties needed by xTS dynamic download jobs from the current test to the next
   * test. This is done so that the following xTS dynamic download jobs don't need to require
   * devices to be online.
   */
  public static void copyTestPropertiesForDynamicDownloadJobs(
      TestInfo currentTest, TestInfo nextTest) {
    ImmutableList.of(
            XtsConstants.DEVICE_ABI_PROPERTY_KEY,
            XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY,
            XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY,
            XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY)
        .forEach(
            propertyKey ->
                currentTest
                    .properties()
                    .getOptional(propertyKey)
                    .ifPresent(value -> nextTest.properties().add(propertyKey, value)));
  }
}
