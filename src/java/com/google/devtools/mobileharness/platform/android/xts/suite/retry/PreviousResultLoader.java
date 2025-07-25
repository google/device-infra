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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.console.command.parser.CommandLineParser;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportParser;
import com.google.devtools.mobileharness.infra.ats.console.result.report.TestResultProtoUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultListerHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Class to load xTS previous results. */
public class PreviousResultLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TEST_RECORD_PROTO_DIR_NAME = "proto";

  private final LocalFileUtil localFileUtil;
  private final ResultListerHelper resultListerHelper;
  private final CompatibilityReportParser compatibilityReportParser;

  @Inject
  PreviousResultLoader(
      LocalFileUtil localFileUtil,
      ResultListerHelper resultListerHelper,
      CompatibilityReportParser compatibilityReportParser) {
    this.localFileUtil = localFileUtil;
    this.resultListerHelper = resultListerHelper;
    this.compatibilityReportParser = compatibilityReportParser;
  }

  /**
   * Loads the result for the given session from ATS Console.
   *
   * <p>Either {@code previousSessionIndex} or {@code previousSessionResultDirName} must be
   * provided.
   *
   * @param resultsDir path to the "results" directory
   * @param previousSessionIndex index of the previous session being loaded
   * @param previousSessionResultDirName name of the previous session result dir
   */
  public Result loadPreviousResult(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    Preconditions.checkState(previousSessionIndex != null || previousSessionResultDirName != null);
    Path testResultProtoFile =
        getPrevSessionTestResultProtoFile(
            resultsDir, previousSessionIndex, previousSessionResultDirName);
    if (localFileUtil.isFileExist(testResultProtoFile)) {
      return loadResult(
          testResultProtoFile,
          previousSessionIndex != null
              ? String.valueOf(previousSessionIndex)
              : previousSessionResultDirName);
    } else {
      // TODO: Remove the legacy result support.
      Optional<Result> result =
          getPrevLegacySessionTestResult(
              resultsDir, previousSessionIndex, previousSessionResultDirName);
      if (result.isPresent()) {
        logger.atInfo().log(
            "Will retry legacy session result for session %s",
            previousSessionIndex != null ? previousSessionIndex : previousSessionResultDirName);
        return result.get();
      } else {
        throw new MobileHarnessException(
            ExtErrorId.PREV_RESULT_LOADER_MISSING_TEST_RESULT_PROTO_FILE_IN_SESSION,
            String.format(
                "The test result proto file %s does not exist for session %s.",
                testResultProtoFile.toAbsolutePath(),
                previousSessionIndex != null
                    ? previousSessionIndex
                    : previousSessionResultDirName));
      }
    }
  }

  /**
   * Loads the result for the given session from ATS Server.
   *
   * @param resultDir path to the session's result PB file.
   * @param previousSessionId ID of the previous session being loaded
   */
  public Result loadPreviousResult(Path resultDir, String previousSessionId)
      throws MobileHarnessException {
    Path testResultProtoFile = resultDir.resolve(SuiteCommon.TEST_RESULT_PB_FILE_NAME);
    return loadResult(testResultProtoFile, previousSessionId);
  }

  private Result loadResult(Path testResultProtoFile, String previousSessionIdOrIndex)
      throws MobileHarnessException {
    try {
      return TestResultProtoUtil.readFromFile(testResultProtoFile.toFile());
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_LOAD_TEST_RESULT_PROTO_FILE_ERROR,
          String.format(
              "Failed to load test result proto file %s for session %s.",
              testResultProtoFile.toAbsolutePath(), previousSessionIdOrIndex),
          e);
    }
  }

  private Path getPrevSessionTestResultProtoFile(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    return getPrevSessionResultDir(resultsDir, previousSessionIndex, previousSessionResultDirName)
        .resolve(SuiteCommon.TEST_RESULT_PB_FILE_NAME);
  }

  /**
   * Gets the result dir for the given session from ATS Console.
   *
   * <p>Either {@code previousSessionIndex} or {@code previousSessionResultDirName} must be
   * provided.
   *
   * @param resultsDir path to the "results" directory
   * @param previousSessionIndex index of the previous session being loaded
   * @param previousSessionResultDirName name of the previous session result dir
   */
  public Path getPrevSessionResultDir(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    Preconditions.checkState(previousSessionIndex != null || previousSessionResultDirName != null);
    ImmutableList<File> allResultDirs =
        getAllResultDirs(resultsDir, previousSessionIndex, previousSessionResultDirName);
    if (previousSessionIndex != null) {
      return allResultDirs.get(previousSessionIndex).toPath();
    }
    return allResultDirs.stream()
        .filter(resultDir -> resultDir.getName().equals(previousSessionResultDirName))
        .findFirst()
        .get()
        .toPath();
  }

  /** Try to find the result with the legacy path. */
  private Optional<Result> getPrevLegacySessionTestResult(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    Preconditions.checkState(previousSessionIndex != null || previousSessionResultDirName != null);
    Path prevSessionResultDir =
        getPrevSessionResultDir(resultsDir, previousSessionIndex, previousSessionResultDirName);
    List<File> testResultXmlFiles =
        localFileUtil.listFiles(
            prevSessionResultDir.toAbsolutePath().toString(),
            /* recursively= */ true,
            p -> p.getName().equals(SuiteCommon.TEST_RESULT_XML_FILE_NAME));
    if (testResultXmlFiles.size() != 1) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_LOAD_LEGACY_TEST_RESULT_XML_FILE_ERROR,
          String.format(
              "Failed to load legacy test result XML file under %s for session %s.",
              resultsDir.toAbsolutePath(), previousSessionIndex));
    }
    Optional<Result> result =
        compatibilityReportParser.parse(testResultXmlFiles.get(0).toPath(), /* shallow= */ false);
    return Optional.ofNullable(result.isPresent() ? injectArgsFromCommandLine(result.get()) : null);
  }

  /** Injects missing args like modules and filters from command line to the result. */
  private Result injectArgsFromCommandLine(Result result) throws MobileHarnessException {
    Optional<Attribute> commandLineArgs =
        result.getAttributeList().stream()
            .filter(attr -> attr.getKey().equals("command_line_args"))
            .findFirst();
    String commandLineArgsStr = commandLineArgs.isPresent() ? commandLineArgs.get().getValue() : "";
    if (commandLineArgsStr.isEmpty()) {
      return result;
    }

    // Generates the SessionRequestInfo from the command line args. Inserted empty values for
    // xts_root_dir, xts_type and command_line_args to avoid error.
    CommandLineParser parser = CommandLineParser.getInstance();
    SessionRequestInfo info =
        parser
            .parseCommandLine(commandLineArgsStr)
            .setCommandLineArgs("")
            .setXtsRootDir("")
            .setXtsType("")
            .build();
    Result.Builder resultBuilder = result.toBuilder();
    if (info.testName().isPresent()) {
      resultBuilder.setTestFilter(info.testName().get());
    }
    if (!info.moduleNames().isEmpty()) {
      resultBuilder.addAllModuleFilter(info.moduleNames());
    }
    if (!info.includeFilters().isEmpty()) {
      resultBuilder.addAllIncludeFilter(info.includeFilters());
    }
    if (!info.excludeFilters().isEmpty()) {
      resultBuilder.addAllExcludeFilter(info.excludeFilters());
    }
    return resultBuilder.build();
  }

  public Optional<TradefedResultFilesBundle> getPrevSessionResultFilesBundle(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    Preconditions.checkState(previousSessionIndex != null || previousSessionResultDirName != null);
    Path prevSessionResultDir =
        getPrevSessionResultDir(resultsDir, previousSessionIndex, previousSessionResultDirName);
    Path testResultXmlFile = prevSessionResultDir.resolve(SuiteCommon.TEST_RESULT_XML_FILE_NAME);
    if (!localFileUtil.isFileExist(testResultXmlFile)) {
      return Optional.empty();
    }
    Path testRecordProtoDir = prevSessionResultDir.resolve("proto");
    if (!localFileUtil.isDirExist(testRecordProtoDir)) {
      return Optional.empty();
    }
    ImmutableList<Path> testRecordProtoFiles =
        ImmutableList.sortedCopyOf(
            localFileUtil.listFilesOrDirs(testRecordProtoDir, p -> p.toFile().isFile()));
    if (testRecordProtoFiles.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(TradefedResultFilesBundle.of(testResultXmlFile, testRecordProtoFiles));
  }

  /**
   * Loads the test report properties for the given session from ATS Console.
   *
   * <p>Either {@code previousSessionIndex} or {@code previousSessionResultDirName} must be
   * provided.
   *
   * @param resultsDir path to the "results" directory
   * @param previousSessionIndex index of the previous session being loaded
   * @param previousSessionResultDirName name of the previous session result dir
   */
  public Optional<Path> getPrevSessionTestReportProperties(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    Preconditions.checkState(previousSessionIndex != null || previousSessionResultDirName != null);
    Path prevSessionResultDir =
        getPrevSessionResultDir(resultsDir, previousSessionIndex, previousSessionResultDirName);
    Optional<Path> testReportPropertiesFile =
        Optional.of(prevSessionResultDir.resolve(SuiteCommon.TEST_REPORT_PROPERTIES_FILE_NAME));
    if (!localFileUtil.isFileExist(testReportPropertiesFile.get())) {
      testReportPropertiesFile = Optional.empty();
    }
    return testReportPropertiesFile;
  }

  /**
   * Loads the test report properties for the given session from ATS Server.
   *
   * @param resultDir parent dir path to the session's test report properties file
   */
  public Optional<Path> getPrevSessionTestReportProperties(Path resultDir)
      throws MobileHarnessException {
    Path testReportPropertiesFile = resultDir.resolve(SuiteCommon.TEST_REPORT_PROPERTIES_FILE_NAME);
    if (!localFileUtil.isFileExist(testReportPropertiesFile)) {
      return Optional.empty();
    }
    return Optional.of(testReportPropertiesFile);
  }

  /**
   * Loads the test record proto files for the given session from ATS Console.
   *
   * <p>Either {@code previousSessionIndex} or {@code previousSessionResultDirName} must be
   * provided.
   *
   * @param resultsDir path to the "results" directory
   * @param previousSessionIndex index of the previous session being loaded
   * @param previousSessionResultDirName name of the previous session result dir
   */
  public ImmutableList<Path> getPrevSessionTestRecordProtoFiles(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    Preconditions.checkState(previousSessionIndex != null || previousSessionResultDirName != null);
    Path prevSessionResultDir =
        getPrevSessionResultDir(resultsDir, previousSessionIndex, previousSessionResultDirName);
    Path testRecordProtoDir = prevSessionResultDir.resolve(TEST_RECORD_PROTO_DIR_NAME);
    if (!localFileUtil.isDirExist(testRecordProtoDir)) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(
        localFileUtil.listFilesOrDirs(testRecordProtoDir, p -> p.toFile().isFile()));
  }

  /**
   * Loads the test record proto files for the given session from ATS Server.
   *
   * @param resultDir parent dir path to the session's test record proto directory
   */
  public ImmutableList<Path> getPrevSessionTestRecordProtoFiles(Path resultDir)
      throws MobileHarnessException {
    Path testRecordProtoDir = resultDir.resolve(TEST_RECORD_PROTO_DIR_NAME);
    if (!localFileUtil.isDirExist(testRecordProtoDir)) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(
        localFileUtil.listFilesOrDirs(testRecordProtoDir, p -> p.toFile().isFile()));
  }

  private ImmutableList<File> getAllResultDirs(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    ImmutableList<File> allResultDirs =
        resultListerHelper.listResultDirsInOrder(resultsDir.toAbsolutePath().toString());
    if (allResultDirs.isEmpty()) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_NO_PREVIOUS_SESSION_FOUND, "No previous session found.");
    }
    if (previousSessionIndex != null
        && (previousSessionIndex < 0 || previousSessionIndex >= allResultDirs.size())) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_SESSION_INDEX_OUT_OF_RANGE,
          String.format(
              "The given previous session index %s is out of index. The session index range is [%d,"
                  + " %d]",
              previousSessionIndex, 0, allResultDirs.size() - 1));
    }
    if (previousSessionResultDirName != null
        && allResultDirs.stream()
            .noneMatch(resultDir -> resultDir.getName().equals(previousSessionResultDirName))) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_SESSION_RESULT_DIR_NOT_FOUND,
          String.format(
              "The given previous session result dir [%s] is not found in the result dir [%s].",
              previousSessionResultDirName, resultsDir));
    }

    return allResultDirs;
  }

  /**
   * A bundle of a Tradefed result including the test_result.xml file and the test-record proto
   * files.
   */
  @AutoValue
  public abstract static class TradefedResultFilesBundle {

    /** Creates a {@link TradefedResultFilesBundle}. */
    public static TradefedResultFilesBundle of(
        Path testResultXml, ImmutableList<Path> testRecordProtoFiles) {
      return new AutoValue_PreviousResultLoader_TradefedResultFilesBundle(
          testResultXml, testRecordProtoFiles);
    }

    /** Path of the test_result.xml file. */
    public abstract Path testResultXml();

    /** Paths of the test-record proto files. */
    public abstract ImmutableList<Path> testRecordProtoFiles();
  }
}
