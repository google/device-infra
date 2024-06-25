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

package com.google.devtools.mobileharness.infra.ats.console.result.checksum;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.hash.BloomFilter;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
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

    FileInputStream fileStream = new FileInputStream(checksumData);
    InputStream outputStream = new BufferedInputStream(fileStream);
    ObjectInput objectInput = new ObjectInputStream(outputStream);

    short magicNumber = objectInput.readShort();
    short version = objectInput.readShort();
    @SuppressWarnings("unchecked")
    BloomFilter<CharSequence> resultChecksum = (BloomFilter<CharSequence>) objectInput.readObject();
    @SuppressWarnings("unchecked")
    HashMap<String, byte[]> fileChecksum = (HashMap<String, byte[]>) objectInput.readObject();

    assertThat(magicNumber).isEqualTo(650);
    assertThat(version).isEqualTo(1);

    // Checks files in the result report
    assertThat(fileChecksum).containsKey(resultDir.getName() + "/" + fakeLogFile.getName());
    // Checks MODULE1
    assertThat(resultChecksum.mightContain("deviceBuildFingerprint/arm64-v8a Module1/false/1"))
        .isTrue();
    assertThat(resultChecksum.mightContain("deviceBuildFingerprint/arm64-v8a Module1/1")).isTrue();
    assertThat(
            resultChecksum.mightContain(
                "deviceBuildFingerprint/arm64-v8a"
                    + " Module1/android.cts.Dummy1Test#testMethod1/fail/Test error stack trace./"))
        .isTrue();
    assertThat(
            resultChecksum.mightContain(
                "deviceBuildFingerprint/arm64-v8a"
                    + " Module1/android.cts.Dummy1Test#testMethod2/pass//"))
        .isTrue();
    assertThat(
            resultChecksum.mightContain(
                "deviceBuildFingerprint/arm64-v8a"
                    + " Module1/android.cts.Dummy2Test#testMethod1/pass//"))
        .isTrue();
    assertThat(
            resultChecksum.mightContain(
                "deviceBuildFingerprint/arm64-v8a"
                    + " Module1/android.cts.Dummy2Test#testMethod2/pass//"))
        .isTrue();

    // Checks MODULE2
    assertThat(resultChecksum.mightContain("deviceBuildFingerprint/arm64-v8a Module2/false/0"))
        .isTrue();
    assertThat(resultChecksum.mightContain("deviceBuildFingerprint/arm64-v8a Module2/0")).isTrue();
    assertThat(
            resultChecksum.mightContain(
                "deviceBuildFingerprint/arm64-v8a"
                    + " Module2/android.cts.Hello1Test#testMethod1/pass//"))
        .isTrue();
    assertThat(
            resultChecksum.mightContain(
                "deviceBuildFingerprint/arm64-v8a"
                    + " Module2/android.cts.Hello2Test#testMethod1/pass//"))
        .isTrue();

    // Now creates another fake log file in the result dir, which is not included by the above
    // checksum data file
    File anotherFakeLogFile =
        folder.newFile(Paths.get(resultDir.getName(), "another-fake-log-file.xml").toString());
    Files.writeString(anotherFakeLogFile.toPath(), "This is an another fake log file for testing.");

    assertThat(fileChecksum)
        .doesNotContainKey(resultDir.getName() + "/" + anotherFakeLogFile.getName());
  }
}
