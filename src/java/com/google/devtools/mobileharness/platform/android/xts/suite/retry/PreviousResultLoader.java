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
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.console.command.parser.CommandLineParser;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.TestResultProtoUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultListerHelper;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultListerHelper.ResultBundle;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Class to load xTS previous results. */
public class PreviousResultLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;
  private final ResultListerHelper resultListerHelper;

  @Inject
  PreviousResultLoader(LocalFileUtil localFileUtil, ResultListerHelper resultListerHelper) {
    this.localFileUtil = localFileUtil;
    this.resultListerHelper = resultListerHelper;
  }

  /**
   * Loads the result for the given session from ATS Console.
   *
   * @param resultsDir path to the "results" directory
   * @param previousSessionIndex index of the previous session being loaded
   */
  public Result loadPreviousResult(Path resultsDir, int previousSessionIndex)
      throws MobileHarnessException {
    Path testResultProtoFile = getPrevSessionTestResultProtoFile(resultsDir, previousSessionIndex);
    if (localFileUtil.isFileExist(testResultProtoFile)) {
      return loadResult(testResultProtoFile, String.valueOf(previousSessionIndex));
    } else {
      // TODO: Remove the legacy result support.
      Optional<Result> result = getPrevLegacySessionTestResult(resultsDir, previousSessionIndex);
      if (result.isPresent()) {
        logger.atInfo().log(
            "Will retry legacy session result for session %s", previousSessionIndex);
        return result.get();
      } else {
        throw new MobileHarnessException(
            ExtErrorId.PREV_RESULT_LOADER_MISSING_TEST_RESULT_PROTO_FILE_IN_SESSION,
            String.format(
                "The test result proto file %s does not exist for session %s.",
                testResultProtoFile.toAbsolutePath(), previousSessionIndex));
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

  private Path getPrevSessionTestResultProtoFile(Path resultsDir, int previousSessionIndex)
      throws MobileHarnessException {
    List<File> allResultDirs = getAllResultDirs(resultsDir, previousSessionIndex);
    return allResultDirs
        .get(previousSessionIndex)
        .toPath()
        .resolve(SuiteCommon.TEST_RESULT_PB_FILE_NAME);
  }

  /** Try to find the result with the legacy path. */
  private Optional<Result> getPrevLegacySessionTestResult(Path resultsDir, int previousSessionIndex)
      throws MobileHarnessException {
    ImmutableList<ResultBundle> results =
        resultListerHelper.listResults(resultsDir.toAbsolutePath().toString());
    Result result = results.get(previousSessionIndex).result();
    return Optional.of(injectArgsFromCommandLine(result));
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
      Path resultsDir, int previousSessionIndex) throws MobileHarnessException {
    ImmutableList<File> allResultDirs = getAllResultDirs(resultsDir, previousSessionIndex);
    Path testResultXmlFile =
        allResultDirs
            .get(previousSessionIndex)
            .toPath()
            .resolve(SuiteCommon.TEST_RESULT_XML_FILE_NAME);
    if (!localFileUtil.isFileExist(testResultXmlFile)) {
      return Optional.empty();
    }
    Path testRecordProtoDir = allResultDirs.get(previousSessionIndex).toPath().resolve("proto");
    if (!localFileUtil.isDirExist(testRecordProtoDir)) {
      return Optional.empty();
    }
    List<Path> testRecordProtoFiles =
        localFileUtil.listFilesOrDirs(testRecordProtoDir, p -> p.toFile().isFile());
    if (testRecordProtoFiles.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        TradefedResultFilesBundle.of(
            testResultXmlFile, ImmutableList.copyOf(testRecordProtoFiles)));
  }

  private ImmutableList<File> getAllResultDirs(Path resultsDir, int previousSessionIndex)
      throws MobileHarnessException {
    ImmutableList<File> allResultDirs =
        resultListerHelper.listResultDirsInOrder(resultsDir.toAbsolutePath().toString());
    if (allResultDirs.isEmpty()) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_NO_PREVIOUS_SESSION_FOUND, "No previous session found.");
    }
    if (previousSessionIndex < 0 || previousSessionIndex >= allResultDirs.size()) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_SESSION_INDEX_OUT_OF_RANGE,
          String.format(
              "The given previous session index %s is out of index. The session index range is [%d,"
                  + " %d]",
              previousSessionIndex, 0, allResultDirs.size() - 1));
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
