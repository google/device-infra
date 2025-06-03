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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.BuildInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Run;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.RunHistory;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.infra.ats.console.util.tradefed.TestRecordProtoUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommonUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Inject;

/** Merger to merge compatibility xTS reports. */
public class CompatibilityReportMerger {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final long PARSE_TIMEOUT_IN_HOUR = 1;

  private static final ImmutableSet<String> EXTRA_BUILD_INFO_ATTR_TO_KEPT =
      ImmutableSet.of(
          XmlConstants.DEVICE_KERNEL_INFO_ATTR,
          XmlConstants.SYSTEM_IMG_INFO_ATTR,
          XmlConstants.VENDOR_IMG_INFO_ATTR);

  private final ListeningExecutorService threadPool;
  private final CompatibilityReportParser reportParser;
  private final MoblyReportParser moblyReportParser;

  @Inject
  CompatibilityReportMerger(
      ListeningExecutorService threadPool,
      CompatibilityReportParser reportParser,
      MoblyReportParser moblyReportParser) {
    this.threadPool = threadPool;
    this.reportParser = reportParser;
    this.moblyReportParser = moblyReportParser;
  }

  /**
   * Parses a list of XML report files and merges them to a single report.
   *
   * <p>Note: the XML report files must have the same device build fingerprint info, otherwise the
   * merge won't proceed.
   */
  public Optional<Result> mergeXmlReports(List<Path> reportXmlFiles, boolean skipDeviceInfo)
      throws MobileHarnessException, InterruptedException {
    return mergeResultBundles(
            reportXmlFiles.stream()
                .map(file -> TradefedResultBundle.of(file, Optional.empty()))
                .collect(toImmutableList()),
            skipDeviceInfo)
        .report();
  }

  /**
   * Parses a map of XML report files with corresponding test record files and merges them to a
   * single report.
   *
   * <p>Note: the XML report files must have the same device build fingerprint info, otherwise the
   * merge won't proceed.
   */
  public ParseResult mergeResultBundles(
      List<TradefedResultBundle> resultBundles, boolean skipDeviceInfo)
      throws MobileHarnessException, InterruptedException {
    List<ParseResult> parseResults = parseResultBundles(resultBundles);
    return ParseResult.of(
        /* originalReportFile= */ Optional.empty(),
        mergeParsedReports(parseResults, skipDeviceInfo),
        parseResults.stream()
            .flatMap(parseResult -> parseResult.skippedModuleIds().stream())
            .collect(toImmutableSet()));
  }

  /**
   * Parses a list of Mobly report files and merges them to a single report.
   *
   * <p>Note: the Mobly report files must have the same device build fingerprint info, otherwise the
   * merge won't proceed.
   */
  public Optional<Result> mergeMoblyReports(
      List<MoblyReportInfo> moblyReports, boolean skipDeviceInfo)
      throws MobileHarnessException, InterruptedException {
    return mergeParsedReports(parseMoblyReports(moblyReports), skipDeviceInfo);
  }

