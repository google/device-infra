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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Metric;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationDescriptorMetadata;
import java.util.Map;
import java.util.Optional;

/** Data class for compatibility report format. */
@AutoValue
public abstract class CompatibilityReportFormat {

  private static final String OPTION_TEST_FAILURE_LEVEL = "failure_level";

  @VisibleForTesting
  public static final String WARNING_FOR_APPROVAL_METRIC_KEY = "WarningForApproval";

  /** The target name this format is for. Can be module name or test plan name. */
  public abstract String targetName();

  /** The failure level of test in the report. */
  public abstract TestStatus failureLevel();

  /** The test cases to be affected by the format. */
  public abstract Optional<ImmutableSet<String>> tests();

  /**
   * Creates a {@link CompatibilityReportFormat} for a module based on the given module {@link
   * Configuration}.
   */
  public static Optional<CompatibilityReportFormat> fromModuleConfig(Configuration configuration) {
    String moduleName = configuration.getMetadata().getXtsModule();
    CompatibilityReportFormat.Builder builder =
        new AutoValue_CompatibilityReportFormat.Builder().setTargetName(moduleName);
    Map<String, ConfigurationDescriptorMetadata> metadataMap =
        configuration.getConfigDescriptor().getMetadataMap();
    if (metadataMap.containsKey(OPTION_TEST_FAILURE_LEVEL)
        && metadataMap.get(OPTION_TEST_FAILURE_LEVEL).getValueCount() > 0) {
      builder.setFailureLevel(
          TestStatus.valueOf(metadataMap.get(OPTION_TEST_FAILURE_LEVEL).getValue(0)));
      return Optional.of(builder.build());
    } else {
      return Optional.empty();
    }
  }

  /**
   * Applies the format to the given module builder in place.
   *
   * <p>For failure level, it changes the test status to the given failure level of all tests with
   * {@link TestStatus#FAILURE} in the module and update test counts accordingly.
   *
   * <p>For example:
   *
   * <pre>
   *   <Module name="module" pass="1" failed="1" total_tests="2">
   *     <TestCase name="class">
   *       <Test result="fail" name="method1" />
   *       <Test result="pass" name="method2" />
   *     </TestCase>
   *   </Module>
   * </pre>
   *
   * <p>with failure level as warning, the module will be:
   *
   * <pre>
   *   <Module name="module" pass="1" failed="0" warning="1" total_tests="2">
   *     <TestCase name="class">
   *       <Test result="warning" name="method1" />
   *       <Test result="pass" name="method2" />
   *     </TestCase>
   *   </Module>
   * </pre>
   */
  public void applyToModule(Module.Builder moduleBuilder) {
    String failureLevel = TestStatus.convertToTestStatusCompatibilityString(failureLevel());
    int changedCount = 0;
    for (TestCase.Builder testCaseBuilder : moduleBuilder.getTestCaseBuilderList()) {
      for (Test.Builder testBuilder : testCaseBuilder.getTestBuilderList()) {
        if (testBuilder
            .getResult()
            .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE))) {
          if (tests().isPresent()
              && !tests()
                  .get()
                  .contains(
                      String.format("%s#%s", testCaseBuilder.getName(), testBuilder.getName()))) {
            continue;
          }
          changedCount++;
          testBuilder.setResult(failureLevel);
        }
      }
    }
    switch (failureLevel()) {
      case PASSED -> {
        moduleBuilder
            .setPassed(moduleBuilder.getPassed() + changedCount)
            .setFailedTests(moduleBuilder.getFailedTests() - changedCount);
      }
      case WARNING -> {
        moduleBuilder
            .setWarningTests(moduleBuilder.getWarningTests() + changedCount)
            .setFailedTests(moduleBuilder.getFailedTests() - changedCount);
      }
      case FAILURE -> {
        // Do nothing.
      }
      default -> moduleBuilder.setFailedTests(0);
    }
  }

  /**
   * Changes the test status to WARNING for all tests with {@link TestStatus#FAILURE} in the module
   * that have a metric with key "WarningForApproval" and updates test counts accordingly.
   */
  public static void applyWarningForApproval(Module.Builder moduleBuilder) {
    if (moduleBuilder.getFailedTests() == 0) {
      return;
    }
    String warningLevel = TestStatus.convertToTestStatusCompatibilityString(TestStatus.WARNING);
    String failureLevel = TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE);
    int changedCount = 0;
    for (int i = 0; i < moduleBuilder.getTestCaseCount(); i++) {
      TestCase testCase = moduleBuilder.getTestCase(i);
      for (int j = 0; j < testCase.getTestCount(); j++) {
        Test test = testCase.getTest(j);
        if (test.getResult().equals(failureLevel)) {
          boolean hasWarningForApproval = false;
          for (Metric metric : test.getMetricList()) {
            if (metric.getKey().equals(WARNING_FOR_APPROVAL_METRIC_KEY)) {
              hasWarningForApproval = true;
              break;
            }
          }
          if (hasWarningForApproval) {
            changedCount++;
            moduleBuilder.getTestCaseBuilder(i).getTestBuilder(j).setResult(warningLevel);
          }
        }
      }
    }

    if (changedCount > 0) {
      moduleBuilder
          .setWarningTests(moduleBuilder.getWarningTests() + changedCount)
          .setFailedTests(moduleBuilder.getFailedTests() - changedCount);
    }
  }

  /** Builder for {@link CompatibilityReportFormat}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTargetName(String targetName);

    public abstract Builder setFailureLevel(TestStatus failureLevel);

    public abstract Builder setTests(ImmutableSet<String> tests);

    public abstract CompatibilityReportFormat build();
  }
}
