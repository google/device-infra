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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteVersion;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Helper class for session handler utils. */
public class SessionHandlerHelper {

  public static final String XTS_MODULE_NAME_PROP = "xts-module-name";
  public static final String XTS_MODULE_ABI_PROP = "xts-module-abi";
  public static final String XTS_MODULE_PARAMETER_PROP = "xts-module-parameter";

  public static final String TEST_RESULT_XML_FILE_NAME = "test_result.xml";
  public static final String TEST_RECORD_PROTOBUFFER_FILE_NAME = "test-record.pb";

  /**
   * The minimum version of the test suite that supports ATS retry.
   *
   * <p>If the min version is TestSuiteVersion.create(0, 0, 0, 0), it means its all versions support
   * ATS retry.
   */
  private static final ImmutableMap<String, TestSuiteVersion>
      XTS_TYPES_SUPPORTING_ATS_RETRY_WITH_MIN_VERSION =
          ImmutableMap.of(
              "cts",
              TestSuiteVersion.create(14, 0, 0, 7),
              "cts-v-host",
              TestSuiteVersion.create(16, 0, 0, 1),
              "gts",
              TestSuiteVersion.create(12, 0, 0, 1));

  /** Checks if the test plan is retry. */
  public static boolean isRunRetry(String testPlan) {
    return Ascii.equalsIgnoreCase(testPlan, "retry");
  }

  public static boolean useTfRetry(
      boolean isAtsServerRequest, String xtsType, @Nullable TestSuiteVersion testSuiteVersion) {
    if (Flags.instance().useTfRetry.getNonNull()) {
      return true;
    }
    if (isAtsServerRequest) {
      // If the xtsType is not in the map, it means it doesn't support ATS retry.
      if (!XTS_TYPES_SUPPORTING_ATS_RETRY_WITH_MIN_VERSION.containsKey(
          Ascii.toLowerCase(xtsType))) {
        return true;
      }
      if (testSuiteVersion == null) {
        // If the test suite version is not available, uses ATS retry.
        return false;
      }
      return testSuiteVersion.compareTo(
              XTS_TYPES_SUPPORTING_ATS_RETRY_WITH_MIN_VERSION.get(Ascii.toLowerCase(xtsType)))
          < 0;
    }
    // If it's from ATS Console, we prefer to use ATS retry mechanism by default
    return false;
  }

  public static Path getSubPlanFilePath(Path xtsRootDir, String xtsType, String subPlanName) {
    return XtsDirUtil.getXtsSubPlansDir(xtsRootDir, xtsType).resolve(subPlanName + ".xml");
  }

  public static void checkSubPlanFileExist(File subPlanFile) throws MobileHarnessException {
    if (!subPlanFile.exists() || !subPlanFile.isFile()) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_SUBPLAN_COMMAND_SUBPLAN_XML_NOT_FOUND,
          String.format("Subplan xml file %s doesn't exist", subPlanFile));
    }
  }

  public static SubPlan loadSubPlan(File subPlanFile) throws MobileHarnessException {
    checkSubPlanFileExist(subPlanFile);
    SubPlan subPlan = new SubPlan();
    try (InputStream inputStream = new FileInputStream(subPlanFile)) {
      subPlan.parse(inputStream);
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_SUBPLAN_COMMAND_PARSE_SUBPLAN_XML_ERROR,
          String.format("Failed to parse the subplan xml file at %s", subPlanFile),
          e);
    }
    return subPlan;
  }

  private SessionHandlerHelper() {}
}
