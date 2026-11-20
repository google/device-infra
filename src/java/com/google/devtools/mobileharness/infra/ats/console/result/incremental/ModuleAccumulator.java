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

package com.google.devtools.mobileharness.infra.ats.console.result.incremental;

import static java.util.stream.Collectors.joining;

import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements.MeasurementCase;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.DebugInfo;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.common.flogger.FluentLogger;
import com.google.common.xml.XmlEscapers;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.tradefed.TestRecordProtoUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.protobuf.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.annotation.Nullable;

/**
 * Accumulates per-shard Tradefed {@link TestRecord} entries for a single compatibility module ID
 * and materializes the merged {@link Module} proto.
 */
final class ModuleAccumulator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Module metadata keys serialized by Tradefed's ModuleDefinition.
  private static final String MODULE_ABI_ATTR = "module-abi";
  private static final String MODULE_NAME_ATTR = "module-name";

  private static final String PASS_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.PASSED);
  private static final String FAIL_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE);
  private static final String ASSUMPTION_FAILURE_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.ASSUMPTION_FAILURE);
  private static final String IGNORED_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.IGNORED);

  static long toEpochMillis(Timestamp timestamp) {
    try {
      return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toEpochMilli();
    } catch (DateTimeException | ArithmeticException e) {
      logger.atWarning().log(
          "Ignoring out-of-range TestRecord timestamp [seconds=%d, nanos=%d]",
          timestamp.getSeconds(), timestamp.getNanos());
      return 0L;
    }
  }

  private final String moduleId;
  private String contextAbi = "";
  private String contextName = "";
  private long runtimeMillis;
  private long prepTimeMillis;
  private boolean hasPrepTime;
  private long teardownTimeMillis;
  private boolean hasTeardownTime;
  @Nullable private DebugInfo firstUnresolvedDebugInfo;
  private final Map<String, Map<String, Test>> testMethodsByClass = new LinkedHashMap<>();

  ModuleAccumulator(String moduleId) {
    this.moduleId = moduleId;
  }

  void foldModuleRecord(TestRecord moduleRecord, Context moduleContext) {
    if (contextAbi.isEmpty()) {
      contextAbi =
          IncrementalResultConverter.findFirstMetadataValue(moduleContext, MODULE_ABI_ATTR)
              .map(String::trim)
              .orElse("");
    }
    if (contextName.isEmpty()) {
      contextName =
          IncrementalResultConverter.findFirstMetadataValue(moduleContext, MODULE_NAME_ATTR)
              .map(String::trim)
              .orElse("");
    }
    if (firstUnresolvedDebugInfo == null && moduleRecord.hasDebugInfo()) {
      firstUnresolvedDebugInfo = moduleRecord.getDebugInfo();
    }
    long recordModuleRuntimeMillis = 0L;
    if (moduleRecord.hasStartTime() && moduleRecord.hasEndTime()) {
      recordModuleRuntimeMillis =
          Math.max(
              0L,
              toEpochMillis(moduleRecord.getEndTime())
                  - toEpochMillis(moduleRecord.getStartTime()));
    }

    long recordRunRuntimeMillis = 0L;
    boolean recordHasRunTimestamps = false;
    OptionalLong recordPrepMillis = OptionalLong.empty();
    OptionalLong recordTeardownMillis = OptionalLong.empty();
    Map<String, Map<Long, Optional<DebugInfo>>> runAttemptsByRunId = new LinkedHashMap<>();
    for (ChildReference runRef : moduleRecord.getChildrenList()) {
      if (!runRef.hasInlineTestRecord()) {
        continue;
      }
      TestRecord runRecord = runRef.getInlineTestRecord();
      runAttemptsByRunId
          .computeIfAbsent(runRecord.getTestRecordId(), k -> new LinkedHashMap<>())
          .merge(
              runRecord.getAttemptId(),
              runRecord.hasDebugInfo() ? Optional.of(runRecord.getDebugInfo()) : Optional.empty(),
              (existing, incoming) -> existing.or(() -> incoming));
      if (runRecord.hasStartTime() && runRecord.hasEndTime()) {
        recordHasRunTimestamps = true;
        recordRunRuntimeMillis +=
            Math.max(
                0L,
                toEpochMillis(runRecord.getEndTime()) - toEpochMillis(runRecord.getStartTime()));
      }

      OptionalLong prep =
          extractIntMetric(runRecord.getMetricsMap(), TestRecordProtoUtil.METRIC_KEY_PREP_TIME);
      if (prep.isPresent()) {
        recordPrepMillis = prep;
      }
      OptionalLong teardown =
          extractIntMetric(runRecord.getMetricsMap(), TestRecordProtoUtil.METRIC_KEY_TEARDOWN_TIME);
      if (teardown.isPresent()) {
        recordTeardownMillis = teardown;
      }

      for (ChildReference testRef : runRecord.getChildrenList()) {
        if (!testRef.hasInlineTestRecord()) {
          continue;
        }
        convertAndMergeTestRecord(testRef.getInlineTestRecord(), moduleId, testMethodsByClass);
      }
    }

    for (Map<Long, Optional<DebugInfo>> attemptsById : runAttemptsByRunId.values()) {
      if (!attemptsById.isEmpty() && attemptsById.values().stream().allMatch(Optional::isPresent)) {
        if (firstUnresolvedDebugInfo == null) {
          firstUnresolvedDebugInfo = attemptsById.values().iterator().next().get();
        }
      }
    }
    if (recordPrepMillis.isPresent()) {
      hasPrepTime = true;
      prepTimeMillis += recordPrepMillis.getAsLong();
    }
    if (recordTeardownMillis.isPresent()) {
      hasTeardownTime = true;
      teardownTimeMillis += recordTeardownMillis.getAsLong();
    }
    runtimeMillis += recordHasRunTimestamps ? recordRunRuntimeMillis : recordModuleRuntimeMillis;
  }

  void mergeFrom(ModuleAccumulator other) {
    if (contextAbi.isEmpty() && !other.contextAbi.isEmpty()) {
      contextAbi = other.contextAbi;
    }
    if (contextName.isEmpty() && !other.contextName.isEmpty()) {
      contextName = other.contextName;
    }
    runtimeMillis += other.runtimeMillis;
    if (other.hasPrepTime) {
      hasPrepTime = true;
      prepTimeMillis += other.prepTimeMillis;
    }
    if (other.hasTeardownTime) {
      hasTeardownTime = true;
      teardownTimeMillis += other.teardownTimeMillis;
    }
    if (firstUnresolvedDebugInfo == null && other.firstUnresolvedDebugInfo != null) {
      firstUnresolvedDebugInfo = other.firstUnresolvedDebugInfo;
    }
    for (Map.Entry<String, Map<String, Test>> classEntry : other.testMethodsByClass.entrySet()) {
      for (Map.Entry<String, Test> methodEntry : classEntry.getValue().entrySet()) {
        testMethodsByClass
            .computeIfAbsent(classEntry.getKey(), k -> new LinkedHashMap<>())
            .merge(
                methodEntry.getKey(),
                methodEntry.getValue(),
                CompatibilityReportUtil::pickPreferredTestAttempt);
      }
    }
  }

  Module toModule() {
    Module.Builder moduleBuilder = Module.newBuilder();
    populateModuleAbiAndName(moduleBuilder, moduleId, contextAbi, contextName);
    moduleBuilder.setRuntimeMillis(runtimeMillis);
    if (hasPrepTime) {
      moduleBuilder.setPrepTimeMillis(prepTimeMillis);
    }
    if (hasTeardownTime) {
      moduleBuilder.setTeardownTimeMillis(teardownTimeMillis);
    }

    boolean done = firstUnresolvedDebugInfo == null;
    moduleBuilder.setDone(done);
    if (!done) {
      moduleBuilder.setReason(buildModuleReason(firstUnresolvedDebugInfo));
    }

    for (Map.Entry<String, Map<String, Test>> classEntry : testMethodsByClass.entrySet()) {
      moduleBuilder.addTestCase(
          TestCase.newBuilder()
              .setName(classEntry.getKey())
              .addAllTest(classEntry.getValue().values())
              .build());
    }
    CompatibilityReportUtil.populateModuleTestCounts(moduleBuilder);
    return moduleBuilder.build();
  }

  private static OptionalLong extractIntMetric(Map<String, Metric> metricsMap, String key) {
    Metric metric = metricsMap.get(key);
    if (metric == null) {
      return OptionalLong.empty();
    }
    Measurements measurements = metric.getMeasurements();
    if (measurements.getMeasurementCase() == MeasurementCase.SINGLE_INT) {
      return OptionalLong.of(measurements.getSingleInt());
    }
    return OptionalLong.empty();
  }

  private static void populateModuleAbiAndName(
      Module.Builder moduleBuilder, String moduleId, String contextAbi, String contextName) {
    int spaceIdx = moduleId.indexOf(' ');
    String idAbi = spaceIdx > 0 ? moduleId.substring(0, spaceIdx) : "";
    String idName = spaceIdx > 0 ? moduleId.substring(spaceIdx + 1) : moduleId;

    String resolvedAbi =
        !contextAbi.isEmpty()
            ? contextAbi
            : (contextName.isEmpty()
                    || idName.equals(contextName)
                    || idName.startsWith(contextName + "["))
                ? idAbi
                : "";
    if (!resolvedAbi.isEmpty()) {
      moduleBuilder.setAbi(sanitizeModulePathComponent(resolvedAbi));
    }

    String idWithoutAbi =
        !resolvedAbi.isEmpty() && moduleId.startsWith(resolvedAbi + " ")
            ? moduleId.substring(resolvedAbi.length() + 1)
            : moduleId;
    // Tradefed sets module-name to the base name without '[param]'; preserve '[param]' from
    // moduleId when present.
    boolean hasParamSuffix =
        !contextName.isEmpty()
            && idWithoutAbi.startsWith(contextName + "[")
            && idWithoutAbi.endsWith("]");
    String resolvedName = contextName.isEmpty() || hasParamSuffix ? idWithoutAbi : contextName;
    moduleBuilder.setName(
        sanitizeModulePathComponent(resolvedName.isEmpty() ? moduleId : resolvedName));
  }

  private static String sanitizeModulePathComponent(String value) {
    String sanitized = value.replace('/', '_').replace('\\', '_').replace('\0', '_');
    while (sanitized.contains("..")) {
      sanitized = sanitized.replace("..", "__");
    }
    return sanitized;
  }

  private static Reason buildModuleReason(DebugInfo debugInfo) {
    String rawMsg =
        !debugInfo.getErrorMessage().isEmpty() ? debugInfo.getErrorMessage() : debugInfo.getTrace();
    if (rawMsg.isEmpty()) {
      rawMsg = CompatibilityReportUtil.INCOMPLETE_MODULE_DEFAULT_REASON;
    }
    String truncatedMsg = CompatibilityReportUtil.truncateStackTrace(rawMsg);
    Reason.Builder reasonBuilder = Reason.newBuilder().setMsg(escapeXml(truncatedMsg));
    String errorName = debugInfo.getDebugInfoContext().getErrorName();
    if (!errorName.isEmpty()) {
      reasonBuilder
          .setErrorName(errorName)
          .setErrorCode(Long.toString(debugInfo.getDebugInfoContext().getErrorCode()));
    }
    return reasonBuilder.build();
  }

  private static void convertAndMergeTestRecord(
      TestRecord testRecord, String moduleId, Map<String, Map<String, Test>> testMethodsByClass) {
    Optional<TestStatus> mappedStatus = mapTestStatus(testRecord);
    if (mappedStatus.isEmpty()) {
      return;
    }

    String testRecordId = testRecord.getTestRecordId();
    int hashIdx = testRecordId.indexOf('#');
    if (hashIdx <= 0 || hashIdx == testRecordId.length() - 1) {
      logger.atWarning().log(
          "Skipping malformed test record ID [%s] in module [%s]", testRecordId, moduleId);
      return;
    }
    String className = testRecordId.substring(0, hashIdx);
    String methodName = testRecordId.substring(hashIdx + 1);

    TestStatus status = mappedStatus.get();
    String compatibilityResult = TestStatus.convertToTestStatusCompatibilityString(status);

    Test.Builder testBuilder = Test.newBuilder().setName(methodName).setResult(compatibilityResult);
    if (status == TestStatus.IGNORED || status == TestStatus.SKIPPED) {
      testBuilder.setSkipped(true);
    }

    if (testRecord.hasDebugInfo()) {
      buildTestFailure(testRecord.getDebugInfo()).ifPresent(testBuilder::setFailure);
    }

    for (Map.Entry<String, Metric> metricEntry : testRecord.getMetricsMap().entrySet()) {
      formatMetricValue(metricEntry.getValue().getMeasurements())
          .ifPresent(formattedValue ->
              testBuilder.addMetric(
                  ReportProto.Metric.newBuilder()
                      .setKey(metricEntry.getKey())
                      .setContent(escapeXml(formattedValue))
                      .build()));
    }

    testMethodsByClass
        .computeIfAbsent(className, k -> new LinkedHashMap<>())
        .merge(methodName, testBuilder.build(), ModuleAccumulator::pickIntraModuleTestAttempt);
  }

  private static Test pickIntraModuleTestAttempt(Test existing, Test candidate) {
    if (existing.getResult().equals(PASS_STATUS)) {
      return existing;
    }
    String candidateResult = candidate.getResult();
    if (candidateResult.equals(PASS_STATUS)
        || candidateResult.equals(ASSUMPTION_FAILURE_STATUS)
        || candidateResult.equals(IGNORED_STATUS)) {
      return candidate;
    }
    if (!existing.getResult().equals(FAIL_STATUS) && candidateResult.equals(FAIL_STATUS)) {
      return candidate;
    }
    return existing;
  }

  private static Optional<TestStatus> mapTestStatus(TestRecord testRecord) {
    return switch (testRecord.getStatus()) {
      case PASS -> Optional.of(testRecord.hasSkipReason() ? TestStatus.SKIPPED : TestStatus.PASSED);
      case FAIL -> Optional.of(TestStatus.FAILURE);
      case IGNORED -> Optional.of(TestStatus.IGNORED);
      case ASSUMPTION_FAILURE -> Optional.of(TestStatus.ASSUMPTION_FAILURE);
      case UNKNOWN, UNRECOGNIZED -> Optional.empty();
    };
  }

  private static Optional<TestFailure> buildTestFailure(DebugInfo debugInfo) {
    String fullStack =
        !debugInfo.getTrace().isEmpty() ? debugInfo.getTrace() : debugInfo.getErrorMessage();
    if (fullStack.isEmpty()) {
      return Optional.empty();
    }

    String truncatedStack = CompatibilityReportUtil.truncateStackTrace(fullStack);
    int newlineIdx = truncatedStack.indexOf('\n');
    String firstLine = newlineIdx < 0 ? truncatedStack : truncatedStack.substring(0, newlineIdx);

    TestFailure.Builder failureBuilder =
        TestFailure.newBuilder()
            .setMsg(escapeXml(firstLine))
            .setStackTrace(StackTrace.newBuilder().setContent(escapeXml(truncatedStack)));
    String errorName = debugInfo.getDebugInfoContext().getErrorName();
    if (!errorName.isEmpty()) {
      failureBuilder
          .setErrorName(errorName)
          .setErrorCode(Long.toString(debugInfo.getDebugInfoContext().getErrorCode()));
    }
    return Optional.of(failureBuilder.build());
  }

  private static Optional<String> formatMetricValue(Measurements measurements) {
    return switch (measurements.getMeasurementCase()) {
      case SINGLE_STRING -> Optional.of(measurements.getSingleString());
      case SINGLE_INT -> Optional.of(Long.toString(measurements.getSingleInt()));
      case SINGLE_DOUBLE -> Optional.of(Double.toString(measurements.getSingleDouble()));
      case STRING_VALUES ->
          Optional.of(String.join(",", measurements.getStringValues().getStringValueList()));
      case NUMERIC_VALUES ->
          Optional.of(
              measurements.getNumericValues().getNumericValueList().stream()
                  .map(String::valueOf)
                  .collect(joining(",")));
      case DOUBLE_VALUES ->
          Optional.of(
              measurements.getDoubleValues().getDoubleValueList().stream()
                  .map(String::valueOf)
                  .collect(joining(",")));
      case MEASUREMENT_NOT_SET -> Optional.empty();
    };
  }

  private static String escapeXml(String text) {
    return XmlEscapers.xmlContentEscaper().escape(text);
  }
}
