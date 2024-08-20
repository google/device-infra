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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.Math.max;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblySummaryEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyTestEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlDocEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.AttributeList;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.BuildInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ModuleRunResult;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Parser to parse Mobly test result to a report.
 *
 * <p>The generated report can be merged with reports generated by Tradefed and being represented as
 * one report to the users.
 */
public class MoblyReportParser {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;
  private final MoblyYamlParser moblyYamlParser;

  @Inject
  MoblyReportParser(LocalFileUtil localFileUtil, MoblyYamlParser moblyYamlParser) {
    this.localFileUtil = localFileUtil;
    this.moblyYamlParser = moblyYamlParser;
  }

  /**
   * Parses one Mobly test result and generates a report.
   *
   * @param moblyReportInfo information for the Mobly test result which will be used to generate a
   *     report
   */
  public Optional<Result> parseMoblyTestResult(MoblyReportInfo moblyReportInfo)
      throws MobileHarnessException {
    AttributeList resultAttributesList = AttributeList.getDefaultInstance();
    if (localFileUtil.isFileExist(moblyReportInfo.resultAttributesFile())) {
      try {
        resultAttributesList =
            TextFormat.parse(
                localFileUtil.readFile(moblyReportInfo.resultAttributesFile()),
                AttributeList.class);
      } catch (MobileHarnessException | ParseException e) {
        logger.atWarning().log(
            "Failed to parse result attributes file [%s]: %s",
            moblyReportInfo.resultAttributesFile(), MoreThrowables.shortDebugString(e));
      }
    }

    AttributeList buildAttributesList = AttributeList.getDefaultInstance();
    if (localFileUtil.isFileExist(moblyReportInfo.buildAttributesFile())) {
      try {
        buildAttributesList =
            TextFormat.parse(
                localFileUtil.readFile(moblyReportInfo.buildAttributesFile()), AttributeList.class);
      } catch (MobileHarnessException | ParseException e) {
        logger.atWarning().log(
            "Failed to parse build attributes file [%s]: %s",
            moblyReportInfo.buildAttributesFile(), MoreThrowables.shortDebugString(e));
      }
    }

    ModuleRunResult moduleRunResult = null;
    if (localFileUtil.isFileExist(moblyReportInfo.moduleResultFile())) {
      try {
        moduleRunResult =
            TextFormat.parse(
                localFileUtil.readFile(moblyReportInfo.moduleResultFile()), ModuleRunResult.class);
      } catch (MobileHarnessException | ParseException e) {
        logger.atWarning().log(
            "Failed to parse ats module result file [%s]: %s",
            moblyReportInfo.moduleResultFile(), MoreThrowables.shortDebugString(e));
      }
    }

    BuildInfo.Builder buildInfoBuilder =
        BuildInfo.newBuilder().addAllAttribute(buildAttributesList.getAttributeList());
    if (moblyReportInfo.deviceBuildFingerprint() != null) {
      buildInfoBuilder.setBuildFingerprint(moblyReportInfo.deviceBuildFingerprint());
    }

    Result.Builder resultBuilder =
        Result.newBuilder()
            .addAllAttribute(resultAttributesList.getAttributeList())
            .setBuild(buildInfoBuilder);

    String moduleName =
        moblyReportInfo.moblyPackageName()
            + (!isNullOrEmpty(moblyReportInfo.moduleParameter())
                ? String.format("[%s]", moblyReportInfo.moduleParameter())
                : "");
    Module.Builder moduleBuilder = Module.newBuilder().setName(moduleName).setIsNonTfModule(true);
    if (moblyReportInfo.moduleAbi() != null) {
      moduleBuilder.setAbi(moblyReportInfo.moduleAbi());
    }

    ImmutableList<MoblyYamlDocEntry> moblyDocEntries = ImmutableList.of();
    if (moblyReportInfo.moblySummaryFile() != null) {
      try {
        moblyDocEntries = moblyYamlParser.parse(moblyReportInfo.moblySummaryFile().toString());
      } catch (MobileHarnessException | IOException e) {
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_REPORT_PARSER_PARSE_SUMMARY_FILE_ERROR,
            String.format(
                "Failed to parse Mobly test summary file %s", moblyReportInfo.moblySummaryFile()),
            e);
      }

      long runtime = 0L;
      // Test class to list of test methods in that test class
      ImmutableMultimap.Builder<String, MoblyTestEntry> testEntriesMapBuilder =
          ImmutableMultimap.builder();
      int failedTestsInModule = 0;
      for (MoblyYamlDocEntry moblyDocEntry : moblyDocEntries) {
        if (moblyDocEntry instanceof MoblyTestEntry) {
          MoblyTestEntry testEntry = (MoblyTestEntry) moblyDocEntry;
          runtime +=
              max(testEntry.getEndTime().orElse(0L) - testEntry.getBeginTime().orElse(0L), 0L);
          testEntriesMapBuilder.put(testEntry.getTestClass(), testEntry);
        } else if (moblyDocEntry instanceof MoblySummaryEntry) {
          MoblySummaryEntry summaryEntry = (MoblySummaryEntry) moblyDocEntry;
          moduleBuilder
              .setDone(true)
              .setTotalTests(summaryEntry.requested())
              .setPassed(summaryEntry.passed());
          failedTestsInModule = summaryEntry.failed() + summaryEntry.error();
          moduleBuilder.setFailedTests(failedTestsInModule);
        } else {
          // Do not handle other MoblyYamlDocEntry at this moment.
        }
      }
      Module module =
          moduleBuilder
              .setRuntimeMillis(runtime)
              .addAllTestCase(getTestCases(testEntriesMapBuilder.build()))
              .build();
      resultBuilder
          .setSummary(
              Summary.newBuilder()
                  .setPassed(module.getPassed())
                  .setFailed(failedTestsInModule)
                  .setModulesDone(module.getDone() ? 1 : 0)
                  .setModulesTotal(1))
          .addModuleInfo(module);
    } else if (moduleRunResult != null) {
      if (moduleRunResult.getResult().equals(TestResult.ERROR)
          || moduleRunResult.getResult().equals(TestResult.FAIL)) {
        Module module =
            moduleBuilder.setReason(Reason.newBuilder().setMsg(moduleRunResult.getCause())).build();
        resultBuilder
            .setSummary(
                Summary.newBuilder().setPassed(0).setFailed(0).setModulesDone(0).setModulesTotal(1))
            .addModuleInfo(module);
      } else if (!moduleRunResult.getResult().equals(TestResult.SKIP)) {
        // Do nothing on SKIP state and print warning for unrecognized states.
        logger.atWarning().log(
            "Unrecognized module state: %s. Cause: %s.",
            moduleRunResult.getResult(), moduleRunResult.getCause());
      }
    }

