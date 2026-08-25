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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyPythonVenvUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import java.nio.file.Path;
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
public final class MoblyResultstoreUploadPluginTest {

  private static final String MODULE_NAME = "MyMoblyTestModule";
  private static final String RESULTSTORE_LINK =
      "https://btx.cloud.google.com/invocations/12345-67890";

  @Rule public final MockitoRule rule = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private MoblyPythonVenvUtil moblyPythonVenvUtil;
  @Mock private CommandExecutor commandExecutor;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private TestInfo testInfo;
  @Mock private Log testLog;
  @Mock private Api logApi;

  private LocalFileUtil localFileUtil;
  private MoblyResultstoreUploadPlugin plugin;
  private Path moblyLogDir;

  @Before
  public void setUp() throws Exception {
    localFileUtil = new LocalFileUtil();
    plugin =
        new MoblyResultstoreUploadPlugin(
            moblyPythonVenvUtil, commandExecutor, localFileUtil, androidAdbUtil);

    when(testInfo.log()).thenReturn(testLog);
    when(testLog.atWarning()).thenReturn(logApi);
    when(logApi.alsoTo(any(FluentLogger.class))).thenReturn(logApi);
    when(logApi.withCause(any())).thenReturn(logApi);

    moblyLogDir = tempFolder.newFolder("mobly_logs").toPath();
  }

  @Test
  public void saveLinkToJson_fileDoesNotExist_createsNewJson() throws Exception {
    plugin.saveLinkToJson(RESULTSTORE_LINK, MODULE_NAME, moblyLogDir, testInfo);

    Path reportLogFile =
        moblyLogDir.resolve("report-log-files").resolve(MODULE_NAME + ".reportlog.json");
    assertThat(localFileUtil.isFileExist(reportLogFile)).isTrue();

    String content = localFileUtil.readFile(reportLogFile);
    JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
    assertThat(jsonObject.get(MoblyResultstoreUploadPlugin.RESULTSTORE_LINK_KEY).getAsString())
        .isEqualTo(RESULTSTORE_LINK);
  }

  @Test
  public void saveLinkToJson_fileAlreadyExists_mergesWithExistingJson() throws Exception {
    Path reportLogDir = moblyLogDir.resolve("report-log-files");
    localFileUtil.prepareDir(reportLogDir);
    Path reportLogFile = reportLogDir.resolve(MODULE_NAME + ".reportlog.json");

    JsonObject initialJson = new JsonObject();
    initialJson.addProperty("existing_metric_key", "metric_value");
    localFileUtil.writeToFile(reportLogFile.toString(), new Gson().toJson(initialJson));

    plugin.saveLinkToJson(RESULTSTORE_LINK, MODULE_NAME, moblyLogDir, testInfo);

    assertThat(localFileUtil.isFileExist(reportLogFile)).isTrue();
    String content = localFileUtil.readFile(reportLogFile);
    JsonObject mergedJson = JsonParser.parseString(content).getAsJsonObject();
    assertThat(mergedJson.get("existing_metric_key").getAsString()).isEqualTo("metric_value");
    assertThat(mergedJson.get(MoblyResultstoreUploadPlugin.RESULTSTORE_LINK_KEY).getAsString())
        .isEqualTo(RESULTSTORE_LINK);
  }

  @Test
  public void saveLinkToJson_fileAlreadyExistsWithPreviousLink_overwritesLink() throws Exception {
    Path reportLogDir = moblyLogDir.resolve("report-log-files");
    localFileUtil.prepareDir(reportLogDir);
    Path reportLogFile = reportLogDir.resolve(MODULE_NAME + ".reportlog.json");

    JsonObject initialJson = new JsonObject();
    initialJson.addProperty(
        MoblyResultstoreUploadPlugin.RESULTSTORE_LINK_KEY, "https://old-link.com");
    initialJson.addProperty("custom_data", "123");
    localFileUtil.writeToFile(reportLogFile.toString(), new Gson().toJson(initialJson));

    plugin.saveLinkToJson(RESULTSTORE_LINK, MODULE_NAME, moblyLogDir, testInfo);

    String content = localFileUtil.readFile(reportLogFile);
    JsonObject mergedJson = JsonParser.parseString(content).getAsJsonObject();
    assertThat(mergedJson.get("custom_data").getAsString()).isEqualTo("123");
    assertThat(mergedJson.get(MoblyResultstoreUploadPlugin.RESULTSTORE_LINK_KEY).getAsString())
        .isEqualTo(RESULTSTORE_LINK);
  }

  @Test
  public void saveLinkToJson_fileInvalidJson_recreatesJsonWithLink() throws Exception {
    Path reportLogDir = moblyLogDir.resolve("report-log-files");
    localFileUtil.prepareDir(reportLogDir);
    Path reportLogFile = reportLogDir.resolve(MODULE_NAME + ".reportlog.json");

    localFileUtil.writeToFile(reportLogFile.toString(), "{invalid_json_content");

    plugin.saveLinkToJson(RESULTSTORE_LINK, MODULE_NAME, moblyLogDir, testInfo);

    String content = localFileUtil.readFile(reportLogFile);
    JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
    assertThat(jsonObject.get(MoblyResultstoreUploadPlugin.RESULTSTORE_LINK_KEY).getAsString())
        .isEqualTo(RESULTSTORE_LINK);
  }

  @Test
  public void saveLinkToJson_fileNotJsonObject_recreatesJsonWithLink() throws Exception {
    Path reportLogDir = moblyLogDir.resolve("report-log-files");
    localFileUtil.prepareDir(reportLogDir);
    Path reportLogFile = reportLogDir.resolve(MODULE_NAME + ".reportlog.json");

    localFileUtil.writeToFile(reportLogFile.toString(), "[\"array_element\"]");

    plugin.saveLinkToJson(RESULTSTORE_LINK, MODULE_NAME, moblyLogDir, testInfo);

    String content = localFileUtil.readFile(reportLogFile);
    JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
    assertThat(jsonObject.get(MoblyResultstoreUploadPlugin.RESULTSTORE_LINK_KEY).getAsString())
        .isEqualTo(RESULTSTORE_LINK);
  }

  @Test
  public void saveLinkToJson_fileEmpty_recreatesJsonWithLink() throws Exception {
    Path reportLogDir = moblyLogDir.resolve("report-log-files");
    localFileUtil.prepareDir(reportLogDir);
    Path reportLogFile = reportLogDir.resolve(MODULE_NAME + ".reportlog.json");

    localFileUtil.writeToFile(reportLogFile.toString(), "");

    plugin.saveLinkToJson(RESULTSTORE_LINK, MODULE_NAME, moblyLogDir, testInfo);

    String content = localFileUtil.readFile(reportLogFile);
    JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
    assertThat(jsonObject.get(MoblyResultstoreUploadPlugin.RESULTSTORE_LINK_KEY).getAsString())
        .isEqualTo(RESULTSTORE_LINK);
  }
}
