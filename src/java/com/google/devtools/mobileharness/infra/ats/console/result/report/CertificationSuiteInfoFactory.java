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

/** Factory for creating certification suite info. */
public class CertificationSuiteInfoFactory {

  /** Certification suite types. */
  public enum SuiteType {
    CTS;
  }

  public static final String SUITE_REPORT_VERSION = "5.0";

  /**
   * The latest suite version for CTS.
   *
   * <p>Find more versions in https://source.android.com/docs/compatibility/cts/downloads
   */
  public static final String CTS_SUITE_VERSION_HEAD = "13.0_r3";

  /**
   * Creates suite info based on given info.
   *
   * <p>Mimic from {@code
   * com.android.compatibility.common.tradefed.result.suite.CertificationResultXml}
   */
  public CertificationSuiteInfo createSuiteInfo(SuiteType suiteType, String suitePlan) {
    switch (suiteType) {
      case CTS:
        return CertificationSuiteInfo.builder()
            .setSuiteName("CTS")
            .setSuiteVariant("CTS")
            .setSuiteVersion(CTS_SUITE_VERSION_HEAD)
            .setSuitePlan(suitePlan)
            .setSuiteBuild("0") // Default to 0 for now
            .setSuiteReportVersion(SUITE_REPORT_VERSION)
            .build();
    }
    throw new UnsupportedOperationException("Unrecognized suite type: " + suiteType);
  }
}
