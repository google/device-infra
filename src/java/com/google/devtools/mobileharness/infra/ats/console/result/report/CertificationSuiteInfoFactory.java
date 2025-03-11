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

package com.google.devtools.mobileharness.infra.ats.console.result.report;

import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Factory for creating certification suite info. */
public class CertificationSuiteInfoFactory {

  // The known existing variant of suites.
  // Adding a new variant requires approval from Android Partner team and Test Harness team.
  private enum SuiteVariant {
    CTS_ON_GSI("CTS_ON_GSI", "cts-on-gsi");

    private final String reportDisplayName;
    private final String configName;

    private SuiteVariant(String reportName, String configName) {
      this.reportDisplayName = reportName;
      this.configName = configName;
    }

    public String getReportDisplayName() {
      return reportDisplayName;
    }

    public String getConfigName() {
      return configName;
    }
  }

  public static final String SUITE_REPORT_VERSION = "5.0";

  /**
   * Creates suite info based on given info.
   *
   * <p>Mimic from {@code
   * com.android.compatibility.common.tradefed.result.suite.CertificationResultXml}
   */
  public CertificationSuiteInfo createSuiteInfo(Map<String, String> suiteInfo) {
    CertificationSuiteInfo.Builder certificationSuiteInfoBuilder = CertificationSuiteInfo.builder();
    if (suiteInfo.containsKey(SuiteCommon.SUITE_NAME)) {
      certificationSuiteInfoBuilder.setSuiteName(suiteInfo.get(SuiteCommon.SUITE_NAME));
    }
    if (suiteInfo.containsKey(SuiteCommon.SUITE_VARIANT)) {
      certificationSuiteInfoBuilder.setSuiteVariant(suiteInfo.get(SuiteCommon.SUITE_VARIANT));
    }
    if (suiteInfo.containsKey(SuiteCommon.SUITE_VERSION)) {
      certificationSuiteInfoBuilder.setSuiteVersion(suiteInfo.get(SuiteCommon.SUITE_VERSION));
    }
    if (suiteInfo.containsKey(SuiteCommon.SUITE_PLAN)) {
      certificationSuiteInfoBuilder.setSuitePlan(suiteInfo.get(SuiteCommon.SUITE_PLAN));
    }
    if (suiteInfo.containsKey(SuiteCommon.SUITE_BUILD)) {
      certificationSuiteInfoBuilder.setSuiteBuild(suiteInfo.get(SuiteCommon.SUITE_BUILD));
    }
    certificationSuiteInfoBuilder.setSuiteReportVersion(SUITE_REPORT_VERSION);

    return certificationSuiteInfoBuilder.build();
  }

  /**
   * Generates suite info map.
   *
   * <p>Mimic from {@code
   * com.android.compatibility.common.tradefed.result.suite.CertificationResultXml}
   */
  public Map<String, String> generateSuiteInfoMap(
      String xtsRootDir, String xtsType, String suitePlan) {
    TestSuiteInfo testSuiteInfo = TestSuiteInfo.getInstance(xtsRootDir, xtsType);

    Map<String, String> suiteInfoMap = new HashMap<>();
    suiteInfoMap.put(SuiteCommon.SUITE_NAME, testSuiteInfo.getName());
    suiteInfoMap.put(
        SuiteCommon.SUITE_VARIANT, getSuiteVariant(suitePlan, testSuiteInfo.getName()));
    suiteInfoMap.put(SuiteCommon.SUITE_VERSION, testSuiteInfo.getVersion());
    suiteInfoMap.put(SuiteCommon.SUITE_PLAN, suitePlan);
    suiteInfoMap.put(SuiteCommon.SUITE_BUILD, testSuiteInfo.getBuildNumber());

    return suiteInfoMap;
  }

  public String getSuiteVariant(String suitePlan, String defaultValue) {
    return createSuiteVariant(suitePlan).orElse(defaultValue);
  }

  private Optional<String> createSuiteVariant(String suitePlan) {
    for (SuiteVariant var : SuiteVariant.values()) {
      if (suitePlan.equals(var.getConfigName())) {
        return Optional.of(var.getReportDisplayName());
      }
    }
    return Optional.empty();
  }
}
