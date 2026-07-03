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

package com.google.devtools.mobileharness.shared.util.junit.xmlwriter;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.StackTrace;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCaseReference;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestSuiteOverview;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MergedXmlWriterTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final TestSuiteOverview TEST_SUITE_OVERVIEW_1 =
      TestSuiteOverview.builder()
          .setName("TEST_SUITE")
          .setElapsedTime(Duration.ofSeconds(1))
          .build();

  private static final TestSuiteOverview TEST_SUITE_OVERVIEW_2 =
      TestSuiteOverview.builder()
          .setName("TEST_SUITE_2")
          .setElapsedTime(Duration.ofMillis(1050))
          .build();

  private static final TestCase TEST_CASE_1 =
      TestCase.builder()
          .setTestCaseReference(
              TestCaseReference.builder()
                  .setName("TEST_CASE_1")
                  .setClassName("CLASS_1")
                  .setTestSuiteName("TEST_SUITE")
                  .build())
          .setStatus(TestStatus.FAILED)
          .addStackTraces(StackTrace.builder().setException("Exception").build())
          .setElapsedTime(Duration.ofMillis(1234))
          .build();

  private static final TestCase TEST_CASE_2 =
      TestCase.builder()
          .setTestCaseReference(
              TestCaseReference.builder()
                  .setName("TEST_CASE_2")
                  .setClassName("CLASS_2")
                  .setTestSuiteName("TEST_SUITE")
                  .build())
          .setStatus(TestStatus.PASSED)
          .setElapsedTime(Duration.ofMillis(4321))
          .build();

  private static final TestCase TEST_CASE_3 =
      TestCase.builder()
          .setTestCaseReference(
              TestCaseReference.builder()
                  .setName("TEST_CASE_3")
                  .setClassName("CLASS_3")
                  .setTestSuiteName("TEST_SUITE_2")
                  .build())
          .setStatus(TestStatus.FLAKY)
          .addStackTraces(StackTrace.builder().setException("Exception").build())
          .setElapsedTime(Duration.ofMillis(3333))
          .build();

  private static final TestCase TEST_CASE_4 =
      TestCase.builder()
          .setTestCaseReference(
              TestCaseReference.builder()
                  .setName("TEST_CASE_4")
                  .setClassName("CLASS_4")
                  .setTestSuiteName("TEST_SUITE_2")
                  .build())
          .setStatus(TestStatus.SKIPPED)
          .setElapsedTime(Duration.ofMillis(4444))
          .build();

  private static final ImmutableList<TestSuiteOverview> TEST_SUITE_OVERVIEWS_LIST =
      ImmutableList.of(TEST_SUITE_OVERVIEW_1, TEST_SUITE_OVERVIEW_2);

  private static final ImmutableList<TestCase> TEST_CASES_OF_TEST_SUITE_1 =
      ImmutableList.of(TEST_CASE_1, TEST_CASE_2);

  /** Tests Android XML output, which contains only one test suite. */
  @Test
  public void generateDocument_androidXmlOutput() throws IOException {
    MergedXmlWriter xmlWriter = new MergedXmlWriter();
    Path result = tempFolder.newFolder().toPath().resolve("merged-result.xml");
    xmlWriter.generateDocument(asList(TEST_SUITE_OVERVIEW_1), TEST_CASES_OF_TEST_SUITE_1, result);
    String expectedOutput =
        """
        <?xml version='1.0' encoding='UTF-8' ?>
        <testsuite name="TEST_SUITE" tests="2" failures="1" flakes="0" errors="0" skipped="0" \
        time="1.0" hostname="localhost">
          <testcase name="TEST_CASE_1" classname="CLASS_1" time="1.234">
            <failure>Exception</failure>
          </testcase>
          <testcase name="TEST_CASE_2" classname="CLASS_2" time="4.321" />
        </testsuite>
        """;

    String xmlOutput = getXmlOutput(result);
    assertThat(xmlOutput).isEqualTo(cleanXml(expectedOutput));
  }

  /** Tests iOS XML output, which contains multiple test suites. */
  @Test
  public void generateDocument_iosXmlOutput() throws IOException {
    MergedXmlWriter xmlWriter = new MergedXmlWriter();
    Path result = tempFolder.newFolder().toPath().resolve("merged-result.xml");
    xmlWriter.generateDocument(
        TEST_SUITE_OVERVIEWS_LIST,
        asList(TEST_CASE_1, TEST_CASE_2, TEST_CASE_3, TEST_CASE_4),
        result);

    String xmlOutput = getXmlOutput(result);

    String expectedOutput =
        """
        <?xml version='1.0' encoding='UTF-8' ?>
        <testsuites>
          <testsuite name="TEST_SUITE" tests="2" failures="1" flakes="0" errors="0" skipped="0" \
        time="1.0" hostname="localhost">
            <testcase name="TEST_CASE_1" classname="CLASS_1" time="1.234">
              <failure>Exception</failure>
            </testcase>
            <testcase name="TEST_CASE_2" classname="CLASS_2" time="4.321" />
          </testsuite>
          <testsuite name="TEST_SUITE_2" tests="2" failures="0" flakes="1" errors="0" skipped="1" \
        time="1.05" hostname="localhost">
            <testcase name="TEST_CASE_3" classname="CLASS_3" time="3.333" flaky="true">
              <failure>Exception</failure>
            </testcase>
            <testcase name="TEST_CASE_4" classname="CLASS_4" time="4.444">
              <skipped />
            </testcase>
          </testsuite>
        </testsuites>
        """;

    assertThat(xmlOutput).isEqualTo(cleanXml(expectedOutput));
  }

  @Test
  public void generateDocument_noTestSuiteOverview_skips() throws IOException {
    MergedXmlWriter xmlWriter = new MergedXmlWriter();
    Path result = tempFolder.newFolder().toPath().resolve("merged-result.xml");

    xmlWriter.generateDocument(ImmutableList.of(), ImmutableList.of(), result);

    assertThat(Files.exists(result)).isFalse();
  }

  /** Gets the output produced, stripping it of extraneous whitespace characters. */
  private static String getXmlOutput(Path result) throws IOException {
    return cleanXml(Files.readString(result));
  }

  private static String cleanXml(String content) {
    return content
        .replaceAll("[\\r\\n\\t]", "")
        .replaceAll(">\\s+<", "><")
        .replaceAll("\\s+", " ")
        .trim();
  }
}
