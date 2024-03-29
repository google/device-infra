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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.InstallMoblyTestDepsArgs;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospPackageTestSetupUtil;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.EmptyDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link MoblyAospPackageTest}. */
@RunWith(JUnit4.class)
public final class MoblyAospPackageTestTest {

  private static final String PY_PKG_INDEX_URL = "https://python.package.index/url";

  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private EmptyDevice emptyDevice;
  @Mock private File configFile;
  @Mock private MoblyAospPackageTestSetupUtil setupUtil;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private MoblyYamlParser moblyYamlParser;
  @Mock private MoblyTestInfoMapHelper moblyTestInfoMapHelper;

  private Params params;

  @Test
  public void convertMoblyConfig_stripsMhPrefix() throws Exception {
    JSONObject moblyJson = new JSONObject();
    moblyJson.put("param1", "value1");
    moblyJson.put("mh_param2", "value2");
    moblyJson.put("param3", new JSONObject(ImmutableMap.of("mh_param3a", "value3a")));
    moblyJson.put(
        "param4",
        new JSONArray(ImmutableList.of(new JSONObject(ImmutableMap.of("mh_param4a", "value4a")))));
    JSONObject newMoblyJson = MoblyAospPackageTest.convertMoblyConfig(moblyJson);
    assertThat(newMoblyJson.get("param1")).isEqualTo("value1");
    assertThat(newMoblyJson.get("param2")).isEqualTo("value2");
    assertThat(newMoblyJson.getJSONObject("param3").get("param3a")).isEqualTo("value3a");
    assertThat(newMoblyJson.getJSONArray("param4").getJSONObject(0).get("param4a"))
        .isEqualTo("value4a");
  }

  @Test
  public void generateTestCommand_verifySetupUtilArgs() throws Exception {
    when(testInfo.getTmpFileDir()).thenReturn("/tmp");
    when(files.getSingle(MoblyAospPackageTest.FILE_MOBLY_PKG)).thenReturn("sample_test.zip");
    when(jobInfo.files()).thenReturn(files);
    params = new Params(null);
    params.add(MoblyAospPackageTest.PARAM_TEST_PATH, "sample_test.py");
    params.add(MoblyGenericTest.TEST_SELECTOR_KEY, "test1 test2");
    params.add(MoblyAospPackageTest.PARAM_PYTHON_VERSION, "3.10");
    params.add(MoblyAospPackageTest.PARAM_PY_PKG_INDEX_URL, PY_PKG_INDEX_URL);
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(configFile.getPath()).thenReturn("config.yaml");
    InstallMoblyTestDepsArgs installMoblyTestDepsArgs =
        InstallMoblyTestDepsArgs.builder()
            .setDefaultTimeout(Duration.ofMinutes(30))
            .setIndexUrl(PY_PKG_INDEX_URL)
            .build();
    MoblyAospPackageTest moblyAospPackageTest =
        new MoblyAospPackageTest(
            emptyDevice,
            testInfo,
            moblyYamlParser,
            moblyTestInfoMapHelper,
            new CommandExecutor(),
            setupUtil,
            localFileUtil);

    var unused = moblyAospPackageTest.generateTestCommand(testInfo, configFile);

    verify(setupUtil)
        .setupEnvAndGenerateTestCommand(
            Path.of("sample_test.zip"),
            Path.of("/tmp/mobly"),
            Path.of("/tmp/venv"),
            Path.of("config.yaml"),
            "sample_test.py",
            "test1 test2",
            "3.10",
            installMoblyTestDepsArgs);
  }
}
