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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospTestSetupUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.EmptyDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.io.File;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link MoblyAospTest}. */
@RunWith(JUnit4.class)
public final class MoblyAospTestTest {

  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private EmptyDevice emptyDevice;
  @Mock private File configFile;
  @Mock private MoblyAospTestSetupUtil setupUtil;

  private Params params;
  private final Timing timing = new Timing();
  private final Log log = new Log(timing);

  @Test
  public void generateTestCommand_verifySetupUtilArgs() throws Exception {
    when(testInfo.getTmpFileDir()).thenReturn("/tmp");
    when(testInfo.log()).thenReturn(log);
    when(files.getSingle(MoblyAospTest.FILE_MOBLY_PKG)).thenReturn("sample_test.zip");
    when(jobInfo.files()).thenReturn(files);
    params = new Params(null);
    params.add(MoblyAospTest.PARAM_TEST_PATH, "sample_test.py");
    params.add(MoblyTest.TEST_SELECTOR_KEY, "test1 test2");
    params.add(MoblyAospTest.PARAM_PYTHON_VERSION, "3.10");
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(configFile.getPath()).thenReturn("config.yaml");
    MoblyAospTest moblyAospTest = new MoblyAospTest(emptyDevice, testInfo, setupUtil);

    var unused = moblyAospTest.generateTestCommand(testInfo, configFile, false);

    verify(setupUtil)
        .setupEnvAndGenerateTestCommand(
            Path.of("sample_test.zip"),
            Path.of("/tmp/mobly"),
            Path.of("/tmp/venv"),
            Path.of("config.yaml"),
            "sample_test.py",
            "test1 test2",
            "3.10",
            /* installMoblyTestPackageArgs= */ null);
  }
}
