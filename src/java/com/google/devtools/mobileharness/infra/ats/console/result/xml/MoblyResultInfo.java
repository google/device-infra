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

package com.google.devtools.mobileharness.infra.ats.console.result.xml;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

/** Data class for Mobly test results in one execution. */
@AutoValue
public abstract class MoblyResultInfo {

  /**
   * Creates a {@link MoblyResultInfo}
   *
   * @param moblyExecNameToTestSummaryYamlFile a map contains Mobly executuable name to its test
   *     summary yaml file path
   * @param xmlResultElementAttrs XML element attributes added to the <Result> element in the CTS
   *     test result XML field
   * @param xmlBuildElementAttrs XML element attributes added to the <Build> element in the CTS test
   *     result XML file
   */
  public static MoblyResultInfo of(
      ImmutableMap<String, String> moblyExecNameToTestSummaryYamlFile,
      ImmutableMap<String, String> xmlResultElementAttrs,
      ImmutableMap<String, String> xmlBuildElementAttrs) {
    return new com.google.devtools.mobileharness.infra.ats.console.result.xml
        .AutoValue_MoblyResultInfo(
        moblyExecNameToTestSummaryYamlFile, xmlResultElementAttrs, xmlBuildElementAttrs);
  }

  public abstract ImmutableMap<String, String> moblyExecNameToTestSummaryYamlFile();

  public abstract ImmutableMap<String, String> xmlResultElementAttrs();

  public abstract ImmutableMap<String, String> xmlBuildElementAttrs();
}
