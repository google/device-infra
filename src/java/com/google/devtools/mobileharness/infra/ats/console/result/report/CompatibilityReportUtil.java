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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.primitives.Booleans.falseFirst;
import static java.util.Comparator.comparing;
import static java.util.Map.Entry.comparingByKey;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.BuildInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommonUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Shared constants and {@code ReportProto} builders for compatibility xTS reports. */
public final class CompatibilityReportUtil {

  /** Maximum UTF-16 code unit length of a stack trace or failure message saved in a report. */
  public static final int STACK_TRACE_MAX_SIZE = 1024 * 1024;

  private static final String BUILD_ATTR_PREFIX = "build_";
  private static final String CSR_ATTR_PREFIX = "csr_";
  private static final String VERIFIED_BOOT_HASH_SUFFIX = ".root_of_trust.verified_boot_hash";
  private static final ImmutableSet<String> EXTRA_BUILD_INFO_ATTRS =
      ImmutableSet.of(
          XmlConstants.DEVICE_KERNEL_INFO_ATTR,
          XmlConstants.SYSTEM_IMG_INFO_ATTR,
          XmlConstants.VENDOR_IMG_INFO_ATTR,
          XmlConstants.ADB_VERSION_ATTR);

  private static final String OS_NAME_SYS_PROP = "os.name";
  private static final String OS_VERSION_SYS_PROP = "os.version";
  private static final String OS_ARCH_SYS_PROP = "os.arch";
  private static final String JAVA_VENDOR_SYS_PROP = "java.vendor";
  private static final String JAVA_VERSION_SYS_PROP = "java.version";