    return Optional.of(resultBuilder.build());
  }

  /**
   * Generates a list of {@link TestCase} where each one represents one test class, and all of them
   * belong to the same module.
   */
  private static ImmutableList<TestCase> getTestCases(
      ImmutableMultimap<String, MoblyTestEntry> testEntries) {
    ImmutableList.Builder<TestCase> testCases = ImmutableList.builder();
    for (Map.Entry<String, Collection<MoblyTestEntry>> entry : testEntries.asMap().entrySet()) {
      TestCase.Builder testCaseBuilder = TestCase.newBuilder().setName(entry.getKey());
      ImmutableList.Builder<Test> testListBuilder = ImmutableList.builder();
      for (MoblyTestEntry testEntry : entry.getValue()) {
        Test.Builder testBuilder =
            Test.newBuilder()
                .setName(testEntry.getTestName())
                .setResult(getTestStatus(testEntry.getResult()));
        if (testEntry.getResult() == MoblyResult.SKIP) {
          testBuilder.setSkipped(true);
        }
        if (testEntry.getStacktrace().isPresent()) {
          String stackTrace = testEntry.getStacktrace().get();
          testBuilder.setFailure(
              TestFailure.newBuilder()
                  // Use the first line of stack trace as error message
                  .setMsg(Iterables.get(Splitter.on('\n').split(stackTrace), 0))
                  .setStackTrace(StackTrace.newBuilder().setContent(stackTrace)));
        }
        testListBuilder.add(testBuilder.build());
      }
      testCaseBuilder.addAllTest(testListBuilder.build());
      testCases.add(testCaseBuilder.build());
    }
    return testCases.build();
  }

  private static String getTestStatus(MoblyResult moblyResult) {
    return TestStatus.convertMoblyResultToTestStatusCompatibilityString(moblyResult);
  }

  /** Data class for the xTS report parse result. */
  @AutoValue
  public abstract static class MoblyReportInfo {

    /** Creates a {@link ParseResult}. */
    public static MoblyReportInfo of(
        String moblyPackageName,
        @Nullable String moduleAbi,
        @Nullable String moduleParameter,
        @Nullable Path moblySummaryFile,
        Path resultAttributesFile,
        @Nullable String deviceBuildFingerprint,
        Path buildAttributesFile,
        Path moduleResultFile) {
      return new AutoValue_MoblyReportParser_MoblyReportInfo(
          moblyPackageName,
          moduleAbi,
          moduleParameter,
          moblySummaryFile,
          resultAttributesFile,
          deviceBuildFingerprint,
          buildAttributesFile,
          moduleResultFile);
    }

    /** The name of Mobly package to which the {@code moblySummaryFile} belongs to. */
    public abstract String moblyPackageName();

    /** The abi of the Mobly module. */
    @Nullable
    public abstract String moduleAbi();

    /** The parameter of the Mobly module. */
    @Nullable
    public abstract String moduleParameter();

    /**
     * The path of the Mobly test summary file being parsed. It could be empty in some cases like
     * the test is skipped.
     */
    @Nullable
    public abstract Path moblySummaryFile();

    /**
     * The path of the text proto file that stores {@link AttributeList} which will be set in the
     * {@link Result}.{@code attribute}.
     */
    public abstract Path resultAttributesFile();

    /**
     * The build fingerprint for the major device on which the test run, it's used to identify the
     * generated report.
     */
    @Nullable
    public abstract String deviceBuildFingerprint();

    /**
     * The path of the text proto file that stores {@link AttributeList} which will be set in the
     * {@link BuildInfo}.{@code attribute}.
     */
    public abstract Path buildAttributesFile();

    /**
     * The path of the text proto file that stores {@link
     * com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ModuleRunResult}
     * which is used as a backup result from ATS if the Mobly result file wasn't created.
     */
    public abstract Path moduleResultFile();
  }
}
