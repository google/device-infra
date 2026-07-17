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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidCdpDecorator}. */
@RunWith(JUnit4.class)
public class AndroidCdpDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String DEVICE_ID = "device_123";
  private static final String PACKAGE_NAME = "com.example.webview";
  private static final String ACTIVITY_NAME = "MainActivity";
  private static final int DEBUG_PORT = 9876;

  @Mock private Driver decoratedDriver;
  @Mock private TestInfo testInfo;
  @Mock private Device device;
  @Mock private JobInfo jobInfo;
  @Mock private Params params;
  @Mock private Properties properties;
  @Mock private Log log;
  @Mock private Api loggingApi;

  @Mock private CommandExecutor commandExecutor;
  @Mock private AndroidAdbUtil adbUtil;
  @Mock private AndroidProcessUtil processUtil;
  @Mock private Adb adb;
  @Mock private AndroidFileUtil fileUtil;

  private SetupContext setupContext;
  private AndroidCdpDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.properties()).thenReturn(properties);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.getTmpFileDir()).thenReturn("/tmp");

    setupContext = SetupContext.create(testInfo);

    // Mock logs to avoid NPE
    when(log.atInfo()).thenReturn(loggingApi);
    when(log.atWarning()).thenReturn(loggingApi);
    when(loggingApi.alsoTo(any(FluentLogger.class))).thenReturn(loggingApi);
    when(loggingApi.withCause(any(Throwable.class))).thenReturn(loggingApi);
    when(loggingApi.withCause(nullable(Throwable.class))).thenReturn(loggingApi);

    decorator =
        Mockito.spy(
            new AndroidCdpDecorator(
                decoratedDriver,
                testInfo,
                device,
                commandExecutor,
                adbUtil,
                processUtil,
                adb,
                fileUtil));
  }

  @Test
  public void setUp_webView_appStartup_successAfterRetries() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT);

    // Mock resolve default activity
    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);
    // Mock startApplication
    doNothing().when(processUtil).startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);

    // Mock getProcessId to fail twice, then succeed
    AtomicInteger attempts = new AtomicInteger(0);
    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME)))
        .thenAnswer(
            invocation -> {
              int attempt = attempts.incrementAndGet();
              if (attempt < 3) {
                return ""; // not started yet
              }
              return "12345"; // PID
            });

    // Mock socket readiness to succeed instantly
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Run setup
    decorator.setUp(setupContext);

    // Verify it retried 3 times in loop, plus 1 call for port forwarding socket name construction
    verify(processUtil, times(4)).getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME));
    verify(adbUtil)
        .forwardTcpPort(DEVICE_ID, DEBUG_PORT, "localabstract:webview_devtools_remote_12345");
  }

  @Test
  public void setUp_webView_appStartup_timeout() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT);

    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);

    // Mock getProcessId to always return empty/null
    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME))).thenReturn("");

    // Verify it throws MobileHarnessException with CDP_APP_LAUNCH_ERROR
    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_APP_LAUNCH_ERROR);
    assertThat(exception).hasMessageThat().contains("Timed out waiting for WebView app package");

    // Verify it retried 30 times (as defined in RetryStrategy)
    verify(processUtil, times(30)).getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME));
  }

  @Test
  public void setUp_socketReadiness_successAfterRetries() throws Exception {
    mockParams(false, null, null, DEBUG_PORT); // Browser test

    // Browser setup mocks
    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");

    // Mock adb runShell for socket check to return empty twice, then succeed
    AtomicInteger attempts = new AtomicInteger(0);
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenAnswer(
            invocation -> {
              int attempt = attempts.incrementAndGet();
              if (attempt < 3) {
                return ""; // socket not ready
              }
              return "chrome_devtools_remote";
            });

    decorator.setUp(setupContext);

    // Verify runShell was called 3 times for socket check
    verify(adb, times(3))
        .runShell(
            eq(DEVICE_ID),
            eq("cat /proc/net/unix | grep -e chrome_devtools -e webview_devtools"),
            any(Duration.class));
    verify(adbUtil).forwardTcpPort(DEVICE_ID, DEBUG_PORT, "localabstract:chrome_devtools_remote");
  }

  @Test
  public void setUp_socketReadiness_warningOnTimeout() throws Exception {
    mockParams(false, null, null, DEBUG_PORT); // Browser test

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");

    // Mock adb runShell to always return empty
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class))).thenReturn("");

    // Setup should NOT throw exception on socket timeout (it only logs a warning according to code)
    decorator.setUp(setupContext);

    // Verify runShell was called 20 times (uniformDelay 500ms, 20 attempts)
    verify(adb, times(20))
        .runShell(
            eq(DEVICE_ID),
            eq("cat /proc/net/unix | grep -e chrome_devtools -e webview_devtools"),
            any(Duration.class));
    // Verify it still proceeds to forward the port
    verify(adbUtil).forwardTcpPort(DEVICE_ID, DEBUG_PORT, "localabstract:chrome_devtools_remote");
  }

  private void mockParams(boolean isWebView, String pkgName, String activity, int debugPort) {
    mockParams(isWebView, pkgName, activity, debugPort, "", "");
  }

  private void mockParams(
      boolean isWebView,
      String pkgName,
      String activity,
      int debugPort,
      String chromeFlags,
      String reversePortMappings) {
    when(params.get(AndroidCdpDecorator.PARAM_TARGET_TYPE, ""))
        .thenReturn(isWebView ? "webview" : "browser");
    when(params.get(AndroidCdpDecorator.PARAM_PACKAGE_NAME, null)).thenReturn(pkgName);
    when(params.get(AndroidCdpDecorator.PARAM_ACTIVITY_NAME, null)).thenReturn(activity);
    when(params.get(AndroidCdpDecorator.PARAM_LOCAL_SERVER_PORT, ""))
        .thenReturn(reversePortMappings);
    when(jobInfo.params()).thenReturn(params);
    when(params.getInt(AndroidCdpDecorator.PARAM_DEBUG_PORT, 0)).thenReturn(debugPort);
    when(params.get(AndroidCdpDecorator.PARAM_CHROME_FLAGS, "")).thenReturn(chromeFlags);

    // Files tagging mocks
    when(jobInfo.files()).thenReturn(mockFiles());
  }

  @Test
  public void setUp_webView_withChromeFlags_success() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT, "--disable-gpu --no-sandbox", "");

    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);
    doNothing().when(processUtil).startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);

    // Mock stopApplication succeeds
    when(processUtil.stopApplication(any(UtilArgs.class), eq(PACKAGE_NAME)))
        .thenReturn(ImmutableList.of());

    // Mock getProcessId to succeed instantly
    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME))).thenReturn("12345");

    // Mock socket readiness to succeed instantly
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock fileUtil push and adb runShell chmod 666
    when(fileUtil.push(
            eq(DEVICE_ID),
            any(Integer.class),
            anyString(),
            eq("/data/local/tmp/webview-command-line")))
        .thenReturn("");
    when(adb.runShell(eq(DEVICE_ID), eq("chmod 666 /data/local/tmp/webview-command-line")))
        .thenReturn("");

    decorator.setUp(setupContext);

    // Verify stopApplication was called
    verify(processUtil).stopApplication(any(UtilArgs.class), eq(PACKAGE_NAME));
    // Verify fileUtil pushed the webview flag file
    verify(fileUtil)
        .push(
            eq(DEVICE_ID),
            any(Integer.class),
            anyString(),
            eq("/data/local/tmp/webview-command-line"));
    // Verify chmod 666 was run
    verify(adb).runShell(eq(DEVICE_ID), eq("chmod 666 /data/local/tmp/webview-command-line"));
  }

  @Test
  public void setUp_browser_withChromeFlags_success() throws Exception {
    mockParams(false, null, null, DEBUG_PORT, "--disable-gpu --no-sandbox", "");

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");

    // Mock stopApplication succeeds
    when(processUtil.stopApplication(any(UtilArgs.class), eq("com.android.chrome")))
        .thenReturn(ImmutableList.of());

    // Mock socket readiness to succeed instantly
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock fileUtil push and adb runShell chmod 666 for both destinations
    when(fileUtil.push(
            eq(DEVICE_ID),
            any(Integer.class),
            anyString(),
            eq("/data/local/tmp/chrome-command-line")))
        .thenReturn("");
    when(fileUtil.push(
            eq(DEVICE_ID), any(Integer.class), anyString(), eq("/data/local/chrome-command-line")))
        .thenReturn("");
    when(adb.runShell(eq(DEVICE_ID), eq("chmod 666 /data/local/tmp/chrome-command-line")))
        .thenReturn("");
    when(adb.runShell(eq(DEVICE_ID), eq("chmod 666 /data/local/chrome-command-line")))
        .thenReturn("");

    decorator.setUp(setupContext);

    // Verify stopApplication was called on Chrome package
    verify(processUtil).stopApplication(any(UtilArgs.class), eq("com.android.chrome"));
    // Verify fileUtil pushed to both locations
    verify(fileUtil)
        .push(
            eq(DEVICE_ID),
            any(Integer.class),
            anyString(),
            eq("/data/local/tmp/chrome-command-line"));
    verify(fileUtil)
        .push(
            eq(DEVICE_ID), any(Integer.class), anyString(), eq("/data/local/chrome-command-line"));
    // Verify chmod 666 was run on both locations
    verify(adb).runShell(eq(DEVICE_ID), eq("chmod 666 /data/local/tmp/chrome-command-line"));
    verify(adb).runShell(eq(DEVICE_ID), eq("chmod 666 /data/local/chrome-command-line"));
  }

  @Test
  public void setUp_reversePortForwarding_invalidMappingFormat_throwsException() throws Exception {
    // Port mappings parameter with invalid format: "abc:def" (port not a number)
    mockParams(false, null, null, DEBUG_PORT, "", "abc:def");

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");

    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Setup should throw MobileHarnessException with CDP_PARAMETER_ERROR
    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PARAMETER_ERROR);
    assertThat(exception).hasMessageThat().contains("Invalid reverse port mapping format");
  }

  @Test
  public void setUp_webView_withChromeFlags_hostIoFailed_throwsException() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT, "--disable-gpu", "");
    when(testInfo.getTmpFileDir()).thenReturn("/invalid/directory/path/nonexistent");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_FLAG_SETUP_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to create temporary Chrome flag file on host");
  }

  @Test
  public void setUp_webView_withChromeFlags_pushFailed_throwsException() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT, "--disable-gpu", "");

    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);
    doNothing().when(processUtil).startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);
    when(processUtil.stopApplication(any(UtilArgs.class), eq(PACKAGE_NAME)))
        .thenReturn(ImmutableList.of());
    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME))).thenReturn("12345");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock fileUtil push to throw MobileHarnessException
    when(fileUtil.push(
            eq(DEVICE_ID),
            any(Integer.class),
            anyString(),
            eq("/data/local/tmp/webview-command-line")))
        .thenThrow(
            new MobileHarnessException(ExtErrorId.CDP_PORT_FORWARD_ERROR, "Mock push failure"));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_FORWARD_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to push or set permissions for Chrome/WebView flags");
  }

  @Test
  public void setUp_browser_withChromeFlags_chmodFailed_throwsException() throws Exception {
    mockParams(false, null, null, DEBUG_PORT, "--disable-gpu", "");

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(processUtil.stopApplication(any(UtilArgs.class), eq("com.android.chrome")))
        .thenReturn(ImmutableList.of());
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock push succeeds
    when(fileUtil.push(
            eq(DEVICE_ID),
            any(Integer.class),
            anyString(),
            eq("/data/local/tmp/chrome-command-line")))
        .thenReturn("");

    // Mock chmod fails
    when(adb.runShell(eq(DEVICE_ID), eq("chmod 666 /data/local/tmp/chrome-command-line")))
        .thenThrow(
            new MobileHarnessException(ExtErrorId.CDP_PORT_FORWARD_ERROR, "Mock chmod failure"));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_FORWARD_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to push or set permissions for Chrome/WebView flags");
  }

  @Test
  public void setUp_webView_debugPortPickerFailed_throwsException() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, 0);

    // Stub pickUnusedPort helper to throw IOException
    doThrow(new IOException("Mock port conflict")).when(decorator).pickUnusedPort();

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_ALLOCATION_ERROR);
    assertThat(exception).hasMessageThat().contains("Failed to pick an unused dynamic port");
  }

  @Test
  public void setUp_reversePortForwarding_portPickerFailed_throwsException() throws Exception {
    mockParams(false, null, null, DEBUG_PORT, "", "0:8080");

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Stub pickUnusedPort helper to throw IOException
    doThrow(new IOException("Mock port conflict")).when(decorator).pickUnusedPort();

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_ALLOCATION_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to pick an unused dynamic port for adb reverse");
  }

  @Test
  public void setUp_browser_chromedriverPortPickerFailed_throwsException() throws Exception {
    mockParams(false, null, null, DEBUG_PORT);
    // Mock Files object to return fake chromedriver path
    com.google.wireless.qa.mobileharness.shared.model.job.in.Files files =
        mock(com.google.wireless.qa.mobileharness.shared.model.job.in.Files.class);
    when(files.isTagNotEmpty(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn(true);
    when(files.getSingle(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn("/path/to/chromedriver");
    when(jobInfo.files()).thenReturn(files);

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Stub pickUnusedPort helper to throw IOException during ChromeDriver port selection
    doThrow(new IOException("Mock chromedriver port conflict")).when(decorator).pickUnusedPort();

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_ALLOCATION_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to pick an unused dynamic port for ChromeDriver");
  }

  @Test
  public void setUp_browser_chromedriverStartFailed_throwsException() throws Exception {
    mockParams(false, null, null, DEBUG_PORT);
    com.google.wireless.qa.mobileharness.shared.model.job.in.Files files =
        mock(com.google.wireless.qa.mobileharness.shared.model.job.in.Files.class);
    when(files.isTagNotEmpty(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn(true);
    when(files.getSingle(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn("/path/to/chromedriver");
    when(jobInfo.files()).thenReturn(files);

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // pickUnusedPort succeeds for chromedriverPort
    doReturn(5555).when(decorator).pickUnusedPort();

    // Stub commandExecutor.start to throw CommandStartException (mocked public class)
    when(commandExecutor.start(any(Command.class))).thenThrow(mock(CommandStartException.class));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_CHROMEDRIVER_ERROR);
    assertThat(exception).hasMessageThat().contains("Failed to start ChromeDriver");
  }

  @Test
  public void setUp_browser_chromedriverBindFailed_throwsException() throws Exception {
    mockParams(false, null, null, DEBUG_PORT);
    com.google.wireless.qa.mobileharness.shared.model.job.in.Files files =
        mock(com.google.wireless.qa.mobileharness.shared.model.job.in.Files.class);
    when(files.isTagNotEmpty(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn(true);
    when(files.getSingle(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn("/path/to/chromedriver");
    when(jobInfo.files()).thenReturn(files);

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // pickUnusedPort succeeds for chromedriverPort
    doReturn(5555).when(decorator).pickUnusedPort();

    // ChromeDriver process succeeds starting
    CommandProcess cmdProcess = mock(CommandProcess.class);
    when(commandExecutor.start(any(Command.class))).thenReturn(cmdProcess);

    // Socket bind check fails (waitForPort retry timeout)
    doThrow(new MobileHarnessException(ExtErrorId.CDP_CHROMEDRIVER_ERROR, "Mock bind fail"))
        .when(decorator)
        .waitForPort(eq(5555), any(Duration.class));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_CHROMEDRIVER_ERROR);
    assertThat(exception).hasMessageThat().contains("ChromeDriver failed to bind to port");
  }

  @Test
  public void tearDown_chromeCleanup_success() throws Exception {
    CommandProcess chromedriverProcess = mock(CommandProcess.class);
    decorator.setChromedriverProcess(chromedriverProcess);
    decorator.setPort(DEBUG_PORT);
    decorator.setIsWebViewTest(false);

    decorator.tearDown(TeardownContext.create(testInfo, null, null));

    verify(chromedriverProcess).kill();
    verify(adbUtil).removeTcpPortForward(DEVICE_ID, DEBUG_PORT);
    verify(processUtil)
        .stopApplication(
            eq(UtilArgs.builder().setSerial(DEVICE_ID).build()), eq("com.android.chrome"));
  }

  @Test
  public void tearDown_webViewCleanup_success() throws Exception {
    CommandProcess chromedriverProcess = mock(CommandProcess.class);
    decorator.setChromedriverProcess(chromedriverProcess);
    decorator.setPort(DEBUG_PORT);
    decorator.setIsWebViewTest(true);
    decorator.setPackageName(PACKAGE_NAME);
    decorator.addResolvedDevicePort("8081");
    decorator.addResolvedDevicePort("8082");

    decorator.tearDown(TeardownContext.create(testInfo, null, null));

    verify(chromedriverProcess).kill();
    verify(adbUtil).removeTcpPortForward(DEVICE_ID, DEBUG_PORT);
    verify(adbUtil).removeReverseTcpPort(DEVICE_ID, 8081);
    verify(adbUtil).removeReverseTcpPort(DEVICE_ID, 8082);
    verify(processUtil)
        .stopApplication(eq(UtilArgs.builder().setSerial(DEVICE_ID).build()), eq(PACKAGE_NAME));
  }

  @Test
  public void tearDown_cleanupStepsThrowExceptions_logsWarnings() throws Exception {
    CommandProcess chromedriverProcess = mock(CommandProcess.class);
    decorator.setChromedriverProcess(chromedriverProcess);
    decorator.setPort(DEBUG_PORT);
    decorator.setIsWebViewTest(true);
    decorator.setPackageName(PACKAGE_NAME);
    decorator.addResolvedDevicePort("8081");

    // Mock adbUtil to throw MobileHarnessException during TCP forward removal
    doThrow(
            new MobileHarnessException(
                ExtErrorId.CDP_PORT_FORWARD_ERROR, "Mock TCP remove failure"))
        .when(adbUtil)
        .removeTcpPortForward(DEVICE_ID, DEBUG_PORT);

    // Mock adbUtil to throw MobileHarnessException during reverse port removal
    doThrow(
            new MobileHarnessException(
                ExtErrorId.CDP_PORT_FORWARD_ERROR, "Mock reverse remove failure"))
        .when(adbUtil)
        .removeReverseTcpPort(DEVICE_ID, 8081);

    // Mock processUtil to throw MobileHarnessException during force-stop app
    doThrow(new MobileHarnessException(ExtErrorId.CDP_APP_LAUNCH_ERROR, "Mock force-stop failure"))
        .when(processUtil)
        .stopApplication(any(UtilArgs.class), eq(PACKAGE_NAME));

    decorator.tearDown(TeardownContext.create(testInfo, null, null));

    // Verify all steps were still attempted despite earlier steps throwing exceptions
    verify(chromedriverProcess).kill();
    verify(adbUtil).removeTcpPortForward(DEVICE_ID, DEBUG_PORT);
    verify(adbUtil).removeReverseTcpPort(DEVICE_ID, 8081);
    verify(processUtil).stopApplication(any(UtilArgs.class), eq(PACKAGE_NAME));

    // Verify warnings were logged for the failures (3 catch blocks)
    verify(log, times(3)).atWarning();
  }

  @Test
  public void waitForPort_portOpen_returnsSuccessfully() throws Exception {
    ServerSocket serverSocket = new ServerSocket(0); // dynamically binds to a free port
    int port = serverSocket.getLocalPort();

    Thread serverThread =
        new Thread(
            () -> {
              try (Socket socket = serverSocket.accept()) {
                // auto-closes
              } catch (IOException e) {
                // ignore
              } finally {
                try {
                  serverSocket.close();
                } catch (IOException e) {
                  // ignore
                }
              }
            });
    serverThread.start();

    try {
      // Call the real waitForPort method
      decorator.waitForPort(port, Duration.ofSeconds(2));
    } finally {
      serverThread.interrupt();
      if (!serverSocket.isClosed()) {
        serverSocket.close();
      }
    }
  }

  @Test
  public void waitForPort_portClosed_throwsException() throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }

    int finalPort = port;
    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> decorator.waitForPort(finalPort, Duration.ofMillis(200)));

    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_CHROMEDRIVER_ERROR);
    assertThat(exception).hasMessageThat().contains("Timed out waiting for port");
  }

  @Test
  public void constructor_default_injectsSuccessfully() {
    AndroidCdpDecorator defaultDecorator =
        new AndroidCdpDecorator(decoratedDriver, testInfo, device);
    assertThat(defaultDecorator).isNotNull();
  }

  @Test
  public void pickUnusedPort_returnsFreePort() throws Exception {
    int freePort = decorator.pickUnusedPort();
    assertThat(freePort).isGreaterThan(0);
  }

  @Test
  public void setUp_webView_withChromeFlags_stopAppFailed_logsWarning() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT, "--disable-gpu", "");
    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);
    doNothing().when(processUtil).startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);

    // Mock stopApplication to throw MobileHarnessException
    when(processUtil.stopApplication(any(UtilArgs.class), eq(PACKAGE_NAME)))
        .thenThrow(
            new MobileHarnessException(ExtErrorId.CDP_APP_LAUNCH_ERROR, "Mock stop app failed"));

    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME))).thenReturn("12345");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Stub fileUtil push to succeed
    when(fileUtil.push(eq(DEVICE_ID), any(Integer.class), anyString(), anyString())).thenReturn("");

    decorator.setUp(setupContext);

    // Verify stopApplication was called
    verify(processUtil).stopApplication(any(UtilArgs.class), eq(PACKAGE_NAME));
    // Verify it logged the warning
    verify(log).atWarning();
  }

  @Test
  public void setUp_webView_withChromeFlags_deleteTempFileFailed_logsWarning() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT, "--disable-gpu", "");
    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);
    doNothing().when(processUtil).startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);
    when(processUtil.stopApplication(any(UtilArgs.class), eq(PACKAGE_NAME)))
        .thenReturn(ImmutableList.of());
    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME))).thenReturn("12345");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Create a temp directory for this test
    File tempDir = java.nio.file.Files.createTempDirectory("cdp_test_temp").toFile();
    when(testInfo.getTmpFileDir()).thenReturn(tempDir.getAbsolutePath());

    // Mock fileUtil push to swap the tempFile with a non-empty directory
    when(fileUtil.push(
            eq(DEVICE_ID),
            any(Integer.class),
            anyString(),
            eq("/data/local/tmp/webview-command-line")))
        .thenAnswer(
            invocation -> {
              File[] files = tempDir.listFiles();
              if (files != null) {
                for (File f : files) {
                  if (f.getName().startsWith("chrome_flags_") && f.getName().endsWith(".txt")) {
                    f.delete();
                    f.mkdir();
                    new File(f, "dummy").createNewFile();
                    break;
                  }
                }
              }
              return "";
            });

    try {
      decorator.setUp(setupContext);
      // Verify warning was logged
      verify(log).atWarning();
      verify(loggingApi).log(eq("Failed to delete temp file: %s"), any());
    } finally {
      File[] files = tempDir.listFiles();
      if (files != null) {
        for (File f : files) {
          if (f.isDirectory()) {
            File[] subFiles = f.listFiles();
            if (subFiles != null) {
              for (File sf : subFiles) {
                sf.delete();
              }
            }
          }
          f.delete();
        }
      }
      tempDir.delete();
    }
  }

  @Test
  public void setUp_webView_missingPackageName_throwsException() throws Exception {
    mockParams(true, null, ACTIVITY_NAME, DEBUG_PORT);

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PARAMETER_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Parameter package_name is required for WebView tests");
  }

  @Test
  public void setUp_webView_resolveDefaultActivityFailed_throwsException() throws Exception {
    mockParams(true, PACKAGE_NAME, null, DEBUG_PORT);

    // Mock resolveDefaultActivity to throw MobileHarnessException
    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME))
        .thenThrow(
            new MobileHarnessException(
                ExtErrorId.CDP_APP_LAUNCH_ERROR, "Mock resolve default activity failed"));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_APP_LAUNCH_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to resolve default launch activity for package");
  }

  @Test
  public void setUp_webView_startApplicationFailed_throwsException() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT);
    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);

    // Mock startApplication to throw MobileHarnessException
    doThrow(
            new MobileHarnessException(
                ExtErrorId.CDP_APP_LAUNCH_ERROR, "Mock start application failed"))
        .when(processUtil)
        .startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_APP_LAUNCH_ERROR);
    assertThat(exception).hasMessageThat().contains("Failed to launch WebView app");
  }

  @Test
  public void setUp_browser_startApplicationFailed_throwsException() throws Exception {
    mockParams(false, null, null, DEBUG_PORT);
    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");

    // Mock startApplication to throw MobileHarnessException
    doThrow(new MobileHarnessException(ExtErrorId.CDP_APP_LAUNCH_ERROR, "Mock start Chrome failed"))
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_APP_LAUNCH_ERROR);
    assertThat(exception).hasMessageThat().contains("Failed to launch Chrome browser");
  }

  @Test
  public void setUp_socketReadiness_interrupted_logsWarning() throws Exception {
    mockParams(false, null, null, DEBUG_PORT);
    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");

    // Mock adb runShell to throw InterruptedException (which gets wrapped in RetryException by
    // RetryingCallable)
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenThrow(new InterruptedException("Mock interrupted exception"));

    decorator.setUp(setupContext);

    // Verify thread interrupted status is set
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    // Clear interrupted status so other tests are not affected
    Thread.interrupted();

    // Verify warning was logged
    verify(log).atWarning();
    verify(loggingApi)
        .log(eq("Interrupted while polling for Chrome DevTools remote socket: %s"), any());
  }

  @Test
  public void setUp_webView_resolvePidFailed_logsWarning() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT);
    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);
    doNothing().when(processUtil).startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);

    // First call to getProcessId (startup verification loop) succeeds
    // Second call to getProcessId (PID resolution for socket forwarding) throws
    // MobileHarnessException
    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME)))
        .thenReturn("12345") // first call
        .thenThrow(
            new MobileHarnessException(
                ExtErrorId.CDP_APP_LAUNCH_ERROR, "Mock PID resolve failed")); // second call

    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("webview_devtools_remote_12345");

    decorator.setUp(setupContext);

    // Verify warning logged for WebView PID resolution failure
    verify(log).atWarning();
    verify(loggingApi).log(eq("Failed to resolve WebView PID: %s"), any());
    verify(loggingApi).log(eq("Resolved WebView PID from active socket: %s"), eq("12345"));

    // Verify forwardTcpPort was called with parsed PID
    verify(adbUtil)
        .forwardTcpPort(DEVICE_ID, DEBUG_PORT, "localabstract:webview_devtools_remote_12345");
  }

  @Test
  public void setUp_webView_resolvePidFailed_parseSocketFailed_throwsException() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT);
    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);
    doNothing().when(processUtil).startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);

    // getProcessId fails on second call
    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME)))
        .thenReturn("12345")
        .thenThrow(
            new MobileHarnessException(ExtErrorId.CDP_APP_LAUNCH_ERROR, "Mock PID resolve failed"));

    // Socket output doesn't match webview pattern
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_FORWARD_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to set up adb WebView port forwarding: WebView PID is null.");
  }

  @Test
  public void setUp_webView_forwardTcpPortFailed_throwsException() throws Exception {
    mockParams(true, PACKAGE_NAME, ACTIVITY_NAME, DEBUG_PORT);
    when(processUtil.resolveDefaultActivity(DEVICE_ID, PACKAGE_NAME)).thenReturn(ACTIVITY_NAME);
    doNothing().when(processUtil).startApplication(DEVICE_ID, PACKAGE_NAME, ACTIVITY_NAME);
    when(processUtil.getProcessId(any(UtilArgs.class), eq(PACKAGE_NAME))).thenReturn("12345");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock adbUtil to throw MobileHarnessException during TCP forward
    doThrow(
            new MobileHarnessException(
                ExtErrorId.CDP_PORT_FORWARD_ERROR, "Mock forward TCP failed"))
        .when(adbUtil)
        .forwardTcpPort(DEVICE_ID, DEBUG_PORT, "localabstract:webview_devtools_remote_12345");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_FORWARD_ERROR);
    assertThat(exception).hasMessageThat().contains("Failed to set up adb WebView port forwarding");
  }

  @Test
  public void setUp_browser_forwardTcpPortFailed_throwsException() throws Exception {
    mockParams(false, null, null, DEBUG_PORT);
    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock adbUtil to throw MobileHarnessException during TCP forward
    doThrow(
            new MobileHarnessException(
                ExtErrorId.CDP_PORT_FORWARD_ERROR, "Mock forward TCP failed"))
        .when(adbUtil)
        .forwardTcpPort(DEVICE_ID, DEBUG_PORT, "localabstract:chrome_devtools_remote");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_FORWARD_ERROR);
    assertThat(exception).hasMessageThat().contains("Failed to set up adb Chrome port forwarding");
  }

  @Test
  public void setUp_reversePortForwarding_adbReverseFailed_throwsException() throws Exception {
    // browser target, reverse port mapping
    mockParams(false, null, null, DEBUG_PORT, "", "8081:8081");
    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock reverseTcpPort to throw MobileHarnessException
    doThrow(
            new MobileHarnessException(
                ExtErrorId.CDP_PORT_FORWARD_ERROR, "Mock reverse TCP failed"))
        .when(adbUtil)
        .reverseTcpPort(DEVICE_ID, 8081, 8081);

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.CDP_PORT_FORWARD_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to set up adb reverse port forwarding for mapping: 8081:8081");
  }

  @Test
  public void setUp_browser_withChromedriver_stdoutCallback_logsToTestInfo() throws Exception {
    mockParams(false, null, null, DEBUG_PORT);
    // Mock Files object to return fake chromedriver path
    com.google.wireless.qa.mobileharness.shared.model.job.in.Files files =
        mock(com.google.wireless.qa.mobileharness.shared.model.job.in.Files.class);
    when(files.isTagNotEmpty(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn(true);
    when(files.getSingle(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn("/path/to/chromedriver");
    when(jobInfo.files()).thenReturn(files);

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock dynamic ports picking
    doReturn(5555).when(decorator).pickUnusedPort();

    // Mock commandExecutor to capture the started ChromeDriver process command
    CommandProcess cmdProcess = mock(CommandProcess.class);
    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    when(commandExecutor.start(commandCaptor.capture())).thenReturn(cmdProcess);

    // Mock socket bind check to succeed
    doNothing().when(decorator).waitForPort(eq(5555), any(Duration.class));

    decorator.setUp(setupContext);

    // Trigger ChromeDriver stdout LineCallback
    Command capturedCommand = commandCaptor.getValue();
    assertThat(capturedCommand.getStdoutLineCallback()).isPresent();
    capturedCommand.getStdoutLineCallback().get().onLine("Mock ChromeDriver stdout line");

    // Verify it logged the ChromeDriver line
    verify(loggingApi, Mockito.atLeastOnce())
        .log(eq("[ChromeDriver] %s"), eq("Mock ChromeDriver stdout line"));
  }

  @Test
  public void setUp_browser_withChromedriver_success() throws Exception {
    mockParams(false, null, null, DEBUG_PORT);
    com.google.wireless.qa.mobileharness.shared.model.job.in.Files files =
        mock(com.google.wireless.qa.mobileharness.shared.model.job.in.Files.class);
    when(files.isTagNotEmpty(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn(true);
    when(files.getSingle(AndroidCdpDecorator.TAG_CHROMEDRIVER)).thenReturn("/path/to/chromedriver");
    when(jobInfo.files()).thenReturn(files);

    when(processUtil.resolveDefaultActivity(DEVICE_ID, "com.android.chrome"))
        .thenReturn("ChromeActivity");
    doNothing()
        .when(processUtil)
        .startApplication(DEVICE_ID, "com.android.chrome", "ChromeActivity");
    when(adb.runShell(eq(DEVICE_ID), anyString(), any(Duration.class)))
        .thenReturn("chrome_devtools_remote");

    // Mock dynamic ports picking
    doReturn(5555).when(decorator).pickUnusedPort();

    // Mock ChromeDriver command execution succeeds
    CommandProcess cmdProcess = mock(CommandProcess.class);
    when(commandExecutor.start(any(Command.class))).thenReturn(cmdProcess);

    // Mock socket bind check to succeed
    doNothing().when(decorator).waitForPort(eq(5555), any(Duration.class));

    decorator.setUp(setupContext);

    // Verify context variables injected
    verify(properties).add("SELENIUM_ADDRESS", "http://127.0.0.1:5555");
    verify(properties).add("seleniumAddress", "http://127.0.0.1:5555");
    verify(properties).add("env_var_SELENIUM_ADDRESS", "http://127.0.0.1:5555");
  }

  private Files mockFiles() {
    return new Files();
  }
}
