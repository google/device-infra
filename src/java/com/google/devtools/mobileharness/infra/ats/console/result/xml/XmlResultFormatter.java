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

package com.google.devtools.mobileharness.infra.ats.console.result.xml;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.max;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblySummaryEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyTestEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlDocEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.BuildElement;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ElementAttribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ModuleElement;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ResultElement;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.SummaryElement;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.TestCaseElement;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.TestElement;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/** Util class to convert Mobly test summary yaml files to CTS test result XML file. */
public class XmlResultFormatter {

  public static final String NS = null;
  private static final String ENCODING = "UTF-8";
  @VisibleForTesting static final String TEST_RESULT_FILE_NAME = "test_result.xml";
  @VisibleForTesting static final String TEST_RESULT_PB_FILE_NAME = "test_result.pb";

  // XML constants
  // <Result> element
  private static final String RESULT_TAG = "Result";

  // <Build> element
  private static final String BUILD_TAG = "Build";

  // <Summary> element
  private static final String SUMMARY_TAG = "Summary";
  private static final String FAILED_ATTR = "failed";
  private static final String MODULES_DONE_ATTR = "modules_done";
  private static final String MODULES_TOTAL_ATTR = "modules_total";

  // <Module> element
  private static final String MODULE_TAG = "Module";
  private static final String ABI_ATTR = "abi";
  private static final String RUNTIME_ATTR = "runtime";
  private static final String DONE_ATTR = "done";
  private static final String TOTAL_TESTS_ATTR = "total_tests";

  // <TestCase> element
  private static final String CASE_TAG = "TestCase";

  // <Test> element
  private static final String TEST_TAG = "Test";
  private static final String RESULT_ATTR = "result";
  private static final String SKIPPED_ATTR = "skipped";

  // Shared across elements
  private static final String PASS_ATTR = "pass";
  private static final String NAME_ATTR = "name";

  private final MoblyYamlParser moblyYamlParser;
  private final LocalFileUtil localFileUtil;

  public XmlResultFormatter() {
    this(new MoblyYamlParser(), new LocalFileUtil());
  }

