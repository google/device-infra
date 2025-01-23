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

package com.google.devtools.mobileharness.infra.ats.console.util.verifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.File;
import java.nio.file.Path;
import javax.inject.Inject;
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
public class VerifierResultHelperTest {

  private static final String CURRENT_TIME = "2025-01-21 20:44:24.181";
  private static final int SDK_VERSION = 36;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Bind @Mock private Adb adb;
  @Bind @Mock private AndroidPackageManagerUtil androidPackageManagerUtil;
  @Bind @Mock private AndroidProcessUtil androidProcessUtil;
  @Bind @Mock private AndroidSystemSettingUtil systemSettingUtil;

  @Inject private VerifierResultHelper verifierResultHelper;

  @Before
  public void setUp() throws Exception {
    when(adb.runShellWithRetry(anyString(), eq("date '+%Y-%m-%d %H:%M:%S.%3N'")))
        .thenReturn(CURRENT_TIME);
    when(systemSettingUtil.getDeviceSdkVersion(anyString())).thenReturn(SDK_VERSION);
    when(androidPackageManagerUtil.getAppVersionName(anyString(), anyString())).thenReturn("16_r1");
    when(androidPackageManagerUtil.getApkVersionName(anyString())).thenReturn("16_r1");
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void testBroadcastResults() throws Exception {
    Result result =
        Result.newBuilder()
            .addModuleInfo(
                Module.newBuilder()
                    .setName("module1")
                    .setDone(true)
                    .setFailedTests(1)
                    .addTestCase(
                        TestCase.newBuilder()
                            .setName("testClass1")
                            .addTest(
                                ReportProto.Test.newBuilder().setName("test1").setResult("fail"))
                            .addTest(
                                ReportProto.Test.newBuilder()
                                    .setName("test2")
                                    .setResult("IGNORED"))))
            .addModuleInfo(
                Module.newBuilder()
                    .setName("module2")
                    .setDone(true)
                    .setPassed(1)
                    .addTestCase(
                        TestCase.newBuilder()
                            .setName("testClass2")
                            .addTest(
                                ReportProto.Test.newBuilder().setName("test3").setResult("pass"))))
            .addModuleInfo(
                Module.newBuilder()
                    .setName("module3")
                    .setDone(false)
                    .setReason(
                        Reason.newBuilder()
                            .setMsg(
                                "Test run failed to complete. Expected 11 tests, received 2."
                                    + " onError: commandError=false"
                                    + " message=INSTRUMENTATION_ABORTED: System has crashed. Java"
                                    + " Crash Messages sorted from most recent: The system died;"
                                    + " earlier logs will point to the root cause")))
            .build();

    verifierResultHelper.broadcastResults(
        result, ImmutableList.of("device1", "device2"), folder.newFolder("xts_root").toPath());

    verifyCommandsOnDevice("device1");
    verifyCommandsOnDevice("device2");
  }

  @Test
  public void testInstallCtsVerifierApk_previousNotInstalled() throws Exception {
    Path xtsRoot = folder.newFolder("xts_root").toPath();
    File apk = xtsRoot.resolve(VerifierResultHelper.CTS_VERIFIER_APK).toFile();
    apk.createNewFile();
    when(androidPackageManagerUtil.getAppVersionName(anyString(), anyString()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_APK_VERSION_NAME_ERROR, ""));

    verifierResultHelper.broadcastResults(
        Result.getDefaultInstance(), ImmutableList.of("device"), xtsRoot);

    verify(androidPackageManagerUtil).installApk("device", SDK_VERSION, apk.getPath());
  }

  @Test
  public void testInstallCtsVerifierApk_versionNameMismatch() throws Exception {
    Path xtsRoot = folder.newFolder("xts_root").toPath();
    File apk = xtsRoot.resolve(VerifierResultHelper.CTS_VERIFIER_APK).toFile();
    apk.createNewFile();
    when(androidPackageManagerUtil.getAppVersionName(anyString(), anyString())).thenReturn("16_r2");

    verifierResultHelper.broadcastResults(
        Result.getDefaultInstance(), ImmutableList.of("device"), xtsRoot);

    verify(androidPackageManagerUtil).installApk("device", SDK_VERSION, apk.getPath());
  }

  @Test
  public void testInstallCtsVerifierApk_versionNameMatch_skip() throws Exception {
    Path xtsRoot = folder.newFolder("xts_root").toPath();
    File apk = xtsRoot.resolve(VerifierResultHelper.CTS_VERIFIER_APK).toFile();
    apk.createNewFile();

    verifierResultHelper.broadcastResults(
        Result.getDefaultInstance(), ImmutableList.of("device"), xtsRoot);

    verify(androidPackageManagerUtil, never()).installApk("device", SDK_VERSION, apk.getPath());
  }

  private void verifyCommandsOnDevice(String serial) throws Exception {
    verify(androidProcessUtil)
        .startApplication(
            serial,
            VerifierResultHelper.VERIFIER_PACKAGE,
            VerifierResultHelper.MAIN_ACTIVITY,
            null,
            true);
    verify(androidProcessUtil)
        .startApplication(
            serial,
            VerifierResultHelper.VERIFIER_PACKAGE,
            VerifierResultHelper.HOST_TESTS_ACTIVITY);
    verify(adb)
        .run(
            eq(serial),
            eq(new String[] {"logcat", "-T", CURRENT_TIME, "HostTestsActivity:I", "*:S"}),
            any(),
            any());
    verify(adb)
        .runShell(
            serial,
            VerifierResultHelper.BROADCAST_COMMAND
                + "'{\"module1\":{\"result\":\"FAIL\",\"details\":\"\",\"subtests\":"
                + "{\"testClass1\":{\"result\":\"\",\"details\":\"\",\"subtests\":"
                + "{\"test1\":{\"result\":\"FAIL\",\"details\":\"\",\"subtests\":{}},"
                + "\"test2\":{\"result\":\"PASS\",\"details\":\"\",\"subtests\":{}}}}}}}'");
    verify(adb)
        .runShell(
            serial,
            VerifierResultHelper.BROADCAST_COMMAND
                + "'{\"module2\":{\"result\":\"PASS\",\"details\":\"\",\"subtests\":"
                + "{\"testClass2\":{\"result\":\"\",\"details\":\"\",\"subtests\":"
                + "{\"test3\":{\"result\":\"PASS\",\"details\":\"\",\"subtests\":{}}}}}}}'");
    verify(adb)
        .runShell(
            serial,
            VerifierResultHelper.BROADCAST_COMMAND
                + "'{\"module3\":{\"result\":\"FAIL\",\"details\":\"Test run failed to complete."
                + " Expected 11 tests, received 2. onError: commandError\\u003dfalse"
                + " message\\u003dINST...\",\"subtests\":{}}}'");
  }
}