  /**
   * Parses a list of reports and merges them to a single report.
   *
   * @param validateReports whether to validate reports before the merge
   */
  public Optional<Result> mergeReports(
      List<Result> reports, boolean validateReports, boolean skipDeviceInfo)
      throws MobileHarnessException {
    ImmutableList<Result> usableReports =
        reports.stream()
            .filter(
                report ->
                    skipDeviceInfo
                        || (report.hasBuild()
                            && !report.getBuild().getBuildFingerprint().isEmpty()))
            .collect(toImmutableList());
    logger.atInfo().log(
        "Given reports number: %d, usable reports number: %d",
        reports.size(), usableReports.size());
    if (!skipDeviceInfo
        && validateReports
        && !validateReportsWithSameBuildFingerprint(usableReports)) {
      return Optional.empty();
    }

    if (usableReports.isEmpty()) {
      return Optional.empty();
    } else if (usableReports.size() == 1) {
      // No need to merge
      return Optional.of(usableReports.get(0));
    }

    ImmutableList.Builder<Run> runs = ImmutableList.builder();
    ImmutableList.Builder<Module> modules = ImmutableList.builder();

    long passedInSummary = 0L;
    long failedInSummary = 0L;
    long warningInSummary = 0L;
    for (Result report : usableReports) {
      List<Run> runsInReport =
          report.hasRunHistory() ? report.getRunHistory().getRunList() : ImmutableList.of();
      runs.addAll(runsInReport);
      modules.addAll(report.getModuleInfoList());
      // Accumulates counts in Summary
      passedInSummary += report.getSummary().getPassed();
      failedInSummary += report.getSummary().getFailed();
      warningInSummary += report.getSummary().getWarning();
    }

    ImmutableList<Module> mergedModuleList = mergeModules(modules.build());

    ImmutableList<Module> modulesWithoutModuleCheckers =
        mergedModuleList.stream()
            .filter(module -> !SuiteCommonUtil.isModuleChecker(module))
            .collect(toImmutableList());
    int modulesDoneInSummary =
        (int) modulesWithoutModuleCheckers.stream().filter(Module::getDone).count();
    int modulesTotalInSummary = modulesWithoutModuleCheckers.size();

    Summary summary =
        Summary.newBuilder()
            .setPassed(passedInSummary)
            .setFailed(failedInSummary)
            .setWarning(warningInSummary)
            .setModulesDone(modulesDoneInSummary)
            .setModulesTotal(modulesTotalInSummary)
            .build();

    Result.Builder res = Result.newBuilder();
    res.setBuild(getNewBuildInfo(usableReports))
        .setSummary(summary)
        .addAllModuleInfo(mergedModuleList)
        .addAllAttribute(getNewResultAttrs(usableReports));

    if (!runs.build().isEmpty()) {
      res.setRunHistory(RunHistory.newBuilder().addAllRun(runs.build()));
    }

    return Optional.of(res.build());
  }

  private static ImmutableList<Module> mergeModules(List<Module> modules) {
    if (modules.isEmpty()) {
      return ImmutableList.of();
    } else if (modules.size() == 1) {
      return ImmutableList.copyOf(modules);
    }

    ImmutableList.Builder<Module> mergedModules = ImmutableList.builder();
    LinkedHashMap<String, ImmutableList<Module>> moduleMap =
        modules.stream()
            .collect(
                Collectors.groupingBy(
                    module -> module.getName() + "_" + module.getAbi(),
                    LinkedHashMap::new,
                    toImmutableList()));

    for (ImmutableList<Module> moduleList : moduleMap.values()) {
      mergeModulesWithSameNameAndAbi(moduleList).ifPresent(mergedModules::add);
    }

    return mergedModules.build();
  }

