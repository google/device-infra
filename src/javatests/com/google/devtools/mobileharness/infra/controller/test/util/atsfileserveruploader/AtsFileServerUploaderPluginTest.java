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

package com.google.devtools.mobileharness.infra.controller.test.util.atsfileserveruploader;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AtsFileServerUploaderPluginTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private TestEndingEvent testEndingEvent;
  @Mock private TestInfo testInfo;
  @Mock private JobSetting jobSetting;
  @Mock private JobInfo jobInfo;
  @Mock private CommandExecutor commandExecutor;
  @Mock private LocalFileUtil localFileUtil;

  private AtsFileServerUploaderPlugin plugin;

  @Before
  public void setUp() throws Exception {
    plugin =
        new AtsFileServerUploaderPlugin(localFileUtil, "www.example.com:8006") {

          @Override
          CommandExecutor createCommandExecutor() {
            return commandExecutor;
          }
        };
    when(testEndingEvent.getTest()).thenReturn(testInfo);
    when(testInfo.getGenFileDir()).thenReturn("/var");
    when(testInfo.getTmpFileDir()).thenReturn("/tmp");
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.locator())
        .thenReturn(new TestLocator("test_id", "test_name", new JobLocator("job_id", "job_name")));
    when(jobInfo.setting()).thenReturn(jobSetting);
    when(jobSetting.getRunFileDir()).thenReturn("/run");
    when(jobSetting.hasRunFileDir()).thenReturn(true);
  }

  @Test
  public void test() throws Exception {
    when(localFileUtil.listFilePaths("/var", true))
        .thenReturn(ImmutableList.of("/var/output.txt", "/var/hello/world.txt"));
    plugin.onTestEnding(testEndingEvent);

    verify(commandExecutor)
        .run(
            Command.of(
                "curl",
                "--request",
                "POST",
                "--form",
                "file=@/var/output.txt",
                "--fail",
                "--location",
                "www.example.com:8006/file/genfiles/test_id/output.txt"));
    verify(commandExecutor)
        .run(
            Command.of(
                "curl",
                "--request",
                "POST",
                "--form",
                "file=@/var/hello/world.txt",
                "--fail",
                "--location",
                "www.example.com:8006/file/genfiles/test_id/hello/world.txt"));
    verify(localFileUtil).removeFileOrDir("/var");
    verify(localFileUtil).removeFileOrDir("/tmp");
    verify(localFileUtil).removeFileOrDir("/run");
  }
}
