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

package com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AntXmlResultWriter}. */
@RunWith(JUnit4.class)
public final class AntXmlResultWriterTest {

  private ByteArrayOutputStream outputStream;
  private XmlWriter xmlWriter;
  private TestResult testResultWithOneTest;

  @Before
  public void setUp() {
    outputStream = new ByteArrayOutputStream();
    xmlWriter = new XmlWriter(outputStream);

    TestSuiteNode testSuite = new TestSuiteNode("");
    testSuite.addTestCase(new TestCaseNode("TestClass", "testMethod"));
    testSuite.setTimestamp(LocalDateTime.of(2021, 9, 1, 1, 30, 15));
    TestSuiteNode testSuites = new TestSuiteNode("");
    testSuites.addTestSuite(testSuite);
    testResultWithOneTest = testSuites.buildResult();
  }

  @Test
  public void writeTestSuites_includesTestSuitesRootNodeByDefault() throws Exception {
    new AntXmlResultWriter().writeTestSuites(xmlWriter, testResultWithOneTest);

    assertThat(outputStream.toString(UTF_8)).contains("<testsuites>");
  }

  @Test
  public void writeTestSuites_optionalTestSuitesRoot_doesNotWriteTestSuitesRootNode()
      throws Exception {
    new AntXmlResultWriter(true).writeTestSuites(xmlWriter, testResultWithOneTest);

    assertThat(outputStream.toString(UTF_8)).doesNotContain("<testsuites>");
  }

  @Test
  public void writeTestSuites_optionalTestSuitesRoot_hasTimestampInISOFormat() throws Exception {
    new AntXmlResultWriter(true).writeTestSuites(xmlWriter, testResultWithOneTest);

    assertThat(outputStream.toString(UTF_8)).contains("timestamp='2021-09-01T01:30:15'");
  }
}
