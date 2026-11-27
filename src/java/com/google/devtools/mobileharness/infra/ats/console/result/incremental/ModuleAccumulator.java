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

import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements.MeasurementCase;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.DebugInfo;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.common.collect.Iterables;
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
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommonUtil;
import com.google.protobuf.Timestamp;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.annotation.Nullable;

/**
 * Accumulates per-shard Tradefed {@link TestRecord} entries for a single compatibility module ID,
 * evaluates per-module unit-count completion, and materializes the merged {@link Module} proto.
 */
final class ModuleAccumulator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Module metadata keys serialized by Tradefed's ModuleDefinition.
  private static final String MODULE_ID_ATTR = "module-id";
  private static final String MODULE_ABI_ATTR = "module-abi";
  private static final String MODULE_NAME_ATTR = "module-name";
  private static final String MODULE_SKIPPED_ATTR = "module-skipped";

  private static final String PASS_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.PASSED);
  private static final String FAIL_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE);
  private static final String ASSUMPTION_FAILURE_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.ASSUMPTION_FAILURE);
  private static final String IGNORED_STATUS =
      TestStatus.convertToTestStatusCompatibilityString(TestStatus.IGNORED);

  static String resolveModuleId(TestRecord moduleRecord, Context moduleContext) {
    String moduleId = moduleRecord.getTestRecordId();
    if (!moduleId.isEmpty()) {
      return moduleId;
    }
    return findFirstMetadataValue(moduleContext, MODULE_ID_ATTR).orElse("");
  }

  static Module createUnexecutedModule(String moduleId) {
    Module.Builder moduleBuilder = Module.newBuilder().setDone(false);
    populateModuleAbiAndName(moduleBuilder, moduleId, /* contextAbi= */ "", /* contextName= */ "");
    CompatibilityReportUtil.populateModuleTestCounts(moduleBuilder);
    return moduleBuilder.build();
  }

  static long toEpochMillis(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toEpochMilli();
  }

  private final String moduleId;
  private String contextAbi = "";
  private String contextName = "";
  private int moduleRecordCount;
  private long completedUnits;
  private boolean allRecordsHaveUnitsRun = true;
  private boolean hasModuleSkippedAttr;
  private boolean hasUninlinedChildRecord;
  private int totalTestRunCount;
  private long runtimeMillis;
  private long prepTimeMillis;
  private boolean hasPrepTime;
  private long teardownTimeMillis;
  private boolean hasTeardownTime;
  @Nullable private DebugInfo firstUnresolvedDebugInfo;
  @Nullable private DebugInfo fallbackDebugInfo;
  private final Map<String, Map<String, Test>> testMethodsByClass = new LinkedHashMap<>();

  ModuleAccumulator(String moduleId) {
    this.moduleId = moduleId;
  }

  void foldModuleRecord(TestRecord moduleRecord, Context moduleContext, Path moduleFile) {
    moduleRecordCount++;
    OptionalLong unitsRun = parseModuleUnitsRun(moduleContext, moduleFile);
    if (unitsRun.isPresent()) {
      completedUnits += unitsRun.getAsLong();
    } else {
      allRecordsHaveUnitsRun = false;
    }
    if (contextAbi.isEmpty()) {
      contextAbi =
          findFirstMetadataValue(moduleContext, MODULE_ABI_ATTR).map(String::trim).orElse("");
    }
    if (contextName.isEmpty()) {
      contextName =
          findFirstMetadataValue(moduleContext, MODULE_NAME_ATTR).map(String::trim).orElse("");
    }
    if (!findFirstMetadataValue(moduleContext, MODULE_SKIPPED_ATTR)
        .map(String::trim)
        .orElse("")
        .isEmpty()) {
      hasModuleSkippedAttr = true;
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
    Map<String, List<RunAttemptGroup>> runGroupsByRunId = new LinkedHashMap<>();
    for (ChildReference runRef : moduleRecord.getChildrenList()) {
      if (!runRef.hasInlineTestRecord()) {
        logger.atWarning().log(
            "Marking module [%s] incomplete due to non-inline child run reference [%s] in [%s]",
            moduleId, runRef.getTestRecordId(), moduleFile);
        hasUninlinedChildRecord = true;
        continue;
      }
      TestRecord runRecord = runRef.getInlineTestRecord();
      if (!runRecord.getTestRecordId().equals(moduleId)
          && SuiteCommonUtil.isModuleChecker(runRecord.getTestRecordId())) {
        continue;
      }
      totalTestRunCount++;
      List<RunAttemptGroup> groups =
          runGroupsByRunId.computeIfAbsent(runRecord.getTestRecordId(), k -> new ArrayList<>());
      DebugInfo runDebugInfo = runRecord.hasDebugInfo() ? runRecord.getDebugInfo() : null;
      long attemptId = runRecord.getAttemptId();
      // Tradefed auto-retries increment attempt_id monotonically; a non-increasing attempt_id
      // (such as a synthetic attempt_id=0 teardown failure) starts an independent run sequence.
      if (groups.isEmpty() || attemptId <= Iterables.getLast(groups).lastAttemptId) {
        groups.add(new RunAttemptGroup(attemptId, runDebugInfo));
      } else {
        Iterables.getLast(groups).recordNextAttempt(attemptId, runDebugInfo);
      }
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
          logger.atWarning().log(
              "Marking module [%s] incomplete due to non-inline child test reference [%s] in [%s]",
              moduleId, testRef.getTestRecordId(), moduleFile);
          hasUninlinedChildRecord = true;
          continue;
        }
        convertAndMergeTestRecord(testRef.getInlineTestRecord(), moduleId, testMethodsByClass);
      }
    }

    for (List<RunAttemptGroup> groups : runGroupsByRunId.values()) {
      for (RunAttemptGroup group : groups) {
        if (group.allHaveDebugInfo) {
          if (firstUnresolvedDebugInfo == null) {
            firstUnresolvedDebugInfo = group.firstDebugInfo;
          }
        } else if (fallbackDebugInfo == null) {
          fallbackDebugInfo = group.lastDebugInfo;
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
    moduleRecordCount += other.moduleRecordCount;
    completedUnits += other.completedUnits;
    allRecordsHaveUnitsRun &= other.allRecordsHaveUnitsRun;
    hasModuleSkippedAttr |= other.hasModuleSkippedAttr;
    hasUninlinedChildRecord |= other.hasUninlinedChildRecord;
    totalTestRunCount += other.totalTestRunCount;
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
    if (fallbackDebugInfo == null && other.fallbackDebugInfo != null) {
      fallbackDebugInfo = other.fallbackDebugInfo;
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

  boolean isJobPlanSatisfied(int plannedUnits, boolean jobCleanlyClosed) {
    if (isZeroUnitSkipCandidate()) {
      // Explicit module-skipped metadata (e.g. preflight skip clearing remaining shards) proves
      // an intentional skip without complete.pb; unannotated 0-unit shards fail closed unless the
      // job closed cleanly so interrupted shards are not misclassified as skipped.
      return jobCleanlyClosed || hasModuleSkippedAttr;
    }
    return allRecordsHaveUnitsRun ? completedUnits >= plannedUnits : jobCleanlyClosed;
  }

  boolean isUnplannedJobSatisfied(boolean jobCleanlyClosed) {
    return jobCleanlyClosed;
  }

  Module toModule(boolean planSatisfied) {
    Module.Builder moduleBuilder = Module.newBuilder();
    populateModuleAbiAndName(moduleBuilder, moduleId, contextAbi, contextName);
    if (planSatisfied && isZeroUnitSkipCandidate()) {
      return CompatibilityReportUtil.createSkippedModule(
          moduleBuilder.getAbi(),
          moduleBuilder.getName(),
          CompatibilityReportUtil.formatControllerSkippedReason(
              escapeXml(moduleBuilder.getName())));
    }

    moduleBuilder.setRuntimeMillis(runtimeMillis);
    if (hasPrepTime) {
      moduleBuilder.setPrepTimeMillis(prepTimeMillis);
    }
    if (hasTeardownTime) {
      moduleBuilder.setTeardownTimeMillis(teardownTimeMillis);
    }

    boolean done = planSatisfied && !hasUninlinedChildRecord && firstUnresolvedDebugInfo == null;
    moduleBuilder.setDone(done);
    if (!done) {
      DebugInfo reasonDebugInfo =
          firstUnresolvedDebugInfo != null ? firstUnresolvedDebugInfo : fallbackDebugInfo;
      if (reasonDebugInfo != null) {
        moduleBuilder.setReason(buildModuleReason(reasonDebugInfo));
      } else {
        moduleBuilder.setReason(
            Reason.newBuilder().setMsg(CompatibilityReportUtil.INCOMPLETE_MODULE_DEFAULT_REASON));
      }
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

  // Structural candidate for a controller/preflight skip. Excludes non-inline child references
  // because skipped run references leave totalTestRunCount == 0 and would otherwise look skipped.
  private boolean isZeroUnitSkipCandidate() {
    return moduleRecordCount > 0
        && allRecordsHaveUnitsRun
        && totalTestRunCount == 0
        && completedUnits == 0
        && !hasUninlinedChildRecord
        && firstUnresolvedDebugInfo == null;
  }

  private static OptionalLong parseModuleUnitsRun(Context moduleContext, Path moduleFile) {
    Optional<String> rawValueOpt =
        findFirstMetadataValue(moduleContext, XtsConstants.MODULE_UNITS_RUN_ATTR);
    if (rawValueOpt.isEmpty()) {
      return OptionalLong.empty();
    }
    String rawValue = rawValueOpt.get().trim();
    long parsed;
    try {
      parsed = Long.parseLong(rawValue);
    } catch (NumberFormatException e) {
      logger.atWarning().log(
          "Invalid %s value [%s] in [%s]",
          XtsConstants.MODULE_UNITS_RUN_ATTR, rawValue, moduleFile);
      return OptionalLong.empty();
    }
    if (parsed < 0) {
      logger.atWarning().log(
          "Negative %s value [%d] in [%s]", XtsConstants.MODULE_UNITS_RUN_ATTR, parsed, moduleFile);
      return OptionalLong.empty();
    }
    return OptionalLong.of(parsed);
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

  private static Optional<String> findFirstMetadataValue(Context context, String key) {
    for (Metadata metadata : context.getMetadataList()) {
      if (metadata.getKey().equals(key) && metadata.getValueCount() > 0) {
        return Optional.of(metadata.getValue(0));
      }
    }
    return Optional.empty();
  }

  private static String escapeXml(String text) {
    return XmlEscapers.xmlContentEscaper().escape(text);
  }

  private static final class RunAttemptGroup {
    private long lastAttemptId;
    @Nullable private final DebugInfo firstDebugInfo;
    @Nullable private DebugInfo lastDebugInfo;
    private boolean allHaveDebugInfo;

    RunAttemptGroup(long attemptId, @Nullable DebugInfo debugInfo) {
      this.lastAttemptId = attemptId;
      this.firstDebugInfo = debugInfo;
      this.lastDebugInfo = debugInfo;
      this.allHaveDebugInfo = debugInfo != null;
    }

    void recordNextAttempt(long attemptId, @Nullable DebugInfo debugInfo) {
      this.lastAttemptId = attemptId;
      this.lastDebugInfo = debugInfo;
      this.allHaveDebugInfo &= debugInfo != null;
    }
  }
}
