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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.ReportLogCollectorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ReportLogCollectorDecoratorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Device device;
  @Mock private Driver decoratedDriver;
  @Mock private JobInfo jobInfo;
  @Mock private TestInfo testInfo;
  @Mock private Params params;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private LocalFileUtil localFileUtil;

  private ReportLogCollectorDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(device.getDeviceId()).thenReturn("device_id");
    when(testInfo.getGenFileDir()).thenReturn("/gen_file_dir");

    decorator =
        new ReportLogCollectorDecorator(decoratedDriver, testInfo, androidFileUtil, localFileUtil);
  }

  @Test
  public void setUp_createsDestDir() throws Exception {
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_DEST_DIR)).thenReturn("dest_dir");

    decorator.setUp(testInfo);

    verify(localFileUtil).prepareDir(eq(new File("/gen_file_dir/dest_dir").getAbsolutePath()));
  }

  @Test
  public void tearDown_pullsAndReformatsLogs() throws Exception {
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_SRC_DIR)).thenReturn("src_dir");
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_DEST_DIR)).thenReturn("dest_dir");
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_TEMP_DIR)).thenReturn("temp_dir");
    when(params.getBool(ReportLogCollectorSpec.PARAM_REPORT_LOG_DEVICE_DIR, false))
        .thenReturn(false);

    decorator.tearDown(testInfo);

    verify(androidFileUtil).pull(eq("device_id"), eq("src_dir"), any());
  }

  @Test
  public void tearDown_appendsDeviceIdToTempDir_whenDeviceDirIsTrue() throws Exception {
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_SRC_DIR)).thenReturn("src_dir");
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_DEST_DIR)).thenReturn("dest_dir");
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_TEMP_DIR)).thenReturn("temp_dir");
    when(params.getBool(ReportLogCollectorSpec.PARAM_REPORT_LOG_DEVICE_DIR, false))
        .thenReturn(true);

    decorator.tearDown(testInfo);

    verify(androidFileUtil)
        .pull(
            eq("device_id"),
            eq("src_dir"),
            eq(new File("/gen_file_dir", "temp_dir-device_id").getAbsolutePath()));
  }

  @Test
  public void tearDown_createsHostReportDir() throws Exception {
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_SRC_DIR)).thenReturn("src_dir");
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_DEST_DIR)).thenReturn("dest_dir");
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_TEMP_DIR)).thenReturn("temp_dir");

    decorator.tearDown(testInfo);

    verify(localFileUtil).prepareDir(eq(new File("/gen_file_dir", "temp_dir").getAbsolutePath()));
  }

  @Test
  public void tearDown_createsResultDir() throws Exception {
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_SRC_DIR)).thenReturn("src_dir");
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_DEST_DIR)).thenReturn("dest_dir");
    when(params.get(ReportLogCollectorSpec.PARAM_REPORT_LOG_TEMP_DIR)).thenReturn("temp_dir");

    decorator.tearDown(testInfo);

    verify(localFileUtil).prepareDir(eq(new File("/gen_file_dir/dest_dir").getAbsolutePath()));
  }
}