  @VisibleForTesting
  XmlResultFormatter(MoblyYamlParser moblyYamlParser, LocalFileUtil localFileUtil) {
    this.moblyYamlParser = moblyYamlParser;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Loads and parses Mobly test summary yaml files, converts and combines them into a CTS test
   * result XML file and stores in a protobuf binary file too.
   *
   * @param resultInfo info about the Mobly test results
   * @param outputDir the directory in which the generated XML file will be saved, the xml file name
   *     will be "test_result.xml"
   */
  public void writeMoblyResults(MoblyResultInfo resultInfo, String outputDir)
      throws MobileHarnessException, IOException {
    if (resultInfo.moblyExecNameToTestSummaryYamlFile().isEmpty()) {
      return;
    }
    localFileUtil.prepareDir(outputDir);
    writeResultsToXml(parseTestSummaryYamlsToXmlResult(resultInfo), new File(outputDir));
  }

  private ResultElement parseTestSummaryYamlsToXmlResult(MoblyResultInfo resultInfo)
      throws MobileHarnessException, IOException {
    ImmutableMap<String, String> moblyExecNameToTestSummaryYamlFileMap =
        resultInfo.moblyExecNameToTestSummaryYamlFile();
    ResultElement.Builder resultElBuilder = ResultElement.newBuilder();
    int summaryPassedTests = 0;
    int summaryFailedTests = 0;

    ImmutableList.Builder<ModuleElement> modulesBuilder = ImmutableList.builder();
    for (Map.Entry<String, String> entry : moblyExecNameToTestSummaryYamlFileMap.entrySet()) {
      ModuleElement moduleEl = genModuleElement(entry.getKey(), entry.getValue());
      summaryPassedTests += moduleEl.getPassed();
      summaryFailedTests += moduleEl.getFailed();
      modulesBuilder.add(moduleEl);
    }
    ImmutableList<ModuleElement> modules = modulesBuilder.build();

    return resultElBuilder
        .addAllAttribute(
            resultInfo.xmlResultElementAttrs().entrySet().stream()
                .map(
                    entry ->
                        ElementAttribute.newBuilder()
                            .setKey(entry.getKey())
                            .setValue(entry.getValue())
                            .build())
                .collect(toImmutableList()))
        .setBuildElement(
            BuildElement.newBuilder()
                .addAllAttribute(
                    resultInfo.xmlBuildElementAttrs().entrySet().stream()
                        .map(
                            entry ->
                                ElementAttribute.newBuilder()
                                    .setKey(entry.getKey())
                                    .setValue(entry.getValue())
                                    .build())
                        .collect(toImmutableList())))
        .setSummaryElement(
            SummaryElement.newBuilder()
                .addAllAttribute(
                    ImmutableList.of(
                        ElementAttribute.newBuilder()
                            .setKey(PASS_ATTR)
                            .setValue(Integer.toString(summaryPassedTests))
                            .build(),
                        ElementAttribute.newBuilder()
                            .setKey(FAILED_ATTR)
                            .setValue(Integer.toString(summaryFailedTests))
                            .build(),
                        ElementAttribute.newBuilder()
                            .setKey(MODULES_DONE_ATTR)
                            .setValue(Integer.toString(modules.size()))
                            .build(),
                        ElementAttribute.newBuilder()
                            .setKey(MODULES_TOTAL_ATTR)
                            .setValue(Integer.toString(modules.size()))
                            .build())))
        .addAllModuleElement(modules)
        .build();
  }

  /** Generates one {@link ModuleElement} from one Mobly test_summary.yaml file. */
  private ModuleElement genModuleElement(String name, String moblyTestSummaryYamlPath)
      throws MobileHarnessException, IOException {
    ImmutableList<MoblyYamlDocEntry> moblyDocEntries =
        moblyYamlParser.parse(moblyTestSummaryYamlPath);
    ModuleElement.Builder moduleElBuilder = ModuleElement.newBuilder().setName(name);
    long runtime = 0L;
    // Test class to list of test methods in that test class
    ImmutableMultimap.Builder<String, MoblyTestEntry> testEntriesMapBuilder =
        ImmutableMultimap.builder();
    for (MoblyYamlDocEntry moblyDocEntry : moblyDocEntries) {
      if (moblyDocEntry instanceof MoblyTestEntry) {
        MoblyTestEntry testEntry = (MoblyTestEntry) moblyDocEntry;
        runtime += max(testEntry.getEndTime().orElse(0L) - testEntry.getBeginTime().orElse(0L), 0L);
        testEntriesMapBuilder.put(testEntry.getTestClass(), testEntry);
      } else if (moblyDocEntry instanceof MoblySummaryEntry) {
        MoblySummaryEntry summaryEntry = (MoblySummaryEntry) moblyDocEntry;
        moduleElBuilder.setDone(true);
        moduleElBuilder.setTotalTests(summaryEntry.requested());
        moduleElBuilder.setPassed(summaryEntry.passed());
        moduleElBuilder.setFailed(summaryEntry.failed());
      } else {
        // Do not handle other MoblyYamlDocEntry at this moment.
      }
    }
    moduleElBuilder.setRuntimeMillis(runtime);
    moduleElBuilder.addAllTestCase(genTestCaseElements(testEntriesMapBuilder.build()));
    return moduleElBuilder.build();
  }

  /**
   * Generates a list of {@link TestCaseElement} where each one represents one test class, and all
   * of them belongs to the same module.
   */
  private ImmutableList<TestCaseElement> genTestCaseElements(
      ImmutableMultimap<String, MoblyTestEntry> testEntries) {
    ImmutableList.Builder<TestCaseElement> testCases = ImmutableList.builder();
    for (Map.Entry<String, Collection<MoblyTestEntry>> entry : testEntries.asMap().entrySet()) {
      TestCaseElement.Builder testCaseElBuilder =
          TestCaseElement.newBuilder().setName(entry.getKey());
      ImmutableList.Builder<TestElement> testElementsBuilder = ImmutableList.builder();
      for (MoblyTestEntry testEntry : entry.getValue()) {
        testElementsBuilder.add(
            TestElement.newBuilder()
                .setName(testEntry.getTestName())
                .setFullName(
                    String.format("%s.%s", testEntry.getTestClass(), testEntry.getTestName()))
                .setResult(testEntry.getResult())
                .build());
      }
      testCaseElBuilder.addAllTest(testElementsBuilder.build());
      testCases.add(testCaseElBuilder.build());
    }
    return testCases.build();
  }

  private void writeResultsToXml(ResultElement resultEl, File resultDir) throws IOException {
    File resultFile = new File(resultDir, TEST_RESULT_FILE_NAME);
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

    serializer.startTag(NS, RESULT_TAG);
    // Result attributes
    for (ElementAttribute attr : resultEl.getAttributeList()) {
      serializer.attribute(NS, attr.getKey(), attr.getValue());
    }

    // Build Info
    serializer.startTag(NS, BUILD_TAG);
    for (ElementAttribute attr : resultEl.getBuildElement().getAttributeList()) {
      serializer.attribute(NS, attr.getKey(), attr.getValue());
    }
    serializer.endTag(NS, BUILD_TAG);

    // Summary
    serializer.startTag(NS, SUMMARY_TAG);
    for (ElementAttribute attr : resultEl.getSummaryElement().getAttributeList()) {
      serializer.attribute(NS, attr.getKey(), attr.getValue());
    }
    serializer.endTag(NS, SUMMARY_TAG);

    // Modules
    for (ModuleElement moduleEl : resultEl.getModuleElementList()) {
      serializer.startTag(NS, MODULE_TAG);
      serializer.attribute(NS, NAME_ATTR, moduleEl.getName());
      if (moduleEl.hasAbi()) {
        serializer.attribute(NS, ABI_ATTR, moduleEl.getAbi());
      }
      if (moduleEl.hasRuntimeMillis()) {
        serializer.attribute(NS, RUNTIME_ATTR, Long.toString(moduleEl.getRuntimeMillis()));
      }
      if (moduleEl.hasDone()) {
        serializer.attribute(NS, DONE_ATTR, Boolean.toString(moduleEl.getDone()));
      }
      if (moduleEl.hasPassed()) {
        serializer.attribute(NS, PASS_ATTR, Integer.toString(moduleEl.getPassed()));
      }
      if (moduleEl.hasTotalTests()) {
        serializer.attribute(NS, TOTAL_TESTS_ATTR, Integer.toString(moduleEl.getTotalTests()));
      }
      serializeTestCases(serializer, moduleEl.getTestCaseList());
      serializer.endTag(NS, MODULE_TAG);
    }

    serializer.endDocument();

    // Writes to test_result.pb
    Path testResultProtoPath = resultDir.toPath().resolve(TEST_RESULT_PB_FILE_NAME);
    try (FileOutputStream fos = new FileOutputStream(testResultProtoPath.toFile())) {
      resultEl.writeTo(fos);
    }
  }

  private void serializeTestCases(XmlSerializer serializer, List<TestCaseElement> testCases)
      throws IOException {
    for (TestCaseElement testCaseEl : testCases) {
      serializer.startTag(NS, CASE_TAG);
      serializer.attribute(NS, NAME_ATTR, testCaseEl.getName());

      for (TestElement testEl : testCaseEl.getTestList()) {
        serializer.startTag(NS, TEST_TAG);
        serializer.attribute(NS, RESULT_ATTR, getTestStatusString(testEl.getResult()));
        serializer.attribute(NS, NAME_ATTR, testEl.getFullName());
        if (testEl.getResult() == MoblyResult.SKIP) {
          serializer.attribute(NS, SKIPPED_ATTR, Boolean.toString(true));
        }
        serializer.endTag(NS, TEST_TAG);
      }

      serializer.endTag(NS, CASE_TAG);
    }
  }

  private String getTestStatusString(MoblyResult moblyResult) {
    return Ascii.toLowerCase(moblyResult.toString());
  }
}