  private static final String PASS_COMPATIBILITY_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.PASSED);
  private static final String FAIL_COMPATIBILITY_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE);
  private static final String WARNING_COMPATIBILITY_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.WARNING);

  private static final ImmutableSet<String> PINNED_RESULT_TIME_ATTRS =
      ImmutableSet.of(
          XmlConstants.START_TIME_ATTR,
          XmlConstants.END_TIME_ATTR,
          XmlConstants.START_DISPLAY_TIME_ATTR,
          XmlConstants.END_DISPLAY_TIME_ATTR);

  /** Aggregated compatibility test status counts for a module's {@link TestCase} list. */
  public record TestCounts(int passed, int failed, int warning, int total) {}

  /**
   * Returns whether {@code key} is a valid XML attribute NCName and is allowlisted on the
   * compatibility {@code <Build>} element.
   */
  public static boolean isAllowedBuildAttribute(String key) {
    return isValidXmlAttributeName(key)
        && (key.startsWith(BUILD_ATTR_PREFIX)
            || key.startsWith(CSR_ATTR_PREFIX)
            || key.endsWith(VERIFIED_BOOT_HASH_SUFFIX)
            || EXTRA_BUILD_INFO_ATTRS.contains(key));
  }

  /**
   * Builds a {@link BuildInfo} from {@code attributeMap}, populating fingerprint fields and
   * ordering non-{@code build_*} attributes alphabetically before {@code build_*} attributes.
   */
  public static BuildInfo buildBuildInfo(Map<String, String> attributeMap) {
    BuildInfo.Builder build = BuildInfo.newBuilder();
    Optional.ofNullable(attributeMap.get(XmlConstants.BUILD_FINGERPRINT_ATTR))
        .map(String::trim)
        .ifPresent(build::setBuildFingerprint);
    Optional.ofNullable(attributeMap.get(XmlConstants.BUILD_FINGERPRINT_UNALTERED_ATTR))
        .map(String::trim)
        .ifPresent(build::setBuildFingerprintUnaltered);
    Optional.ofNullable(attributeMap.get(XmlConstants.BUILD_VENDOR_FINGERPRINT_ATTR))
        .map(String::trim)
        .ifPresent(build::setBuildVendorFingerprint);

    ImmutableList<Attribute> orderedAttributes =
        attributeMap.entrySet().stream()
            .sorted(
                comparing(
                        (Map.Entry<String, String> e) -> e.getKey().startsWith(BUILD_ATTR_PREFIX),
                        falseFirst())
                    .thenComparing(Map.Entry::getKey))
            .map(e -> Attribute.newBuilder().setKey(e.getKey()).setValue(e.getValue()).build())
            .collect(toImmutableList());
    return build.addAllAttribute(orderedAttributes).build();
  }

  /**
   * Builds compatibility {@code <Result>} attributes with {@code start}, {@code end}, {@code
   * start_display}, and {@code end_display} placed first (when present), followed by all remaining
   * attributes sorted lexicographically by key.
   */
  public static ImmutableList<Attribute> buildOrderedResultAttributes(
      Map<String, String> attributeMap) {
    ImmutableList.Builder<Attribute> attributes = ImmutableList.builder();
    for (String pinnedKey : PINNED_RESULT_TIME_ATTRS) {
      String value = attributeMap.get(pinnedKey);
      if (value != null) {
        attributes.add(Attribute.newBuilder().setKey(pinnedKey).setValue(value.trim()).build());
      }
    }
    attributeMap.entrySet().stream()
        .filter(e -> !PINNED_RESULT_TIME_ATTRS.contains(e.getKey()))
        .sorted(comparingByKey())
        .map(e -> Attribute.newBuilder().setKey(e.getKey()).setValue(e.getValue().trim()).build())
        .forEach(attributes::add);
    return attributes.build();
  }

  /** Converts a {@link CertificationSuiteInfo} into {@code <Result>} suite XML attributes. */
  public static ImmutableMap<String, String> toSuiteAttributesMap(
      CertificationSuiteInfo suiteInfo) {
    return ImmutableMap.<String, String>builder()
        .put(XmlConstants.SUITE_NAME_ATTR, suiteInfo.suiteName())
        .put(XmlConstants.SUITE_VARIANT_ATTR, suiteInfo.suiteVariant())
        .put(XmlConstants.SUITE_VERSION_ATTR, suiteInfo.suiteVersion())
        .put(XmlConstants.SUITE_PLAN_ATTR, suiteInfo.suitePlan())
        .put(XmlConstants.SUITE_BUILD_ATTR, suiteInfo.suiteBuild())
        .put(XmlConstants.SUITE_REPORT_VERSION_ATTR, suiteInfo.suiteReportVersion())
        .buildOrThrow();
  }

  /** Builds {@code <Result>} host and Java environment XML attributes. */
  public static ImmutableMap<String, String> buildHostAttributes(
      String hostName, UnaryOperator<String> systemPropertyLookup) {
    return ImmutableMap.<String, String>builder()
        .put(XmlConstants.HOST_NAME_ATTR, hostName)
        .put(XmlConstants.OS_NAME_ATTR, systemPropertyLookup.apply(OS_NAME_SYS_PROP))
        .put(XmlConstants.OS_VERSION_ATTR, systemPropertyLookup.apply(OS_VERSION_SYS_PROP))
        .put(XmlConstants.OS_ARCH_ATTR, systemPropertyLookup.apply(OS_ARCH_SYS_PROP))
        .put(XmlConstants.JAVA_VENDOR_ATTR, systemPropertyLookup.apply(JAVA_VENDOR_SYS_PROP))
        .put(XmlConstants.JAVA_VERSION_ATTR, systemPropertyLookup.apply(JAVA_VERSION_SYS_PROP))
        .buildOrThrow();
  }

  /** Counts passed, failed, warning, and total tests across {@code testCases}. */
  public static TestCounts countModuleTests(List<TestCase> testCases) {
    int passed = 0;
    int failed = 0;
    int warning = 0;
    int total = 0;
    for (TestCase testCase : testCases) {
      for (Test test : testCase.getTestList()) {
        String result = test.getResult();
        if (result.equals(PASS_COMPATIBILITY_STATUS)) {
          passed++;
        } else if (result.equals(FAIL_COMPATIBILITY_STATUS)) {
          failed++;
        } else if (result.equals(WARNING_COMPATIBILITY_STATUS)) {
          warning++;
        }
        total++;
      }
    }
    return new TestCounts(passed, failed, warning, total);
  }

  /**
   * Recomputes and sets {@code passed}, {@code failed_tests}, {@code warning_tests}, and {@code
   * total_tests} on {@code moduleBuilder} from its {@code test_case} list.
   */
  public static void populateModuleTestCounts(Module.Builder moduleBuilder) {
    TestCounts counts = countModuleTests(moduleBuilder.getTestCaseList());
    moduleBuilder
        .setPassed(counts.passed())
        .setFailedTests(counts.failed())
        .setWarningTests(counts.warning())
        .setTotalTests(counts.total());
  }

  /**
   * Picks the preferred {@link Test} attempt when merging duplicate executions of the same test
   * method (first {@code pass} wins; otherwise retains {@code existing}).
   */
  public static Test pickPreferredTestAttempt(Test existing, Test candidate) {
    if (!existing.getResult().equals(PASS_COMPATIBILITY_STATUS)
        && candidate.getResult().equals(PASS_COMPATIBILITY_STATUS)) {
      return candidate;
    }
    return existing;
  }

  /**
   * Returns whether {@code module} contributes to {@link Summary} module counts (excludes skipped
   * modules and Tradefed {@code PreModuleChecker}/{@code PostModuleChecker} modules).
   */
  public static boolean isModuleCountedInSummary(Module module) {
    return !module.getSkipped() && !SuiteCommonUtil.isModuleChecker(module);
  }

  /** Formats the compatibility reason message for a module skipped by a module controller. */
  public static String formatControllerSkippedReason(String moduleName) {
    return String.format("Module %s is skipped by module controller.", moduleName);
  }

  /**
   * Creates a skipped {@link Module} ({@code done=true, skipped=true}) with {@code reason}, setting
   * {@code abi} only when non-empty.
   */
  public static Module createSkippedModule(String abi, String name, String reason) {
    Module.Builder builder =
        Module.newBuilder()
            .setName(name)
            .setDone(true)
            .setSkipped(true)
            .setReason(Reason.newBuilder().setMsg(reason));
    if (!abi.isEmpty()) {
      builder.setAbi(abi);
    }
    return builder.build();
  }

  /**
   * Truncates {@code text} to {@link #STACK_TRACE_MAX_SIZE} UTF-16 code units without splitting a
   * trailing surrogate pair.
   */
  public static String truncateStackTrace(String text) {
    if (text.length() <= STACK_TRACE_MAX_SIZE) {
      return text;
    }
    int endIdx = STACK_TRACE_MAX_SIZE;
    if (Character.isHighSurrogate(text.charAt(endIdx - 1))) {
      endIdx--;
    }
    return text.substring(0, endIdx);
  }

  private static boolean isValidXmlAttributeName(String key) {
    if (key.isEmpty() || !isAsciiLetterOrUnderscore(key.charAt(0))) {
      return false;
    }
    for (int i = 1; i < key.length(); i++) {
      char c = key.charAt(i);
      if (!isAsciiLetterOrUnderscore(c) && (c < '0' || c > '9') && c != '.' && c != '-') {
        return false;
      }
    }
    return true;
  }

  private static boolean isAsciiLetterOrUnderscore(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  private CompatibilityReportUtil() {}
}
