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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidScreenshotDecorator}. */
@RunWith(JUnit4.class)
public class AndroidScreenshotDecoratorTest {

  private static final String DEVICE_ID = "device-test-00000";
  private static final String TEST_GEN_FILE_DIR = "/var/www/gen_files/test001";
  private AndroidScreenshotDecorator androidScreenshotDecorator;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Driver decoratedDriver;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private AndroidMediaUtil androidMediaUtil;
  @Mock private LocalFileUtil fileUtil;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Params params;
  @Mock private Result result;
  @Mock private ResultTypeWithCause resultTypeWithCause;
  @Mock private Warnings warnings;
  @Mock private Log log;
  @Mock private Api api;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.getGenFileDir()).thenReturn(TEST_GEN_FILE_DIR);
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get()).thenReturn(resultTypeWithCause);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.warnings()).thenReturn(warnings);
    when(log.atInfo()).thenReturn(api);
    when(api.alsoTo(any(FluentLogger.class))).thenReturn(api);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.params()).thenReturn(params);
    when(params.get(AndroidScreenshotDecorator.PARAM_SCREENSHOT_ON_PASS)).thenReturn("false");
    androidScreenshotDecorator =
        new AndroidScreenshotDecorator(
            decoratedDriver, testInfo, androidFileUtil, androidMediaUtil, fileUtil);
  }

  @Test
  // Test for no exception.
  public void takeScreenshot_success() throws Exception {
    doNothing().when(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    doNothing().when(fileUtil).prepareDir(TEST_GEN_FILE_DIR);
    when(androidFileUtil.pull(
            eq(DEVICE_ID),
            Mockito.matches(".*\\.png"),
            Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png")))
        .thenReturn("");
    doNothing()
        .when(fileUtil)
        .grantFileOrDirFullAccess(Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    doNothing().when(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));

    androidScreenshotDecorator.run(testInfo);

    // Verifies
    verify(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    verify(fileUtil).prepareDir(TEST_GEN_FILE_DIR);
    verify(androidFileUtil)
        .pull(
            eq(DEVICE_ID),
            Mockito.matches(".*\\.png"),
            Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(fileUtil).grantFileOrDirFullAccess(Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
  }

  @Test
  // Test for android device taking screen shot exception
  public void deviceTakeScreenShotFail() throws Exception {
    MobileHarnessException androidScreenshotException =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_MEDIA_UTIL_TAKE_SCREEN_SHOT_ERROR,
            "Mocked androidScreenshotException exception");
    doThrow(androidScreenshotException)
        .when(androidMediaUtil)
        .takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    doNothing().when(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));

    androidScreenshotDecorator.run(testInfo);

    verify(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    verify(fileUtil, never()).prepareDir(TEST_GEN_FILE_DIR);
    verify(androidFileUtil, never())
        .pull(
            eq(DEVICE_ID),
            Mockito.matches(".*\\.png"),
            Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(fileUtil, never())
        .grantFileOrDirFullAccess(Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
  }

  @Test
  // Test for preparing lab screenshot file path exception
  public void fileWriteFail() throws Exception {
    MobileHarnessException fileWriteException =
        new MobileHarnessException(
            BasicErrorId.LOCAL_DIR_CREATE_ERROR, "Mocked fileWriteException exception");

    doNothing().when(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    doThrow(fileWriteException).when(fileUtil).prepareDir(TEST_GEN_FILE_DIR);
    doNothing().when(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));

    androidScreenshotDecorator.run(testInfo);

    verify(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    verify(fileUtil).prepareDir(TEST_GEN_FILE_DIR);
    verify(androidFileUtil, never())
        .pull(
            eq(DEVICE_ID),
            Mockito.matches(".*\\.png"),
            Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(fileUtil, never())
        .grantFileOrDirFullAccess(Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
  }

  @Test
  // Test for pulling screenshot form device to lab exception
  public void pullScreenshotFail() throws Exception {
    MobileHarnessException exception =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_UTIL_PULL_FILE_ERROR, "Mocked exception");

    doNothing().when(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    doNothing().when(fileUtil).prepareDir(TEST_GEN_FILE_DIR);
    doThrow(exception)
        .when(androidFileUtil)
        .pull(
            eq(DEVICE_ID),
            Mockito.matches(".*\\.png"),
            Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    doNothing().when(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));

    androidScreenshotDecorator.run(testInfo);

    verify(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    verify(fileUtil).prepareDir(TEST_GEN_FILE_DIR);
    verify(androidFileUtil)
        .pull(
            eq(DEVICE_ID),
            Mockito.matches(".*\\.png"),
            Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(fileUtil, never())
        .grantFileOrDirFullAccess(Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
  }

  @Test
  // Test for grant file or dir full access exception
  public void grantAccessFail() throws Exception {
    MobileHarnessException grantAccessException =
        new MobileHarnessException(
            BasicErrorId.LOCAL_FILE_GRANT_PERMISSION_ERROR,
            "Mocked grantAccessException exception");

    doNothing().when(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    doNothing().when(fileUtil).prepareDir(TEST_GEN_FILE_DIR);
    when(androidFileUtil.pull(
            eq(DEVICE_ID),
            Mockito.matches(".*\\.png"),
            Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png")))
        .thenReturn("");
    doThrow(grantAccessException)
        .when(fileUtil)
        .grantFileOrDirFullAccess(Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    doNothing().when(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));

    androidScreenshotDecorator.run(testInfo);

    verify(androidMediaUtil).takeScreenshot(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
    verify(fileUtil).prepareDir(TEST_GEN_FILE_DIR);
    verify(androidFileUtil)
        .pull(
            eq(DEVICE_ID),
            Mockito.matches(".*\\.png"),
            Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(fileUtil).grantFileOrDirFullAccess(Mockito.matches(TEST_GEN_FILE_DIR + ".*\\.png"));
    verify(androidFileUtil).removeFiles(eq(DEVICE_ID), Mockito.matches(".*\\.png"));
  }
}
