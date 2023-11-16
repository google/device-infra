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

package com.google.devtools.atsconsole.result.checksum;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompatibilityReportChecksumHelperTest {

  private static final String BUILD_FINGERPRINT = "deviceBuildFingerprint";

  private static final Module MODULE1 =
      Module.newBuilder()
          .setName("Module1")
          .setAbi("arm64-v8a")
          .setTotalTests(4)
          .addTestCase(
              TestCase.newBuilder()
                  .setName("android.cts.Dummy1Test")
                  .addTest(
                      com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                          .Test.newBuilder()
                          .setResult("fail")
                          .setName("testMethod1")
                          .setFailure(
                              TestFailure.newBuilder()
                                  .setStackTrace(
                                      StackTrace.newBuilder()
                                          .setContent("Test error stack trace."))))
                  .addTest(
                      com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                          .Test.newBuilder()
                          .setResult("pass")
                          .setName("testMethod2")))
          .addTestCase(
              TestCase.newBuilder()
                  .setName("android.cts.Dummy2Test")
                  .addTest(
                      com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                          .Test.newBuilder()
                          .setResult("pass")
                          .setName("testMethod1"))
                  .addTest(
                      com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                          .Test.newBuilder()
                          .setResult("pass")
                          .setName("testMethod2")))
          .build();

  private static final Module MODULE2 =
      Module.newBuilder()
          .setName("Module2")
          .setAbi("arm64-v8a")
          .setTotalTests(2)
          .addTestCase(
              TestCase.newBuilder()
                  .setName("android.cts.Hello1Test")
                  .addTest(
                      com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                          .Test.newBuilder()
                          .setResult("pass")
                          .setName("testMethod1")))
          .addTestCase(
              TestCase.newBuilder()
                  .setName("android.cts.Hello2Test")
                  .addTest(
                      com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                          .Test.newBuilder()
                          .setResult("pass")
                          .setName("testMethod1")))
          .build();

  private File resultDir;
  private File fakeLogFile;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    resultDir = folder.newFolder("test_result_dir");
    fakeLogFile = folder.newFile(Paths.get(resultDir.getName(), "fake-log-file.xml").toString());
    Files.writeString(fakeLogFile.toPath(), "This is a fake log file for testing.");
  }

  @Test
  public void validateGeneratedChecksumDataFile() throws Exception {
    Result resultReport = Result.newBuilder().addModuleInfo(MODULE1).addModuleInfo(MODULE2).build();

    boolean res =
        CompatibilityReportChecksumHelper.tryCreateChecksum(
            resultDir, resultReport, BUILD_FINGERPRINT);
    assertThat(res).isTrue();

    // Try to parse the result back
    File checksumData = new File(resultDir, CompatibilityReportChecksumHelper.NAME);
    assertThat(checksumData.exists()).isTrue();

    CompatibilityReportChecksumHelper checksumHelper =
        new CompatibilityReportChecksumHelper(resultDir, BUILD_FINGERPRINT);
    assertThat(checksumHelper.containsFile(fakeLogFile, resultDir.getName())).isTrue();

    // Checks tests in MODULE1
    for (TestCase testCase : MODULE1.getTestCaseList()) {
      for (com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test test :
          testCase.getTestList()) {
        assertThat(checksumHelper.containsTestResult(MODULE1, testCase, test, BUILD_FINGERPRINT))
            .isTrue();
      }
    }

    // Checks tests in MODULE2
    for (TestCase testCase : MODULE2.getTestCaseList()) {
      for (com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test test :
          testCase.getTestList()) {
        assertThat(checksumHelper.containsTestResult(MODULE2, testCase, test, BUILD_FINGERPRINT))
            .isTrue();
      }
    }

    // Now creates another fake log file in the result dir, which is not included by the above
    // checksum data file
    File anotherFakeLogFile =
        folder.newFile(Paths.get(resultDir.getName(), "another-fake-log-file.xml").toString());
    Files.writeString(anotherFakeLogFile.toPath(), "This is an another fake log file for testing.");

    assertThat(checksumHelper.containsFile(anotherFakeLogFile, resultDir.getName())).isFalse();
  }
}