  private static Optional<Module> mergeModulesWithSameNameAndAbi(List<Module> modules) {
    if (modules.isEmpty()) {
      return Optional.empty();
    } else if (modules.size() == 1) {
      return Optional.of(modules.get(0));
    }

    Module.Builder mergedModule =
        Module.newBuilder().setName(modules.get(0).getName()).setAbi(modules.get(0).getAbi());
    long mergedModuleRuntime = 0L;
    boolean mergedModuleDone = true;
    int mergedModulePassedTests = 0;
    int mergedModuleFailedTests = 0;
    int mergedModuleWarningTests = 0;
    int mergedModuleTotalTests = 0;
    List<TestCase> testCases = new ArrayList<>();
    Reason moduleNotDoneReason = null;
    for (Module module : modules) {
      mergedModuleRuntime += module.getRuntimeMillis();
      mergedModuleDone &= module.getDone();
      if (module.hasReason()) {
        moduleNotDoneReason = module.getReason();
      }
      testCases.addAll(module.getTestCaseList());
    }
    ImmutableList<TestCase> mergedtestCases = mergeTestCasesFromSameModule(testCases);
    for (TestCase testCase : mergedtestCases) {
      for (Test test : testCase.getTestList()) {
        if (test.getResult()
            .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.PASSED))) {
          mergedModulePassedTests++;
        } else if (test.getResult()
            .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE))) {
          mergedModuleFailedTests++;
        } else if (test.getResult()
            .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.WARNING))) {
          mergedModuleWarningTests++;
        }
        mergedModuleTotalTests++;
      }
    }
    mergedModule
        .setRuntimeMillis(mergedModuleRuntime)
        .setDone(mergedModuleDone)
        .setPassed(mergedModulePassedTests)
        .setFailedTests(mergedModuleFailedTests)
        .setWarningTests(mergedModuleWarningTests)
        .setTotalTests(mergedModuleTotalTests)
        .addAllTestCase(mergedtestCases);
    if (moduleNotDoneReason != null) {
      mergedModule.setReason(moduleNotDoneReason);
    }
    return Optional.of(mergedModule.build());
  }

  private static ImmutableList<TestCase> mergeTestCasesFromSameModule(List<TestCase> testCases) {
    if (testCases.isEmpty()) {
      return ImmutableList.of();
    } else if (testCases.size() == 1) {
      return ImmutableList.copyOf(testCases);
    }

    ImmutableList.Builder<TestCase> mergedTestCases = ImmutableList.builder();
    LinkedHashMap<String, ImmutableList<TestCase>> testCaseMap =
        testCases.stream()
            .collect(
                Collectors.groupingBy(TestCase::getName, LinkedHashMap::new, toImmutableList()));

    for (ImmutableList<TestCase> testCaseList : testCaseMap.values()) {
      mergeTestCasesWithSameName(testCaseList).ifPresent(mergedTestCases::add);
    }

    return mergedTestCases.build();
  }

  private static Optional<TestCase> mergeTestCasesWithSameName(List<TestCase> testCases) {
    if (testCases.isEmpty()) {
      return Optional.empty();
    } else if (testCases.size() == 1) {
      return Optional.of(testCases.get(0));
    }

    TestCase.Builder mergedTestCase = TestCase.newBuilder().setName(testCases.get(0).getName());
    LinkedHashMap<String, ImmutableList<Test>> testMap =
        testCases.stream()
            .flatMap(testCase -> testCase.getTestList().stream())
            .collect(Collectors.groupingBy(Test::getName, LinkedHashMap::new, toImmutableList()));
    for (ImmutableList<Test> tests : testMap.values()) {
      mergeTestsWithSameName(tests).ifPresent(mergedTestCase::addTest);
    }

    return Optional.of(mergedTestCase.build());
  }

  private static Optional<Test> mergeTestsWithSameName(List<Test> tests) {
    if (tests.isEmpty()) {
      return Optional.empty();
    } else if (tests.size() == 1) {
      return Optional.of(tests.get(0));
    }

    logger.atInfo().log("Found %d duplicated test cases: %s", tests.size(), tests.get(0).getName());
    Optional<Test> passTest =
        tests.stream()
            .filter(
                test ->
                    test.getResult()
                        .equals(
                            TestStatus.convertToTestStatusCompatibilityString(TestStatus.PASSED)))
            .findFirst();
    if (passTest.isPresent()) {
      return passTest;
    }
    return Optional.of(tests.get(0));
  }

  /**
   * Merges a list of {@link ParseResult} into a single report.
   *
   * <p>Note: the report in the {@link ParseResult} must have the same device build fingerprint
   * info, otherwise the merge won't proceed.
   */
  private Optional<Result> mergeParsedReports(
      List<ParseResult> reportParseResults, boolean skipDeviceInfo) throws MobileHarnessException {
    // Filters parse results that have report and its build info has device build fingerprint
    ImmutableList<ParseResult> parseResults =
        reportParseResults.stream()
            .filter(
                parseResult ->
                    parseResult.report().isPresent()
                        && (skipDeviceInfo
                            || (parseResult.report().get().hasBuild()
                                && !parseResult
                                    .report()
                                    .get()
                                    .getBuild()
                                    .getBuildFingerprint()
                                    .isEmpty())))
            .collect(toImmutableList());
    logger.atInfo().log(
        "Given reports number: %d, usable parsed results number: %d",
        reportParseResults.size(), parseResults.size());
    if (!skipDeviceInfo && !validateParsedReportsWithSameBuildFingerprint(parseResults)) {
      return Optional.empty();
    }

    return mergeReports(
        parseResults.stream().map(pr -> pr.report().get()).collect(toImmutableList()),
        /* validateReports= */ false,
        skipDeviceInfo);
  }

  /** Gets the attributes showed in the <Result> element in the merged report. */
  private static ImmutableList<Attribute> getNewResultAttrs(List<Result> reports) {
    AtomicReference<Long> startTime = new AtomicReference<>(null);
    AtomicReference<Long> endTime = new AtomicReference<>(null);
    AtomicReference<String> startTimeDisplay = new AtomicReference<>("");
    AtomicReference<String> endTimeDisplay = new AtomicReference<>("");
    LinkedHashSet<String> deviceSerials = new LinkedHashSet<>();
    LinkedHashMap<String, String> newAttrs =
        new LinkedHashMap<>(); // Use LinkedHashMap to keep entry order
    reports.forEach(
        report -> {
          List<Attribute> resultAttrsList = report.getAttributeList();
          LinkedHashMap<String, String> attrsMap =
              resultAttrsList.stream()
                  .collect(
                      toMap(
                          Attribute::getKey,
                          Attribute::getValue,
                          (e1, e2) -> e1,
                          LinkedHashMap::new));

          for (Attribute attr : resultAttrsList) {
            newAttrs.putIfAbsent(attr.getKey(), attr.getValue());
            if (Objects.equals(attr.getKey(), XmlConstants.START_TIME_ATTR)) {
              Long currentStartTime = Longs.tryParse(attr.getValue().trim());
              if ((startTime.get() == null && currentStartTime != null)
                  || (currentStartTime != null && currentStartTime < startTime.get())) {
                startTime.set(currentStartTime);
                startTimeDisplay.set(
                    attrsMap.getOrDefault(XmlConstants.START_DISPLAY_TIME_ATTR, ""));
              }
            } else if (Objects.equals(attr.getKey(), XmlConstants.END_TIME_ATTR)) {
              Long currentEndTime = Longs.tryParse(attr.getValue().trim());
              if ((endTime.get() == null && currentEndTime != null)
                  || (currentEndTime != null && currentEndTime > endTime.get())) {
                endTime.set(currentEndTime);
                endTimeDisplay.set(attrsMap.getOrDefault(XmlConstants.END_DISPLAY_TIME_ATTR, ""));
              }
            } else if (Objects.equals(attr.getKey(), XmlConstants.DEVICES_ATTR)) {
              deviceSerials.addAll(
                  Splitter.on(',')
                      .omitEmptyStrings()
                      .trimResults()
                      .splitToList(attr.getValue().trim()));
            }
          }
        });
    // Now update values for attributes start, end, start_display, end_display, command_line_args,
    // and devices.
    newAttrs.put(
        XmlConstants.START_TIME_ATTR, startTime.get() == null ? "" : startTime.get().toString());
    newAttrs.put(XmlConstants.END_TIME_ATTR, endTime.get() == null ? "" : endTime.get().toString());
    newAttrs.put(XmlConstants.START_DISPLAY_TIME_ATTR, startTimeDisplay.get());
    newAttrs.put(XmlConstants.END_DISPLAY_TIME_ATTR, endTimeDisplay.get());
    if (newAttrs.containsKey(XmlConstants.COMMAND_LINE_ARGS)) {
      newAttrs.remove(XmlConstants.COMMAND_LINE_ARGS);
    }
    newAttrs.put(XmlConstants.DEVICES_ATTR, Joiner.on(",").join(deviceSerials));

    return newAttrs.entrySet().stream()
        .map(e -> Attribute.newBuilder().setKey(e.getKey()).setValue(e.getValue()).build())
        .collect(toImmutableList());
  }

  private static BuildInfo getNewBuildInfo(List<Result> report) {
    BuildInfo buildInfo = report.stream().findFirst().get().getBuild();
    ImmutableList<Attribute> attrs =
        buildInfo.getAttributeList().stream()
            .filter(
                attr ->
                    attr.getKey().startsWith("build_")
                        || EXTRA_BUILD_INFO_ATTR_TO_KEPT.contains(attr.getKey()))
            .collect(toImmutableList());

    return buildInfo.toBuilder().clearAttribute().addAllAttribute(attrs).build();
  }

  // Ensures all parsed results have same device build fingerprint so the merge will be valid.
  private static boolean validateParsedReportsWithSameBuildFingerprint(
      List<ParseResult> parseResults) throws MobileHarnessException {
    return validateReportsWithSameBuildFingerprint(
        parseResults.stream().map(pr -> pr.report().get()).collect(toImmutableList()));
  }

  // Ensures all reports have same device build fingerprint so the merge will be valid.
  @VisibleForTesting
  static boolean validateReportsWithSameBuildFingerprint(List<Result> reports)
      throws MobileHarnessException {
    if (reports.isEmpty()) {
      return false;
    }
    Result firstReport =
        reports.stream()
            .filter(
                report -> report.hasBuild() && !report.getBuild().getBuildFingerprint().isEmpty())
            .findFirst()
            .orElse(null);
    if (firstReport == null) {
      throw new MobileHarnessException(
          ExtErrorId.REPORT_MERGER_NO_DEVICE_BUILD_FINGERPRINT_FOUND,
          "Not found any report with info about device build_fingerprint");
    }
    String baseBuildFingerprint = getBuildFingerprint(firstReport);
    for (Result report : reports) {
      if (!getBuildFingerprint(report).equals(baseBuildFingerprint)) {
        throw new MobileHarnessException(
            ExtErrorId.REPORT_MERGER_DIFF_DEVICE_BUILD_FINGERPRINT_FOUND,
            String.format(
                "Expected build fingerprint [%s] but found a report with different build"
                    + " fingerprint: %s",
                baseBuildFingerprint, report.getBuild().getBuildFingerprint()));
      }
    }
    return true;
  }

  /**
   * TF tests stores original build_fingerprint to build_fingerprint_unaltered for suites which edit
   * the original one.
   */
  private static String getBuildFingerprint(Result report) {
    if (report.getBuild().getBuildFingerprint().isEmpty()) {
      return "";
    }

    return report.getBuild().getBuildFingerprintUnaltered().isEmpty()
        ? report.getBuild().getBuildFingerprint()
        : report.getBuild().getBuildFingerprintUnaltered();
  }

  /** Parses multiple XML reports syncly. */
  @VisibleForTesting
  List<ParseResult> parseResultBundles(List<TradefedResultBundle> resultBundles)
      throws MobileHarnessException, InterruptedException {
    try {
      return parseResultBundlesAsync(resultBundles).get(PARSE_TIMEOUT_IN_HOUR, HOURS);
    } catch (TimeoutException e) {
      throw new MobileHarnessException(
          ExtErrorId.REPORT_MERGER_PARSE_REPORTS_TIMEOUT_ERROR,
          "Timeout while parsing report xml files",
          e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException) {
        throw (MobileHarnessException) e.getCause();
      } else {
        throw new MobileHarnessException(
            ExtErrorId.REPORT_MERGER_PARSE_REPORTS_GENERIC_ERROR,
            "Failed to parse report xml file",
            e);
      }
    }
  }

  /** Parses multiple XML reports asyncly. */
  private ListenableFuture<List<ParseResult>> parseResultBundlesAsync(
      List<TradefedResultBundle> resultBundles) {
    List<ListenableFuture<ParseResult>> parseReportFutures = new ArrayList<>();
    logger.atInfo().log(
        "Start to parse result bundle files:\n - %s",
        resultBundles.stream()
            .map(
                bundle ->
                    String.format(
                        "Test result XML: %s, Test record file: %s",
                        bundle.xmlReportFile(), bundle.testRecordFile()))
            .collect(joining(",\n - ")));
    resultBundles.forEach(bundle -> parseReportFutures.add(parseResultBundleAsync(bundle)));
    return Futures.allAsList(parseReportFutures);
  }

  /** Parses one XML report asyncly. */
  private ListenableFuture<ParseResult> parseResultBundleAsync(TradefedResultBundle resultBundle) {
    return threadPool.submit(() -> parseResultBundle(resultBundle));
  }

  private ParseResult parseResultBundle(TradefedResultBundle resultBundle)
      throws MobileHarnessException {
    Path xmlReportFile = resultBundle.xmlReportFile();
    Optional<Result> report = reportParser.parse(xmlReportFile, /* shallow= */ false);
    ImmutableSet<String> skippedModuleIds = ImmutableSet.of();
    if (resultBundle.testRecordFile().isPresent() && report.isPresent()) {
      Optional<TestRecord> testRecord = readTestRecord(resultBundle.testRecordFile().get());
      if (testRecord.isPresent()) {
        report = Optional.of(insertMetadataFromTestRecord(report.get(), testRecord.get()));
        skippedModuleIds = getSkippedModuleIds(testRecord.get());
      }
    }

    return ParseResult.of(Optional.of(xmlReportFile), report, skippedModuleIds);
  }

  @VisibleForTesting
  static Optional<TestRecord> readTestRecord(Path testRecordPath) {
    try {
      return Optional.of(TestRecordProtoUtil.readFromFile(new File(testRecordPath.toString())));
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to read test record file from path: %s.", testRecordPath);
      return Optional.empty();
    }
  }

  @VisibleForTesting
  static Result insertMetadataFromTestRecord(Result report, TestRecord testRecord) {
    // The metrics map - Map<TestRecord ID, Map<Metric Key, Metric Value>> from the test
    // record.
    Map<String, Map<String, Metric>> metricsMap = new LinkedHashMap<>();
    generateTestRecordMetricsMap(testRecord, metricsMap);

    Result.Builder reportBuilder = report.toBuilder();
    List<Module> modules = reportBuilder.getModuleInfoList();
    reportBuilder.clearModuleInfo();
    for (Module module : modules) {
      String moduleName = String.format("%s %s", module.getAbi(), module.getName());
      Module.Builder builder = module.toBuilder();
      Map<String, Metric> metrics = metricsMap.get(moduleName);
      if (metrics != null) {
        Metric prepTimeMetric = metrics.get(TestRecordProtoUtil.METRIC_KEY_PREP_TIME);
        if (prepTimeMetric != null) {
          builder.setPrepTimeMillis(prepTimeMetric.getMeasurements().getSingleInt());
        }
        Metric tearDownTimeMetric = metrics.get(TestRecordProtoUtil.METRIC_KEY_TEARDOWN_TIME);
        if (tearDownTimeMetric != null) {
          builder.setTeardownTimeMillis(tearDownTimeMetric.getMeasurements().getSingleInt());
        }
      }
      reportBuilder.addModuleInfo(builder.build());
    }
    return reportBuilder.build();
  }

  /** Gets the skipped module ids (abi module_name) from the test record. */
  @VisibleForTesting
  static ImmutableSet<String> getSkippedModuleIds(TestRecord testRecord) {
    return testRecord.getChildrenList().stream()
        .map(ChildReference::getInlineTestRecord)
        .filter(
            moduleRecord ->
                !moduleRecord.getTestRecordId().isEmpty()
                    // Module is skipped if it has no children and no failure.
                    && moduleRecord.getChildrenList().isEmpty()
                    && !moduleRecord.hasDebugInfo())
        .map(TestRecord::getTestRecordId)
        .collect(toImmutableSet());
  }

  /**
   * Goes through all TestRecord instances including children TestRecord and maps the test record id
   * to its metrics.
   */
  @VisibleForTesting
  static void generateTestRecordMetricsMap(
      TestRecord testRecord, Map<String, Map<String, Metric>> metricsMap) {
    if (!testRecord.getTestRecordId().isEmpty()) {
      Map<String, Metric> metrics = testRecord.getMetricsMap();
      if (!metrics.isEmpty()) {
        metricsMap.put(testRecord.getTestRecordId(), metrics);
      }
    }
    for (ChildReference child : testRecord.getChildrenList()) {
      generateTestRecordMetricsMap(child.getInlineTestRecord(), metricsMap);
    }
  }

  /** Parses multiple Mobly reports syncly. */
  @VisibleForTesting
  List<ParseResult> parseMoblyReports(List<MoblyReportInfo> moblyReports)
      throws MobileHarnessException, InterruptedException {
    try {
      return parseMoblyReportsAsync(moblyReports).get(PARSE_TIMEOUT_IN_HOUR, HOURS);
    } catch (TimeoutException e) {
      throw new MobileHarnessException(
          ExtErrorId.REPORT_MERGER_PARSE_MOBLY_REPORTS_TIMEOUT_ERROR,
          "Timeout while parsing Mobly reports",
          e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException) {
        throw (MobileHarnessException) e.getCause();
      } else {
        throw new MobileHarnessException(
            ExtErrorId.REPORT_MERGER_PARSE_MOBLY_REPORTS_GENERIC_ERROR,
            "Failed to parse Mobly reports",
            e);
      }
    }
  }

  /** Parses multiple Mobly reports asyncly. */
  private ListenableFuture<List<ParseResult>> parseMoblyReportsAsync(
      List<MoblyReportInfo> moblyReports) {
    List<ListenableFuture<ParseResult>> parseMoblyReportFutures = new ArrayList<>();
    logger.atInfo().log(
        "Start to parse Mobly report files:\n - %s",
        moblyReports.stream()
            .map(mr -> mr.moblySummaryFile() == null ? "" : mr.moblySummaryFile().toString())
            .collect(joining(",\n - ")));
    for (MoblyReportInfo moblyReportInfo : moblyReports) {
      parseMoblyReportFutures.add(parseMoblyReportAsync(moblyReportInfo));
    }
    return Futures.allAsList(parseMoblyReportFutures);
  }

  /** Parses one Mobly report asyncly. */
  private ListenableFuture<ParseResult> parseMoblyReportAsync(MoblyReportInfo moblyReportInfo) {
    return threadPool.submit(() -> parseMoblyReport(moblyReportInfo));
  }

  private ParseResult parseMoblyReport(MoblyReportInfo moblyReportInfo)
      throws MobileHarnessException {
    return ParseResult.of(
        Optional.ofNullable(moblyReportInfo.moblySummaryFile()),
        moblyReportParser.parseMoblyTestResult(moblyReportInfo));
  }

  /** Data class for the xTS report parse result. */
  @AutoValue
  public abstract static class ParseResult {

    /** Creates a {@link ParseResult}. */
    public static ParseResult of(Optional<Path> originalReportFile, Optional<Result> report) {
      return new AutoValue_CompatibilityReportMerger_ParseResult(
          originalReportFile, report, ImmutableSet.of());
    }

    public static ParseResult of(
        Optional<Path> originalReportFile,
        Optional<Result> report,
        ImmutableSet<String> skippedModuleIds) {
      return new AutoValue_CompatibilityReportMerger_ParseResult(
          originalReportFile, report, skippedModuleIds);
    }

    public abstract Optional<Path> originalReportFile();

    /** The parsed report. */
    public abstract Optional<Result> report();

    /** Skipped module ids in the report. */
    public abstract ImmutableSet<String> skippedModuleIds();
  }

  /** A bundle of a Tradefed result including the XML file and the test record file. */
  @AutoValue
  public abstract static class TradefedResultBundle {

    /** Creates a {@link ParseResult}. */
    public static TradefedResultBundle of(Path xmlReportFile, Optional<Path> testRecordFile) {
      return new AutoValue_CompatibilityReportMerger_TradefedResultBundle(
          xmlReportFile, testRecordFile);
    }

    /** Path of the XML report file. */
    public abstract Path xmlReportFile();

    /** Path of the test_record.pb which is optional. */
    public abstract Optional<Path> testRecordFile();
  }
}
