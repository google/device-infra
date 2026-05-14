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

package com.google.wireless.qa.mobileharness.shared.api.decorator.util;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.io.File;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ReportLogCollectorUtilTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private TestInfo testInfo;

  private File tempDir;
  private Gson gson;
  private LocalFileUtil localFileUtil;

  @Before
  public void setUp() throws Exception {
    tempDir = temporaryFolder.newFolder();
    gson = new Gson();
    localFileUtil = new LocalFileUtil();

    when(testInfo.log()).thenReturn(new Log(new Timing()));
  }

  @Test
  public void reformatJsonString_validJson_reformatsCorrectly() {
    String input = "\"stream1\":{\"key1\":\"value1\"},\"stream1\":{\"key2\":\"value2\"}";
    String expectedOutput = "{\"stream1\":[{\"key1\":\"value1\"},{\"key2\":\"value2\"}]}";

    String actualOutput = ReportLogCollectorUtil.reformatJsonString(input);

    assertThat(gson.fromJson(actualOutput, JsonObject.class))
        .isEqualTo(gson.fromJson(expectedOutput, JsonObject.class));
  }

  @Test
  public void merge_destFileDoesNotExist_createsDestFile() throws Exception {
    File origFile = new File(tempDir, "orig.json");
    File destFile = new File(tempDir, "dest.json");

    String origContent = "{\"stream1\":[{\"key1\":\"value1\"}]}";
    Files.writeString(origFile.toPath(), origContent);

    ReportLogCollectorUtil.merge(origFile, destFile, localFileUtil);

    assertThat(destFile.exists()).isTrue();
    assertThat(Files.readString(destFile.toPath())).isEqualTo(origContent);
  }

  @Test
  public void merge_destFileExists_mergesContent() throws Exception {
    File origFile = new File(tempDir, "orig.json");
    File destFile = new File(tempDir, "dest.json");

    String origContent = "{\"stream1\":[{\"key1\":\"value1\"}]}";
    String destContent =
        "{\"stream1\":[{\"key2\":\"value2\"}],\"stream2\":[{\"key3\":\"value3\"}]}";

    Files.writeString(origFile.toPath(), origContent);
    Files.writeString(destFile.toPath(), destContent);

    ReportLogCollectorUtil.merge(origFile, destFile, localFileUtil);

    // Tradefed merge appends old data to new data: [new, old]
    String expectedContent =
        "{\"stream1\":[{\"key1\":\"value1\"},{\"key2\":\"value2\"}],\"stream2\":[{\"key3\":\"value3\"}]}";

    assertThat(gson.fromJson(Files.readString(destFile.toPath()), JsonObject.class))
        .isEqualTo(gson.fromJson(expectedContent, JsonObject.class));
  }

  @Test
  public void reformatJsonString_keysWithUppercaseAndHyphen_returnsOriginalString() {
    String input = "\"Stream-1\":{\"key1\":\"value1\"},\"Stream-1\":{\"key2\":\"value2\"}";

    String actualOutput = ReportLogCollectorUtil.reformatJsonString(input);

    // Regex [a-z0-9_]* does not match uppercase or hyphen, so it should return the original string.
    assertThat(actualOutput).isEqualTo(input);
  }

  @Test
  public void reformatJsonString_invalidJsonElement_includesElement() {
    String input = "\"stream1\":{\"key1\":\"value1\"},\"stream1\":{invalid_json}";
    String expectedOutput = "{\"stream1\":[{\"key1\":\"value1\"},{invalid_json}]}";

    String actualOutput = ReportLogCollectorUtil.reformatJsonString(input);

    assertThat(actualOutput).isEqualTo(expectedOutput);
  }

  @Test
  public void pullFromHost_mergesWithExistingDestFile() throws Exception {
    File srcDir = temporaryFolder.newFolder("src");
    File file1 = new File(srcDir, "file.json");

    String content1 = "{\"stream1\":[{\"key1\":\"value1\"}]}";
    Files.writeString(file1.toPath(), content1);

    File destDir = temporaryFolder.newFolder("dest");
    File destFile = new File(destDir, "file.json");
    String destContent = "{\"stream1\":[{\"key2\":\"value2\"}]}";
    Files.writeString(destFile.toPath(), destContent);

    ReportLogCollectorUtil.pullFromHost(srcDir, destDir, localFileUtil, testInfo);

    assertThat(destFile.exists()).isTrue();

    String actualContent = Files.readString(destFile.toPath());
    String expectedContent = "{\"stream1\":[{\"key1\":\"value1\"},{\"key2\":\"value2\"}]}";

    assertThat(gson.fromJson(actualContent, JsonObject.class))
        .isEqualTo(gson.fromJson(expectedContent, JsonObject.class));
    assertThat(srcDir.exists()).isFalse();
  }

  @Test
  public void reformatRepeatedStreams_nonRecursive() throws Exception {
    File resultDir = temporaryFolder.newFolder("result");
    File file1 = new File(resultDir, "file1.json");

    String input = "\"stream1\":{\"key1\":\"value1\"},\"stream1\":{\"key2\":\"value2\"}";

    Files.writeString(file1.toPath(), input);

    ReportLogCollectorUtil.reformatRepeatedStreams(resultDir, localFileUtil, testInfo);

    String expectedOutput = "{\"stream1\":[{\"key1\":\"value1\"},{\"key2\":\"value2\"}]}";

    String content1 = Files.readString(file1.toPath());

    assertThat(gson.fromJson(content1, JsonObject.class))
        .isEqualTo(gson.fromJson(expectedOutput, JsonObject.class));
  }
}
