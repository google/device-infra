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

import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.xml.XmlEscapers;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.checksum.CompatibilityReportChecksumHelper;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.LoggedFile;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Metric;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Run;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.infra.ats.console.util.tradefed.TestRecordWriter;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/** Utility to prepare the report to upload. */
public class CompatibilityReportCreator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String NS = null;

  private static final String ENCODING = "UTF-8";

  @VisibleForTesting static final String TEST_RESULT_FILE_NAME = "test_result.xml";
  @VisibleForTesting static final String HTML_REPORT_NAME = "test_result.html";
  @VisibleForTesting static final String REPORT_XSL_FILE_NAME = "compatibility_result.xsl";
  @VisibleForTesting static final String TEST_RECORD_PROTO_FILE_NAME = "test-record.pb";

  @VisibleForTesting
  static final ImmutableList<String> RESULT_RESOURCES =
      ImmutableList.of("compatibility_result.css", REPORT_XSL_FILE_NAME, "logo.png");

  private final LocalFileUtil localFileUtil;
  private final TestRecordWriter testRecordWriter;

  @Inject
  CompatibilityReportCreator(LocalFileUtil localFileUtil, TestRecordWriter testRecordWriter) {
    this.localFileUtil = localFileUtil;
    this.testRecordWriter = testRecordWriter;
  }

  /**
   * Creates report and related files under directory {@code resultDir}, and a zip file for
   * directory {@code resultDir} and its contents later for upload.
   *
   * <p>Things done in this method:
   *
   * <ol>
   *   <li>Writes the {@code report} to "test_result.xml" file in directory {@code resultDir}.
   *   <li>Copies formatting files into directory {@code resultDir}.
   *   <li>Creates checksum data for the report and the files in directory {@code resultDir}.
   *   <li>Packages the directory {@code resultDir} and its content into a zip file. The zip file is
   *       stored as a sibling file as directory {@code resultDir}.
   *   <li>Creates a HTML report in directory {@code resultDir} based on the "test_result.xml".
   * </ol>
   *
   * @param report the result report
   * @param resultDir the directory where to store the generated report files
   * @param testRecord test record proto packed into the report if specified
   * @throws MobileHarnessException if failed to write the report to a XML file
   */
  public void createReport(Result report, Path resultDir, @Nullable TestRecord testRecord)
      throws MobileHarnessException, InterruptedException {
    try {
      writeReportToXml(report, resultDir.toFile());
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.REPORT_CREATOR_WRITE_REPORT_TO_XML_ERROR,
          String.format("Failed to write report to dir %s", resultDir),
          e);
    }
    copyFormattingFiles(resultDir.toFile());
    if (!report.getBuild().getBuildFingerprint().isEmpty()
        && !CompatibilityReportChecksumHelper.tryCreateChecksum(
            resultDir.toFile(), report, report.getBuild().getBuildFingerprint())) {
      logger.atWarning().log(
          "Failed to crete checksum for result dir [%s] and report with build fingerprint [%s]",
          resultDir, report.getBuild().getBuildFingerprint());
    }

    if (testRecord != null) {
      Path testRecordProtoDir = resultDir.resolve("proto");
      localFileUtil.prepareDir(testRecordProtoDir);
      testRecordWriter.writeTestRecordProto(
          testRecord,
          testRecordProtoDir.resolve(TEST_RECORD_PROTO_FILE_NAME),
          /* useDelimitedApi= */ true);
    } else {
      logger.atInfo().log("No test record specified.");
    }

    try {
      File zipResultFile =
          resultDir.resolveSibling(String.format("%s.zip", resultDir.getFileName())).toFile();
      ZipUtil.createZip(resultDir.toFile(), zipResultFile);
    } catch (IOException e) {
      logger.atWarning().log(
          "Failed to create zip for %s: %s",
          resultDir.getFileName(), MoreThrowables.shortDebugString(e, 0));
    }

    Optional<File> htmlReport = createHtmlReport(resultDir.resolve(TEST_RESULT_FILE_NAME).toFile());
    if (htmlReport.isPresent()) {
      logger.atInfo().log("HTML report: %s", htmlReport.get().getAbsolutePath());
    }
  }

  /**
   * Converts the {@code report} to a compatibility test result XML, which is stored under the given
   * directory {@code parentDir} with the file name "test_result.xml".
   */
  @VisibleForTesting
  void writeReportToXml(Result report, File parentDir) throws IOException {
    File resultFile = new File(parentDir, TEST_RESULT_FILE_NAME);
    OutputStream stream = new FileOutputStream(resultFile);
    XmlSerializer serializer = null;
    try {
      serializer = XmlPullParserFactory.newInstance().newSerializer();
    } catch (XmlPullParserException e) {
      try {
        stream.close();
      } catch (IOException e2) {
        // ignored
      }
      throw new IOException(e);
    }
    serializer.setOutput(stream, ENCODING);
    serializer.startDocument(ENCODING, /* standalone= */ false);
    serializer.setFeature(
        "http://xmlpull.org/v1/doc/features.html#indent-output", /* state= */ true);
    serializer.processingInstruction(
        "xml-stylesheet type=\"text/xsl\" href=\"compatibility_result.xsl\"");

    serializer.startTag(NS, XmlConstants.RESULT_TAG);
    // Result attributes
    for (Attribute attr : report.getAttributeList()) {
      serializer.attribute(NS, attr.getKey(), attr.getValue());
    }

    // Build Info
    serializer.startTag(NS, XmlConstants.BUILD_TAG);
    for (Attribute attr : report.getBuild().getAttributeList()) {
      serializer.attribute(NS, attr.getKey(), attr.getValue());
    }
    serializer.endTag(NS, XmlConstants.BUILD_TAG);
    // Run History
    if (report.hasRunHistory()) {
      serializer.startTag(NS, XmlConstants.RUN_HISTORY_TAG);
      for (Run run : report.getRunHistory().getRunList()) {
        serializer.startTag(NS, XmlConstants.RUN_TAG);
        if (run.hasStartTimeMillis()) {
          serializer.attribute(
              NS, XmlConstants.START_TIME_ATTR, Long.toString(run.getStartTimeMillis()));
        }
        if (run.hasEndTimeMillis()) {
          serializer.attribute(
              NS, XmlConstants.END_TIME_ATTR, Long.toString(run.getEndTimeMillis()));
        }
        if (run.hasPassedTests()) {
          serializer.attribute(NS, XmlConstants.PASS_ATTR, Long.toString(run.getPassedTests()));
        }
        if (run.hasFailedTests()) {
          serializer.attribute(NS, XmlConstants.FAILED_ATTR, Long.toString(run.getFailedTests()));
        }
        if (run.hasCommandLineArgs()) {
          serializer.attribute(NS, XmlConstants.COMMAND_LINE_ARGS, run.getCommandLineArgs());
        }
        if (run.hasHostName()) {
          serializer.attribute(NS, XmlConstants.HOST_NAME_ATTR, run.getHostName());
        }
        serializer.endTag(NS, XmlConstants.RUN_TAG);
      }
      serializer.endTag(NS, XmlConstants.RUN_HISTORY_TAG);
    }

    // Summary
    serializer.startTag(NS, XmlConstants.SUMMARY_TAG);
    serializer.attribute(
        NS, XmlConstants.PASS_ATTR, Long.toString(report.getSummary().getPassed()));
    serializer.attribute(
        NS, XmlConstants.FAILED_ATTR, Long.toString(report.getSummary().getFailed()));
    serializer.attribute(
        NS, XmlConstants.MODULES_DONE_ATTR, Integer.toString(report.getSummary().getModulesDone()));
    serializer.attribute(
        NS,
        XmlConstants.MODULES_TOTAL_ATTR,
        Integer.toString(report.getSummary().getModulesTotal()));
    serializer.endTag(NS, XmlConstants.SUMMARY_TAG);

    // Modules
    for (Module module : report.getModuleInfoList()) {
      serializer.startTag(NS, XmlConstants.MODULE_TAG);
      serializer.attribute(NS, XmlConstants.NAME_ATTR, module.getName());
      if (module.hasAbi()) {
        serializer.attribute(NS, XmlConstants.ABI_ATTR, module.getAbi());
      }
      if (module.hasRuntimeMillis()) {
        serializer.attribute(
            NS, XmlConstants.RUNTIME_ATTR, Long.toString(module.getRuntimeMillis()));
      }
      if (module.hasDone()) {
        serializer.attribute(NS, XmlConstants.DONE_ATTR, Boolean.toString(module.getDone()));
      }
      if (module.hasPassed()) {
        serializer.attribute(NS, XmlConstants.PASS_ATTR, Integer.toString(module.getPassed()));
      }
      if (module.hasTotalTests()) {
        serializer.attribute(
            NS, XmlConstants.TOTAL_TESTS_ATTR, Integer.toString(module.getTotalTests()));
      }
      if (module.hasReason()) {
        serializer.startTag(NS, XmlConstants.MODULES_NOT_DONE_REASON);
        serializer.attribute(
            NS, XmlConstants.MESSAGE_ATTR, sanitizeXmlContent(module.getReason().getMsg()));
        if (!module.getReason().getErrorName().isEmpty()
            && !module.getReason().getErrorCode().isEmpty()) {
          serializer.attribute(NS, XmlConstants.ERROR_NAME_ATTR, module.getReason().getErrorName());
          serializer.attribute(NS, XmlConstants.ERROR_CODE_ATTR, module.getReason().getErrorCode());
        }
        serializer.endTag(NS, XmlConstants.MODULES_NOT_DONE_REASON);
      }
      serializeTestCases(serializer, module.getTestCaseList());
      serializer.endTag(NS, XmlConstants.MODULE_TAG);
    }

    serializer.endDocument();
  }

  private static void serializeTestCases(XmlSerializer serializer, List<TestCase> testCases)
      throws IOException {
    for (TestCase testCase : testCases) {
      serializer.startTag(NS, XmlConstants.CASE_TAG);
      serializer.attribute(NS, XmlConstants.NAME_ATTR, testCase.getName());

      for (Test test : testCase.getTestList()) {
        if (test.getResult().isEmpty()) {
          continue; // Test was not executed, don't report
        }
        serializer.startTag(NS, XmlConstants.TEST_TAG);
        serializer.attribute(NS, XmlConstants.RESULT_ATTR, test.getResult());
        serializer.attribute(NS, XmlConstants.NAME_ATTR, test.getName());
        if (test.getSkipped()) {
          serializer.attribute(NS, XmlConstants.SKIPPED_ATTR, Boolean.toString(true));
        }

        if (test.hasFailure()) {
          handleTestFailure(serializer, test.getFailure());
        }
        if (test.hasBugReport()) {
          handleLoggedFile(
              serializer,
              XmlConstants.BUGREPORT_TAG,
              test.getBugReport().getFileName(),
              test.getBugReport().getContent());
        }
        if (test.hasLogcat()) {
          handleLoggedFile(
              serializer,
              XmlConstants.LOGCAT_TAG,
              test.getLogcat().getFileName(),
              test.getLogcat().getContent());
        }
        for (LoggedFile screenshot : test.getScreenshotList()) {
          handleLoggedFile(
              serializer,
              XmlConstants.SCREENSHOT_TAG,
              screenshot.getFileName(),
              screenshot.getContent());
        }
        for (Metric metric : test.getMetricList()) {
          serializer.startTag(NS, XmlConstants.METRIC_TAG);
          serializer.attribute(NS, XmlConstants.METRIC_KEY, metric.getKey());
          serializer.text(sanitizeXmlContent(metric.getContent()));
          serializer.endTag(NS, XmlConstants.METRIC_TAG);
        }

        serializer.endTag(NS, XmlConstants.TEST_TAG);
      }

      serializer.endTag(NS, XmlConstants.CASE_TAG);
    }
  }

  private static void handleTestFailure(XmlSerializer serializer, TestFailure testFailure)
      throws IOException {
    serializer.startTag(NS, XmlConstants.FAILURE_TAG);
    serializer.attribute(NS, XmlConstants.MESSAGE_ATTR, sanitizeXmlContent(testFailure.getMsg()));
    if (!testFailure.getErrorName().isEmpty() && !testFailure.getErrorCode().isEmpty()) {
      serializer.attribute(NS, XmlConstants.ERROR_NAME_ATTR, testFailure.getErrorName());
      serializer.attribute(NS, XmlConstants.ERROR_CODE_ATTR, testFailure.getErrorCode());
    }
    if (testFailure.hasStackTrace()) {
      serializer.startTag(NS, XmlConstants.STACKTRACE_TAG);
      serializer.text(sanitizeXmlContent(testFailure.getStackTrace().getContent()));
      serializer.endTag(NS, XmlConstants.STACKTRACE_TAG);
    }
    serializer.endTag(NS, XmlConstants.FAILURE_TAG);
  }

  private static void handleLoggedFile(
      XmlSerializer serializer, String tag, String fileName, String text) throws IOException {
    if (text.isEmpty()) {
      return;
    }
    serializer.startTag(NS, tag);
    serializer.attribute(NS, XmlConstants.LOG_FILE_NAME_ATTR, fileName);
    serializer.text(text);
    serializer.endTag(NS, tag);
  }

  private static String sanitizeXmlContent(String s) {
    return XmlEscapers.xmlContentEscaper().escape(s);
  }

  /** Copies the xml formatting files stored in this jar to the {@code resultDir} directory. */
  private void copyFormattingFiles(File resultDir) {
    for (String resultFileName : RESULT_RESOURCES) {
      InputStream configStream =
          CompatibilityReportCreator.class.getResourceAsStream(
              String.format(
                  "/com/google/devtools/mobileharness/infra/ats/console/result/report/res/%s",
                  resultFileName));

      if (configStream != null) {
        File resultFile = new File(resultDir, resultFileName);
        try {
          localFileUtil.writeToFile(resultFile.getAbsolutePath(), configStream);
        } catch (MobileHarnessException e) {
          logger.atWarning().log(
              "Failed to write %s to file: %s",
              resultFileName, MoreThrowables.shortDebugString(e, 0));
        }
      } else {
        logger.atWarning().log("Failed to load %s from jar", resultFileName);
      }
    }
  }

  /** Generates test_result.html report based on the input XML file. */
  private Optional<File> createHtmlReport(File inputXml) {
    File report = new File(inputXml.getParentFile(), HTML_REPORT_NAME);
    try (InputStream xslStream =
            new FileInputStream(new File(inputXml.getParentFile(), REPORT_XSL_FILE_NAME));
        OutputStream outputStream = new FileOutputStream(report)) {
      Transformer transformer =
          TransformerFactory.newInstance().newTransformer(new StreamSource(xslStream));
      transformer.transform(new StreamSource(inputXml), new StreamResult(outputStream));
    } catch (IOException | TransformerException ignored) {
      logger.atSevere().log(
          "Failed to create %s: %s", HTML_REPORT_NAME, MoreThrowables.shortDebugString(ignored, 0));
      if (report != null) {
        report.delete();
      }
      return Optional.empty();
    }
    return Optional.of(report);
  }
}
