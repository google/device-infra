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
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
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
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Inject;

/** Merger to merge compatibitly xTS reports. */
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
  public Optional<Result> mergeXmlReports(List<Path> reportXmlFiles)
      throws MobileHarnessException, InterruptedException {
    return mergeParsedReports(parseXmlReports(reportXmlFiles));
  }

  /**
   * Parses a list of Mobly report files and merges them to a single report.
   *
   * <p>Note: the Mobly report files must have the same device build fingerprint info, otherwise the
   * merge won't proceed.
   */
  public Optional<Result> mergeMoblyReports(List<MoblyReportInfo> moblyReports)
      throws MobileHarnessException, InterruptedException {
    return mergeParsedReports(parseMoblyReports(moblyReports));
  }

  /**
   * Parses a list of reports and merges them to a single report.
   *
   * @param validateReports whether to validate reports before the merge
   */
  public Optional<Result> mergeReports(List<Result> reports, boolean validateReports) {
    ImmutableList<Result> usableReports =
        reports.stream()
            .filter(
                report -> report.hasBuild() && !report.getBuild().getBuildFingerprint().isEmpty())
            .collect(toImmutableList());
    logger.atInfo().log(
        "Given reports number: %d, usable reports number: %d",
        reports.size(), usableReports.size());
    if (validateReports && !validateReportsWithSameBuildFingerprint(usableReports)) {
      return Optional.empty();
    }

    if (usableReports.size() == 1) {
      // No need to merge
      return Optional.of(usableReports.get(0));
    }

    ImmutableList.Builder<Run> runs = ImmutableList.builder();
    ImmutableList.Builder<Module> modules = ImmutableList.builder();

    long passedInSummary = 0L;
    long failedInSummary = 0L;
    for (Result report : usableReports) {
      List<Run> runsInReport =
          report.hasRunHistory() ? report.getRunHistory().getRunList() : ImmutableList.of();
      runs.addAll(runsInReport);
      modules.addAll(report.getModuleInfoList());
      // Accumulates counts in Summary
      passedInSummary += report.getSummary().getPassed();
      failedInSummary += report.getSummary().getFailed();
    }

    ImmutableList<Module> mergedModuleList = mergeModules(modules.build());
    int modulesDoneInSummary = (int) mergedModuleList.stream().filter(Module::getDone).count();
    int modulesTotalInSummary = mergedModuleList.size();

    Summary summary =
        Summary.newBuilder()
            .setPassed(passedInSummary)
            .setFailed(failedInSummary)
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
    int mergedModuleTotalTests = 0;
    List<TestCase> testCases = new ArrayList<>();
    Reason moduleNotDoneReason = null;
    for (Module module : modules) {
      mergedModuleRuntime += module.getRuntimeMillis();
      mergedModuleDone &= module.getDone();
      mergedModulePassedTests += module.getPassed();
      mergedModuleTotalTests += module.getTotalTests();
      if (module.hasReason()) {
        moduleNotDoneReason = module.getReason();
      }
      testCases.addAll(module.getTestCaseList());
    }
    testCases = mergeTestCasesFromSameModule(testCases);
    mergedModule
        .setRuntimeMillis(mergedModuleRuntime)
        .setDone(mergedModuleDone)
        .setPassed(mergedModulePassedTests)
        .setTotalTests(mergedModuleTotalTests)
        .addAllTestCase(testCases);
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
    for (TestCase testCase : testCases) {
      mergedTestCase.addAllTest(testCase.getTestList());
    }

    return Optional.of(mergedTestCase.build());
  }

  /**
   * Merges a list of {@link ParseResult} into a single report.
   *
   * <p>Note: the report in the {@link ParseResult} must have the same device build fingerprint
   * info, otherwise the merge won't proceed.
   */
  private Optional<Result> mergeParsedReports(List<ParseResult> reportParseResults) {
    // Filters parse results that have report and its build info has device build fingerprint
    ImmutableList<ParseResult> parseResults =
        reportParseResults.stream()
            .filter(
                parseResult ->
                    parseResult.report().isPresent()
                        && parseResult.report().get().hasBuild()
                        && !parseResult.report().get().getBuild().getBuildFingerprint().isEmpty())
            .collect(toImmutableList());
    logger.atInfo().log(
        "Given reports number: %d, usable parsed results number: %d",
        reportParseResults.size(), parseResults.size());
    if (!validateParsedReportsWithSameBuildFingerprint(parseResults)) {
      return Optional.empty();
    }

    return mergeReports(
        parseResults.stream().map(pr -> pr.report().get()).collect(toImmutableList()),
        /* validateReports= */ false);
  }

  /** Gets the attributes showed in the <Result> element in the merged report. */
  private static ImmutableList<Attribute> getNewResultAttrs(List<Result> reports) {
    AtomicReference<Long> startTime = new AtomicReference<>(null);
    AtomicReference<Long> endTime = new AtomicReference<>(null);
    AtomicReference<String> startTimeDisplay = new AtomicReference<>("");
    AtomicReference<String> endTimeDisplay = new AtomicReference<>("");
    List<String> deviceSerials = new ArrayList<>();
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
              deviceSerials.add(attr.getValue().trim());
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
      List<ParseResult> parseResults) {
    if (parseResults.isEmpty()) {
      return false;
    }
    Result firstResult =
        parseResults.stream()
            .filter(
                parseResult ->
                    parseResult.report().get().hasBuild()
                        && !parseResult.report().get().getBuild().getBuildFingerprint().isEmpty())
            .map(parseResult -> parseResult.report().get())
            .findFirst()
            .orElse(null);
    if (firstResult == null) {
      logger.atInfo().log("Not found any report with info about device build_fingerprint");
      return false;
    }
    String baseBuildFingerprint = firstResult.getBuild().getBuildFingerprint();
    boolean reportsAreValid = true;
    for (ParseResult parseResult : parseResults) {
      if (!parseResult
          .report()
          .get()
          .getBuild()
          .getBuildFingerprint()
          .equals(baseBuildFingerprint)) {
        logger.atInfo().log(
            "The report generated based on [%s] doesn't have build_fingerprint [%s]:\n%s",
            parseResult.originalReportFile(),
            baseBuildFingerprint,
            parseResult.report().get().getBuild());
        reportsAreValid = false;
      }
    }
    return reportsAreValid;
  }

  // Ensures all reports have same device build fingerprint so the merge will be valid.
  private static boolean validateReportsWithSameBuildFingerprint(List<Result> reports) {
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
      logger.atInfo().log("Not found any report with info about device build_fingerprint");
      return false;
    }
    String baseBuildFingerprint = firstReport.getBuild().getBuildFingerprint();
    boolean reportsAreValid = true;
    for (Result report : reports) {
      if (!report.getBuild().getBuildFingerprint().equals(baseBuildFingerprint)) {
        logger.atInfo().log(
            "Expected build fingerprint [%s] but found a report with different build fingerprint:\n"
                + "%s",
            baseBuildFingerprint, report.getBuild());
        reportsAreValid = false;
      }
    }
    return reportsAreValid;
  }

  /** Parses multiple XML reports syncly. */
  @VisibleForTesting
  List<ParseResult> parseXmlReports(List<Path> reportXmlFiles)
      throws MobileHarnessException, InterruptedException {
    try {
      return parseXmlReportsAsync(reportXmlFiles).get(PARSE_TIMEOUT_IN_HOUR, HOURS);
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
  private ListenableFuture<List<ParseResult>> parseXmlReportsAsync(List<Path> reportXmlFiles) {
    List<ListenableFuture<ParseResult>> parseReportFutures = new ArrayList<>();
    logger.atInfo().log(
        "Start to parse report xml files:\n - %s",
        reportXmlFiles.stream().map(Path::toString).collect(joining(",\n - ")));
    for (Path reportXmlFile : reportXmlFiles) {
      parseReportFutures.add(parseXmlReportAsync(reportXmlFile));
    }
    return Futures.allAsList(parseReportFutures);
  }

  /** Parses one XML report asyncly. */
  private ListenableFuture<ParseResult> parseXmlReportAsync(Path reportXmlFile) {
    return threadPool.submit(() -> parseXmlReport(reportXmlFile));
  }

  private ParseResult parseXmlReport(Path reportXmlFile) throws MobileHarnessException {
    return ParseResult.of(reportXmlFile, reportParser.parse(reportXmlFile));
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
            .map(mr -> mr.moblySummaryFile().toString())
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
        moblyReportInfo.moblySummaryFile(),
        moblyReportParser.parseMoblyTestResult(moblyReportInfo));
  }

  /** Data class for the xTS report parse result. */
  @AutoValue
  public abstract static class ParseResult {

    /** Creates a {@link ParseResult}. */
    public static ParseResult of(Path originalReportFile, Optional<Result> report) {
      return new com.google.devtools.mobileharness.infra.ats.console.result.report
          .AutoValue_CompatibilityReportMerger_ParseResult(originalReportFile, report);
    }

    /** The original report file being parsed. */
    public abstract Path originalReportFile();

    /** The parsed report. */
    public abstract Optional<Result> report();
  }
}
