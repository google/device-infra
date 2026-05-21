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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidDumpSysSpec;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidDumpSysDecorator}. */
@RunWith(JUnit4.class)
public final class AndroidDumpSysDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private AndroidAdbUtil adbUtil;
  @Mock private SystemStateManager systemStateManager;
  @Mock private Driver decoratedDriver;
  @Mock private Device device;
  @Mock private JobInfo jobInfo;
  @Mock private LocalFileUtil fileUtil;
  @Mock private TestInfo testInfo;
  @Mock private Params params;
  @Mock private Warnings warnings;
  @Mock private Log log;
  @Mock private Api api;

  private static final String DEVICE_ID = "123456";
  private static final String GEN_DIR = "/var/www/tmp";
  private static final String DUMPSYS_LOG = "some example log";

  private AndroidDumpSysDecorator decorator;

  @Before
  public void setUp() {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.warnings()).thenReturn(warnings);
    when(testInfo.log()).thenReturn(log);
    when(log.atInfo()).thenReturn(api);
    when(api.alsoTo(any(FluentLogger.class))).thenReturn(api);
    decorator =
        new AndroidDumpSysDecorator(
            decoratedDriver, testInfo, systemStateManager, adbUtil, fileUtil);
  }

  @Test
  public void run_success() throws Exception {
    mockBasicSetup(
        false,
        AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE_DEFAULT,
        AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT);
    when(adbUtil.dumpSys(
            eq(DEVICE_ID),
            eq(DumpSysType.ACTIVITY),
            eq(AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT)))
        .thenReturn(DUMPSYS_LOG);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adbUtil)
        .dumpSys(
            eq(DEVICE_ID),
            eq(DumpSysType.ACTIVITY),
            eq(AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT));
    verify(warnings, never()).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_success_logToFile() throws Exception {
    mockBasicSetup(
        true,
        AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE_DEFAULT,
        AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT);
    when(adbUtil.dumpSys(
            eq(DEVICE_ID),
            eq(DumpSysType.ACTIVITY),
            eq(AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT)))
        .thenReturn(DUMPSYS_LOG);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adbUtil)
        .dumpSys(
            eq(DEVICE_ID),
            eq(DumpSysType.ACTIVITY),
            eq(AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT));
    verify(fileUtil).writeToFile(startsWith(GEN_DIR), anyString());
    verify(warnings, never()).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_success_customizedParam() throws Exception {
    mockBasicSetup(
        false,
        AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE_NONE,
        AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT);
    when(adbUtil.dumpSys(eq(DEVICE_ID))).thenReturn(DUMPSYS_LOG);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adbUtil).dumpSys(eq(DEVICE_ID));
    verify(adbUtil, never()).dumpSys(anyString(), any(DumpSysType.class), anyString());
    verify(warnings, never()).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_success_runsMultipleCommandsWithParams() throws Exception {
    mockBasicSetup(
        /* logToFile= */ true,
        "first_command_log.txt,second_command_log.txt",
        "uimode,display",
        "input,test");
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("input")))
        .thenReturn(DUMPSYS_LOG);
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.DISPLAY), eq("test")))
        .thenReturn(DUMPSYS_LOG);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("input"));
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.DISPLAY), eq("test"));
    verify(fileUtil).writeToFile(endsWith("first_command_log.txt"), anyString());
    verify(fileUtil).writeToFile(endsWith("second_command_log.txt"), anyString());
    verify(warnings, never()).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_success_usesDefaultTypeForEmptyStringInMultipleCommands() throws Exception {
    mockBasicSetup(false, "uimode,,display", ",input,test");
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("all"))).thenReturn(DUMPSYS_LOG);
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.ACTIVITY), eq("input")))
        .thenReturn(DUMPSYS_LOG);
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.DISPLAY), eq("test")))
        .thenReturn(DUMPSYS_LOG);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("all")); // default suffix
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.ACTIVITY), eq("input")); // default type
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.DISPLAY), eq("test"));
    verify(warnings, never()).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_success_generatesMultipleLogFiles() throws Exception {
    mockBasicSetup(
        /* logToFile= */ true,
        "", // missing log file names
        "uimode,display",
        "input,test");
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("input")))
        .thenReturn(DUMPSYS_LOG);
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.DISPLAY), eq("test")))
        .thenReturn(DUMPSYS_LOG);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("input"));
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.DISPLAY), eq("test"));
    verify(fileUtil).writeToFile(endsWith("dumpsys_0.log"), anyString());
    verify(fileUtil).writeToFile(endsWith("dumpsys_1.log"), anyString());
    verify(warnings, never()).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_success_usesDefaultSuffixesForMissingParams() throws Exception {
    mockBasicSetup(
        /* logToFile= */ false,
        "first_command_log.txt,second_command_log.txt",
        "uimode,display",
        "input"); // missing suffix for second command
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("input")))
        .thenReturn(DUMPSYS_LOG);
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.DISPLAY), eq("all")))
        .thenReturn(DUMPSYS_LOG);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("input"));
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.DISPLAY), eq("all"));
    verify(warnings, never()).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_success_ignoresExtraSuffixes() throws Exception {
    mockBasicSetup(
        /* logToFile= */ false,
        "first_command_log.txt,second_command_log.txt",
        "uimode",
        "input,all"); // missing suffix for second command
    when(adbUtil.dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("input")))
        .thenReturn(DUMPSYS_LOG);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adbUtil).dumpSys(eq(DEVICE_ID), eq(DumpSysType.UIMODE), eq("input"));
    verify(adbUtil, never()).dumpSys(eq(DEVICE_ID), any(DumpSysType.class), eq("all"));
    verify(warnings, never()).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_failedWithError() throws Exception {
    mockBasicSetup(
        false,
        AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE_DEFAULT,
        AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT);
    when(adbUtil.dumpSys(
            eq(DEVICE_ID),
            eq(DumpSysType.ACTIVITY),
            eq(AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT)))
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_DUMPSYS_ERROR, "error"));

    decorator.run(testInfo);

    verify(fileUtil, never()).writeToFile(startsWith(GEN_DIR), anyString());
    verify(warnings).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  @Test
  public void run_failedToLogFileWithException() throws Exception {
    mockBasicSetup(
        true,
        AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE_DEFAULT,
        AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT);
    when(adbUtil.dumpSys(
            eq(DEVICE_ID),
            eq(DumpSysType.ACTIVITY),
            eq(AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT)))
        .thenReturn(DUMPSYS_LOG);
    doThrow(new MobileHarnessException(BasicErrorId.LOCAL_FILE_WRITE_STRING_ERROR, "error"))
        .when(fileUtil)
        .writeToFile(startsWith(GEN_DIR), anyString());

    decorator.run(testInfo);

    verify(warnings).addAndLog(any(MobileHarnessException.class), any(FluentLogger.class));
  }

  private void mockBasicSetup(boolean logToFile, String dumpsysType, String dumpsysSuffix)
      throws Exception {
    mockBasicSetup(
        logToFile,
        "", // No log file names
        dumpsysType,
        dumpsysSuffix);
  }

  private void mockBasicSetup(
      boolean logToFile, String dumpSysLogFileNames, String dumpsysType, String dumpsysSuffix)
      throws Exception {
    when(params.isTrue(AndroidDumpSysSpec.PARAM_LOG_TO_FILE)).thenReturn(logToFile);
    when(params.get(
            AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE, AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE_DEFAULT))
        .thenReturn(dumpsysType);
    when(params.get(
            AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX,
            AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT))
        .thenReturn(dumpsysSuffix);
    when(params.get(
            AndroidDumpSysSpec.PARAM_LOG_FILE_NAME, AndroidDumpSysSpec.PARAM_LOG_FILE_NAME_DEFAULT))
        .thenReturn(dumpSysLogFileNames);
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    if (logToFile) {
      when(testInfo.getGenFileDir()).thenReturn(GEN_DIR);
    }
  }
}
