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

package com.google.devtools.mobileharness.platform.android.process;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import java.time.Clock;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidProcessUtil}. */
@RunWith(JUnit4.class)
public final class AndroidProcessUtilTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  @Mock private Clock clock;
  @Mock private AndroidSystemSpecUtil systemSpecUtil;

  private static final String SERIAL = "363005dc750400ec";
  private AndroidProcessUtil androidProcessUtil;

  @Before
  public void setUp() {
    androidProcessUtil = new AndroidProcessUtil(adb, systemSpecUtil, clock);
  }

  @Test
  public void checkServiceAvailable_found() throws Exception {
    String serviceName = "package";
    String command =
        String.format(AndroidProcessUtil.ADB_SHELL_TEMPLATE_SERVICE_CHECK, serviceName);
    when(adb.runShellWithRetry(SERIAL, command)).thenReturn("Service package: found");

    assertThat(androidProcessUtil.checkServiceAvailable(SERIAL, serviceName)).isTrue();
  }

  @Test
  public void checkServiceAvailable_notFound() throws Exception {
    String serviceName = "package";
    String command =
        String.format(AndroidProcessUtil.ADB_SHELL_TEMPLATE_SERVICE_CHECK, serviceName);
    when(adb.runShellWithRetry(SERIAL, command))
        .thenReturn("Service package: not found")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    assertThat(androidProcessUtil.checkServiceAvailable(SERIAL, serviceName)).isFalse();
    assertThat(androidProcessUtil.checkServiceAvailable(SERIAL, serviceName)).isFalse();
  }

  @Test
  public void dumpHeap() throws Exception {
    String packageName = "package";
    String processId = "100";
    String outputFile = "/path/to/file";
    AndroidProcessUtil spiedAndroidProcessUtil = spy(androidProcessUtil);
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    doReturn(processId)
        .doReturn(processId)
        .doReturn(null)
        .when(spiedAndroidProcessUtil)
        .getProcessId(utilArgs, packageName);

    String command =
        String.format(AndroidProcessUtil.ADB_SHELL_TEMPLATE_DUMP_HEAP, processId, outputFile);
    when(adb.runShellWithRetry(SERIAL, command))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    spiedAndroidProcessUtil.dumpHeap(utilArgs, packageName, outputFile);
    verify(adb).runShellWithRetry(SERIAL, command);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> spiedAndroidProcessUtil.dumpHeap(utilArgs, packageName, outputFile))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PROCESS_DUMP_HEAP_ERROR);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> spiedAndroidProcessUtil.dumpHeap(utilArgs, packageName, outputFile))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PROCESS_GET_PROCESS_ID_ERROR);
  }

  @Test
  public void getAllProcessId_api26() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).setSdkVersion(26).build();
    String psCommand = AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS + " -A";
    when(adb.runShellWithRetry(SERIAL, psCommand))
        .thenReturn(
            "USER     PID   PPID  VSZ  RSS     WCHAN    ADDR S NAME\n"
                + "root      1      0  10817696   5764 SyS_epoll+      0 S init\n"
                + "radio  2597    867  14343844 122396 SyS_epoll_wait  0 S com.android.phone\n");

    assertThat(androidProcessUtil.getAllProcessId(utilArgs, "com.android.phone"))
        .containsExactly("2597");
  }

  @Test
  public void getAllProcessId_withDuplicateAndSuffix() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "root      1     0     280    188   c009a694 0000c93c S /init\n"
                + "root      2     0     0      0     c004dea0 00000000 S kthreadd\n"
                + "root      3     2     0      0     c003f778 00000000 S ksoftirqd/0\n"
                + "radio     99    29    148888 23376 ffffffff afe0da04 S com.android.phone\n"
                + "app_7     104   29    152796 29200 ffffffff afe0da04 S android.process.acore\n"
                + "app_7     105   29    152796 29200 ffffffff afe0da04 S android.process.acore\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n"
                + "app_24    199   29    152796 29200 ffffffff afe0da04 S"
                + " com.google.android.googlequicksearchbox:search\n");

    assertThat(androidProcessUtil.getAllProcessId(utilArgs, "com.android.phone"))
        .containsExactly("99");
    assertThat(androidProcessUtil.getAllProcessId(utilArgs, "android.process.acore"))
        .containsExactly("104", "105");
    assertThat(androidProcessUtil.getAllProcessId(utilArgs, "com.android.email"))
        .containsExactly("181");
    assertThat(androidProcessUtil.getAllProcessId(utilArgs, "quicksearchbox:search"))
        .containsExactly("199");
    assertThat(androidProcessUtil.getAllProcessId(utilArgs, "not_exist_package")).isEmpty();
  }

  @Test
  public void getProcessId() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "root      1     0     280    188   c009a694 0000c93c S /init\n"
                + "root      2     0     0      0     c004dea0 00000000 S kthreadd\n"
                + "root      3     2     0      0     c003f778 00000000 S ksoftirqd/0\n"
                + "radio     99    29    148888 23376 ffffffff afe0da04 S com.android.phone\n"
                + "app_7     104   29    152796 29200 ffffffff afe0da04 S android.process.acore\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n");

    assertThat(androidProcessUtil.getProcessId(utilArgs, "com.android.phone")).isEqualTo("99");
    assertThat(androidProcessUtil.getProcessId(utilArgs, "com.android.email")).isEqualTo("181");
    assertThat(androidProcessUtil.getProcessId(utilArgs, "not_exist_package")).isNull();
  }

  @Test
  public void getProcessId_handleLineSeparatorRN() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\r\n"
                + "root      1     0     280    188   c009a694 0000c93c S /initr\r\n"
                + "root      2     0     0      0     c004dea0 00000000 S kthreadd\r\n"
                + "root      3     2     0      0     c003f778 00000000 S ksoftirqd/0\r\n"
                + "radio     99    29    148888 23376 ffffffff afe0da04 S com.android.phone\r\n"
                + "app_7     104   29    152796 29200 ffffffff afe0da04 S android.process.acore\r\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\r\n");

    assertThat(androidProcessUtil.getProcessId(utilArgs, "com.android.phone")).isEqualTo("99");
    assertThat(androidProcessUtil.getProcessId(utilArgs, "com.android.email")).isEqualTo("181");
    assertThat(androidProcessUtil.getProcessId(utilArgs, "not_exist_package")).isNull();
  }

  @Test
  public void getProcessId_commandFailed_throwException() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidProcessUtil.getProcessId(utilArgs, "process_name"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PROCESS_GET_PROCESS_STATUS_ERROR);
  }

  @Test
  public void getProcessId_onEmulator() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(systemSpecUtil.isEmulator(SERIAL)).thenReturn(true);
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "u0_a52    3581  1149  491620 29024 ffffffff b7726157 S"
                + " com.google.devtools.mobileharness.platform.android.testdata.testapp.helloco\n");

    assertThat(
            androidProcessUtil.getProcessId(
                utilArgs,
                "com.google.devtools.mobileharness.platform.android.testdata.testapp.helloconcordtest"))
        .isEqualTo("3581");
  }

  @Test
  public void startApplication_withException() throws Exception {
    String packageName = "org.openqa.selenium.android.app";
    String activityName = ".MainActivity";
    when(adb.runShellWithRetry(
            SERIAL,
            AndroidProcessUtil.ADB_SHELL_START_APPLICATION
                + " "
                + packageName
                + '/'
                + activityName))
        .thenReturn("Error: Activity class {MainActivity} does not exist");

    String intent =
        "-e bypass_onboarding true -n com.google.android.apps.youtube.mango/"
            + "com.google.android.apps.youtube.lite.frontend.activities.MainActivity";
    when(adb.runShellWithRetry(
            SERIAL, AndroidProcessUtil.ADB_SHELL_START_APPLICATION_BY_INTENT + intent))
        .thenReturn("Error: Activity not started, you do not have permission to access it.");

    assertThrows(
        MobileHarnessException.class,
        () -> androidProcessUtil.startApplication(SERIAL, packageName, activityName));

    assertThrows(
        MobileHarnessException.class, () -> androidProcessUtil.startApplication(SERIAL, intent));
  }

  @Test
  public void startApplication() throws Exception {
    String packageName = "org.openqa.selenium.android.app";
    String activityName = ".MainActivity";
    when(adb.runShellWithRetry(
            SERIAL,
            AndroidProcessUtil.ADB_SHELL_START_APPLICATION
                + " "
                + packageName
                + '/'
                + activityName))
        .thenReturn("");

    String intent =
        "-e bypass_onboarding true -n com.google.android.apps.youtube.mango/"
            + "com.google.android.apps.youtube.lite.frontend.activities.MainActivity";
    when(adb.runShellWithRetry(
            SERIAL, AndroidProcessUtil.ADB_SHELL_START_APPLICATION_BY_INTENT + intent))
        .thenReturn("");

    androidProcessUtil.startApplication(SERIAL, packageName, activityName);
    androidProcessUtil.startApplication(SERIAL, intent);
  }

  @Test
  public void startService() throws Exception {
    String packageName = "a.b.c.d";
    String serviceName = "a.b.c.d.service";
    when(adb.runShellWithRetry(
            SERIAL,
            AndroidProcessUtil.ADB_SHELL_START_SERVICE + " -n " + packageName + '/' + serviceName))
        .thenReturn("");

    androidProcessUtil.startService(
        UtilArgs.builder().setSerial(SERIAL).build(),
        IntentArgs.builder().setComponent(packageName + '/' + serviceName).build());
  }

  @Test
  public void stopService() throws Exception {
    String packageName = "a.b.c.d";
    String serviceName = "a.b.c.d.service";
    when(adb.runShellWithRetry(
            SERIAL,
            AndroidProcessUtil.ADB_SHELL_STOP_SERVICE + " -n " + packageName + '/' + serviceName))
        .thenReturn("");

    androidProcessUtil.stopService(
        UtilArgs.builder().setSerial(SERIAL).build(),
        IntentArgs.builder().setComponent(packageName + '/' + serviceName).build());
  }

  @Test
  public void startService_withException() throws Exception {
    String packageName = "a.b.c.d";
    String serviceName = "a.b.c.d.service";
    when(adb.runShellWithRetry(
            SERIAL,
            AndroidProcessUtil.ADB_SHELL_START_SERVICE + " -n " + packageName + '/' + serviceName))
        .thenReturn("Error: Not found; no service started.");

    MobileHarnessException expected =
        assertThrows(
            MobileHarnessException.class,
            () ->
                androidProcessUtil.startService(
                    UtilArgs.builder().setSerial(SERIAL).build(),
                    IntentArgs.builder().setComponent(packageName + '/' + serviceName).build()));
    assertThat(expected.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PROCESS_UPDATE_SERVICE_ERROR);
  }

  @Test
  public void startServiceOptions_withException() throws Exception {
    String packageName = "a.b.c.d";
    String serviceName = "a.b.c.d.service";
    when(adb.runShellWithRetry(
            SERIAL,
            AndroidProcessUtil.ADB_SHELL_START_SERVICE
                + " -a action"
                + " -n "
                + packageName
                + '/'
                + serviceName
                + " -e key1 val1"))
        .thenReturn("Error: Not found; no service started.");
    MobileHarnessException expected =
        assertThrows(
            MobileHarnessException.class,
            () ->
                androidProcessUtil.startService(
                    UtilArgs.builder().setSerial(SERIAL).build(),
                    IntentArgs.builder()
                        .setAction("action")
                        .setComponent(packageName + '/' + serviceName)
                        .setExtras(ImmutableMap.of("key1", "val1"))
                        .build()));
    assertThat(expected.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PROCESS_UPDATE_SERVICE_ERROR);
  }

  @Test
  public void isServiceRunning_fALSE() throws Exception {
    String packageName = "a.b.c.d";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(AndroidProcessUtil.ADB_SHELL_TEMPLATE_DUMP_SYS_SERVICE, packageName)))
        .thenReturn("ACTIVITY MANAGER SERVICES (dumpsys activity services)\n (nothing)");

    assertThat(androidProcessUtil.isServiceRunning(SERIAL, packageName)).isFalse();
  }

  @Test
  public void isServiceRunning_tRUE() throws Exception {
    String packageName = "a.b.c.d";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(AndroidProcessUtil.ADB_SHELL_TEMPLATE_DUMP_SYS_SERVICE, packageName)))
        .thenReturn(
            "ACTIVITY MANAGER SERVICES (dumpsys activity services)\n"
                + "  User 0 active services:\n"
                + "  * ServiceRecord{ca08d4c u0 a.b.c.d/.e}\n"
                + "    intent={act=a.i.a.MAIN cat=[a.i.c.LAUNCHER] cmp=a.b.c.d/.e}\n"
                + "    packageName=a.b.c.d\n"
                + "    processName=a.b.c.d\n"
                + "    baseDir=/data/app/a.b.c.d-W3raKI3IrZDy2nOivPX-Hg==/base.apk\n"
                + "    dataDir=/data/user/0/a.b.c.d\n"
                + "    app=ProcessRecord{ba9741a 8814:a.b.c.d/u0a130}\n"
                + "    createTime=-1h3m34s908ms startingBgTimeout=--\n"
                + "    lastActivity=-1h3m34s871ms restartTime=-1h3m34s871ms createdFromFg=true\n"
                + "    startRequested=true delayedStop=false stopIfKilled=false callStart=true\n");

    assertThat(androidProcessUtil.isServiceRunning(SERIAL, packageName)).isTrue();
  }

  @Test
  public void isServiceRunning_commandFailed_throwException() throws Exception {
    String packageName = "a.b.c.d";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(AndroidProcessUtil.ADB_SHELL_TEMPLATE_DUMP_SYS_SERVICE, packageName)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidProcessUtil.isServiceRunning(SERIAL, packageName))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PROCESS_DUMPSYS_SERVICE_ERROR);
  }

  @Test
  public void stopApplication_application_not_started() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n");

    assertThat(androidProcessUtil.stopApplication(utilArgs, "android.process.acore")).isEmpty();
    verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
  }

  @Test
  public void stopApplication_timeout() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(1))
        .thenReturn(Instant.ofEpochSecond(100));
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n")
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n");
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidProcessUtil.stopApplication(utilArgs, "com.android.email"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PROCESS_STOP_PROCESS_TIMEOUT);
    InOrder adbOrder = Mockito.inOrder(adb);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(
            SERIAL, AndroidProcessUtil.ADB_SHELL_AM_FORCE_STOP + "com.android.email");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
  }

  @Test
  public void stopApplication_amForceStop() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n")
        .thenReturn("USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n");

    assertThat(androidProcessUtil.stopApplication(utilArgs, "com.android.email"))
        .containsExactly("181");
    InOrder adbOrder = Mockito.inOrder(adb);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(
            SERIAL, AndroidProcessUtil.ADB_SHELL_AM_FORCE_STOP + "com.android.email");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
  }

  @Test
  public void stopApplication_killProcess_sigTerm() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    when(adb.runShellWithRetry(
            SERIAL, AndroidProcessUtil.ADB_SHELL_AM_FORCE_STOP + "android.process.acore"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Mocked adb shell exception"));
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_7     104   29    152796 29200 ffffffff afe0da04 S android.process.acore\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n")
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n");

    assertThat(androidProcessUtil.stopApplication(utilArgs, "android.process.acore"))
        .containsExactly("104");
    InOrder adbOrder = Mockito.inOrder(adb);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -15 104");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
  }

  @Test
  public void stopApplication_killProcess_sigKill() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    when(adb.runShellWithRetry(
            SERIAL, AndroidProcessUtil.ADB_SHELL_AM_FORCE_STOP + "android.process.acore"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Mocked adb shell exception"));
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_7     104   29    152796 29200 ffffffff afe0da04 S android.process.acore\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n")
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email\n");

    assertThat(androidProcessUtil.stopApplication(utilArgs, "android.process.acore", true))
        .containsExactly("104");
    InOrder adbOrder = Mockito.inOrder(adb);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 104");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
  }

  @Test
  public void stopApplication_kill_all_auto_restart_processes() throws Exception {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(SERIAL).build();
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(1))
        .thenReturn(Instant.ofEpochSecond(2))
        .thenReturn(Instant.ofEpochSecond(3))
        .thenReturn(Instant.ofEpochSecond(4));
    when(adb.runShellWithRetry(
            SERIAL, AndroidProcessUtil.ADB_SHELL_AM_FORCE_STOP + "android.process.acore"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Mocked adb shell exception"));
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS))
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_7     104   29    152796 29200 ffffffff afe0da04 S android.process.acore\n"
                + "app_7     105   29    152796 29200 ffffffff afe0da04 S android.process.acore\n")
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_7     105   29    152796 29200 ffffffff afe0da04 S android.process.acore\n"
                + "app_8     106   29    152796 29200 ffffffff afe0da04 S android.process.acore\n")
        .thenReturn(
            "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n"
                + "app_8     106   29    152796 29200 ffffffff afe0da04 S android.process.acore\n"
                + "app_9     107   29    152796 29200 ffffffff afe0da04 S android.process.acore\n")
        .thenReturn("USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME\n");

    assertThat(androidProcessUtil.stopApplication(utilArgs, "android.process.acore", true))
        .containsExactly("104", "105", "106", "107");
    InOrder adbOrder = Mockito.inOrder(adb);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 104");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 105");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 105");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 106");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 106");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 107");
    adbOrder
        .verify(adb, times(1))
        .runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_GET_PROCESS_STATUS);
  }

  @Test
  public void stopProcess() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 104"))
        .thenReturn("");

    androidProcessUtil.stopProcess(SERIAL, "104", KillSignal.SIGKILL);
  }

  @Test
  public void stopProcess_alreadyStopped() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 104"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR,
                "/system/bin/sh: kill: 104: No such process"));

    androidProcessUtil.stopProcess(SERIAL, "104", KillSignal.SIGKILL);
  }

  @Test
  public void stopProcess_commandFailed_throwException() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidProcessUtil.ADB_SHELL_KILL_PROCESS + " -9 104"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidProcessUtil.stopProcess(SERIAL, "104", KillSignal.SIGKILL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PROCESS_STOP_PROCESS_ERROR);
  }

  @Test
  public void testEnableCommandOutputLogging() {
    androidProcessUtil.enableCommandOutputLogging();
    verify(adb).enableCommandOutputLogging();
  }
}
