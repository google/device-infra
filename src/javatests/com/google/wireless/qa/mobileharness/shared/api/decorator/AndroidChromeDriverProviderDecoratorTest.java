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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidChromeDriverProviderDecorator}. */
@RunWith(JUnit4.class)
public class AndroidChromeDriverProviderDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String DEVICE_ID = "device_123";
  private static final String CHROME_PACKAGE = "com.android.chrome";
  private static final String WEBVIEW_PACKAGE = "com.google.android.webview";

  @Mock private Driver decoratedDriver;
  @Mock private TestInfo testInfo;
  @Mock private Device device;
  @Mock private JobInfo jobInfo;
  @Mock private Params params;
  @Mock private Files files;
  @Mock private Files testFiles;
  @Mock private Properties properties;
  @Mock private Log log;
  @Mock private Log.Api loggingApi;
  @Mock private AndroidPackageManagerUtil packageManagerUtil;
  @Mock private CommandExecutor commandExecutor;
  @Mock private CommandProcess commandProcess;

  private SetupContext setupContext;
  private SystemUtil systemUtil;
  private Path chromedriverBaseDir;
  private AndroidChromeDriverProviderDecorator decorator;

  @Before
  public void setUp() throws Exception {
    chromedriverBaseDir = tempFolder.newFolder("chromedriver").toPath();
    Path v150Dir = chromedriverBaseDir.resolve("linux/v150_0_7871_46");
    java.nio.file.Files.createDirectories(v150Dir);
    java.nio.file.Files.createFile(v150Dir.resolve("chromedriver"));

    Path v151Dir = chromedriverBaseDir.resolve("linux/v151_0_7922_71");
    java.nio.file.Files.createDirectories(v151Dir);
    java.nio.file.Files.createFile(v151Dir.resolve("chromedriver"));

    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.files()).thenReturn(testFiles);
    when(testInfo.properties()).thenReturn(properties);
    when(testFiles.get("chromedriver_binaries")).thenReturn(ImmutableSet.of());
    when(jobInfo.params()).thenReturn(params);
    when(jobInfo.files()).thenReturn(files);
    when(files.get("chromedriver_binaries")).thenReturn(ImmutableSet.of());
    when(testInfo.log()).thenReturn(log);
    when(commandExecutor.start(any())).thenReturn(commandProcess);

    setupContext = SetupContext.create(testInfo);
    systemUtil = new SystemUtil();

    // Mock logs to avoid NPE
    when(log.atInfo()).thenReturn(loggingApi);
    when(log.atWarning()).thenReturn(loggingApi);
    when(loggingApi.alsoTo(any(FluentLogger.class))).thenReturn(loggingApi);
    when(loggingApi.withCause(any(Throwable.class))).thenReturn(loggingApi);
    when(loggingApi.withCause(nullable(Throwable.class))).thenReturn(loggingApi);

    decorator =
        new AndroidChromeDriverProviderDecorator(
            decoratedDriver,
            testInfo,
            device,
            packageManagerUtil,
            systemUtil,
            chromedriverBaseDir,
            commandExecutor) {
          @Override
          void waitForPort(int port, Duration timeout) {
            // No-op in unit tests
          }
        };
  }

  @Test
  public void setUp_stagedChromedriverBinaries_resolvesFromJobFiles() throws Exception {
    String stagedPath = chromedriverBaseDir.resolve("linux/v150_0_7871_46/chromedriver").toString();
    when(files.get("chromedriver_binaries")).thenReturn(ImmutableSet.of(stagedPath));
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");

    SetupResult result = decorator.setUp(setupContext);

    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);

    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(files).add(eq("chromedriver"), pathCaptor.capture());
    assertThat(pathCaptor.getValue()).contains("v150_0_7871_46/chromedriver");
  }

  @Test
  public void setUp_chromeBrowser_resolvesAndStagesCompatibleDriver() throws Exception {
    // Expecting Chrome package by default
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    // Mock package version name
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");

    SetupResult result = decorator.setUp(setupContext);

    // Verify it returned continue decorated
    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);

    // Capture files.add calls to verify staged ChromeDriver path
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(files).add(eq("chromedriver"), pathCaptor.capture());
    String resolvedPath = pathCaptor.getValue();

    // Verify staged path points to correct major version dir
    assertThat(resolvedPath).contains("linux/v150_0_7871_46");
    assertThat(resolvedPath).endsWith("chromedriver");
  }

  @Test
  public void setUp_clientPluginAttachedFile_resolvesFromTestFiles() throws Exception {
    String clientAttachedPath =
        chromedriverBaseDir.resolve("linux/v150_0_7871_46/chromedriver").toString();
    when(testFiles.get("chromedriver_binaries")).thenReturn(ImmutableSet.of(clientAttachedPath));
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");

    SetupResult result = decorator.setUp(setupContext);

    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(files).add(eq("chromedriver"), pathCaptor.capture());
    assertThat(pathCaptor.getValue()).isEqualTo(clientAttachedPath);
  }

  @Test
  public void setUp_macOsHost_resolvesMacOsDriver() throws Exception {
    systemUtil.setOsNameForTest("Mac OS X");
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("144.0.7559.3");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND);
    assertThat(exception).hasMessageThat().contains("macos");
  }

  @Test
  public void setUp_webviewTargetPackage_resolvesAndStagesCompatibleDriver() throws Exception {
    // WebView package specified in params
    when(params.get("package_name", "com.android.chrome")).thenReturn(WEBVIEW_PACKAGE);
    // Mock package version name
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, WEBVIEW_PACKAGE))
        .thenReturn("151.0.7922.71");

    SetupResult result = decorator.setUp(setupContext);

    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);

    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(files).add(eq("chromedriver"), pathCaptor.capture());
    String resolvedPath = pathCaptor.getValue();

    assertThat(resolvedPath).contains("linux/v151_0_7922_71");
    assertThat(resolvedPath).endsWith("chromedriver");
  }

  @Test
  public void setUp_webviewTargetType_usesConfiguredWebviewPackage() throws Exception {
    when(params.get("target_type", "browser")).thenReturn("webview");
    when(params.get("webview_package_name", null)).thenReturn(WEBVIEW_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, WEBVIEW_PACKAGE))
        .thenReturn("151.0.7922.71");

    SetupResult result = decorator.setUp(setupContext);

    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(files).add(eq("chromedriver"), pathCaptor.capture());
    assertThat(pathCaptor.getValue()).contains("linux/v151_0_7922_71");
  }

  @Test
  public void setUp_webviewTargetTypeDefaultPackageInstalled_usesDefaultWebviewPackage()
      throws Exception {
    when(params.get("target_type", "browser")).thenReturn("webview");
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, WEBVIEW_PACKAGE))
        .thenReturn("151.0.7922.71");

    SetupResult result = decorator.setUp(setupContext);

    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(files).add(eq("chromedriver"), pathCaptor.capture());
    assertThat(pathCaptor.getValue()).contains("linux/v151_0_7922_71");
  }

  @Test
  public void setUp_webviewTargetTypeDefaultPackageMissing_fallsBackToChromePackage()
      throws Exception {
    when(params.get("target_type", "browser")).thenReturn("webview");
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, WEBVIEW_PACKAGE))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND,
                "WebView package not found"));
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");

    SetupResult result = decorator.setUp(setupContext);

    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(files).add(eq("chromedriver"), pathCaptor.capture());
    assertThat(pathCaptor.getValue()).contains("linux/v150_0_7871_46");
  }

  @Test
  public void setUp_missingPackage_throwsException() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    // Mock package version throws MobileHarnessException (indicating not installed)
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND,
                "Package not found"));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND);
    assertThat(exception).hasMessageThat().contains("Package not found");
  }

  @Test
  public void setUp_invalidChromeVersion_throwsException() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    // Return an empty version name, which yields an empty major version.
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE)).thenReturn("");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_VERSION_INFO_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to parse Chrome/WebView major version from version name:");
  }

  @Test
  public void setUp_nonNumericChromeVersion_throwsException() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE)).thenReturn("abc.0.0.0");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_VERSION_INFO_ERROR);
    assertThat(exception).hasMessageThat().contains("abc.0.0.0");
  }

  @Test
  public void setUp_nonAsciiDigitChromeVersion_throwsException() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    // Arabic-Indic digits are Unicode digits but not valid ChromeDriver version directories.
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("\u0661\u0665\u0660.0.0.0");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_VERSION_INFO_ERROR);
  }

  @Test
  public void setUp_nullChromeVersion_throwsException() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE)).thenReturn(null);

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_VERSION_INFO_ERROR);
    assertThat(exception).hasMessageThat().contains("null");
  }

  @Test
  public void setUp_platformDirectoryMissing_throwsException() throws Exception {
    Path emptyBaseDir = tempFolder.newFolder("empty_chromedriver").toPath();
    AndroidChromeDriverProviderDecorator decoratorWithEmptyBaseDir =
        new AndroidChromeDriverProviderDecorator(
            decoratedDriver,
            testInfo,
            device,
            packageManagerUtil,
            systemUtil,
            emptyBaseDir,
            commandExecutor) {
          @Override
          void waitForPort(int port, Duration timeout) {
            // No-op in unit tests
          }
        };
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> decoratorWithEmptyBaseDir.setUp(setupContext));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND);
    assertThat(exception).hasMessageThat().contains("ChromeDriver directory not found in runfiles");
  }

  @Test
  public void setUp_noCompatibleDriverFound_throwsException() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    // Mock version 999 which does not exist in the checked-in ChromeDriver tree
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE)).thenReturn("999.0.0.0");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND);
    assertThat(exception).hasMessageThat().contains("No compatible ChromeDriver found");
  }

  @Test
  public void setUp_withTestTimer_setsCommandTimeout() throws Exception {
    CountDownTimer mockTimer = mock(CountDownTimer.class);
    when(testInfo.timer()).thenReturn(mockTimer);
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");

    SetupResult result = decorator.setUp(setupContext);

    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);

    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).start(commandCaptor.capture());
    Command executedCommand = commandCaptor.getValue();
    assertThat(executedCommand.getTimeout()).isPresent();
    assertThat(executedCommand.getTimeout().get().getDeadline()).hasValue(mockTimer);
  }

  @Test
  public void tearDown_killsChromedriverProcess() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");

    var unused = decorator.setUp(setupContext);
    decorator.tearDown(TeardownContext.create(testInfo, null, null));

    verify(commandProcess).kill();
  }

  @Test
  public void waitForPort_processExitsUnexpectedly_throwsException() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");
    when(commandProcess.isAlive()).thenReturn(false);

    AndroidChromeDriverProviderDecorator realWaitDecorator = newRealWaitDecorator();

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> realWaitDecorator.setUp(setupContext));
    assertThat(exception.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR);
    verify(commandProcess).kill();
  }

  @Test
  public void tearDown_withoutSetUp_doesNotKillProcess() throws Exception {
    decorator.tearDown(TeardownContext.create(testInfo, null, null));

    verify(commandProcess, never()).kill();
  }

  @Test
  public void setUp_chromedriverFailsToStart_throwsException() throws Exception {
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");
    when(commandExecutor.start(any())).thenThrow(mock(CommandStartException.class));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.setUp(setupContext));

    assertThat(exception.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR);
    assertThat(exception).hasMessageThat().contains("Failed to start ChromeDriver.");
  }

  @Test
  public void waitForPort_portOpen_returnsWithoutThrowing() throws Exception {
    AndroidChromeDriverProviderDecorator realWaitDecorator = newRealWaitDecorator();

    try (ServerSocket serverSocket = new ServerSocket(0)) {
      realWaitDecorator.waitForPort(serverSocket.getLocalPort(), Duration.ofSeconds(5));

      assertThat(serverSocket.isClosed()).isFalse();
    }
  }

  @Test
  public void waitForPort_portNeverOpens_throwsTimeoutException() throws Exception {
    int closedPort;
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      closedPort = serverSocket.getLocalPort();
    }
    AndroidChromeDriverProviderDecorator realWaitDecorator = newRealWaitDecorator();

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> realWaitDecorator.waitForPort(closedPort, Duration.ofMillis(200)));

    assertThat(exception.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains("Timed out waiting for port " + closedPort + " to open.");
  }

  @Test
  public void setUp_ignoresCustomChromedriverAndResolvesByVersion() throws Exception {
    when(files.isTagNotEmpty("chromedriver")).thenReturn(true);
    when(files.getSingle("chromedriver")).thenReturn("/tmp/untrusted_chromedriver");
    when(params.get("chromedriver_path", null)).thenReturn("/tmp/untrusted_chromedriver_param");
    when(params.get("package_name", "com.android.chrome")).thenReturn(CHROME_PACKAGE);
    when(packageManagerUtil.getAppVersionName(DEVICE_ID, CHROME_PACKAGE))
        .thenReturn("150.0.7871.46");

    SetupResult result = decorator.setUp(setupContext);

    assertThat(result.action()).isEqualTo(SetupResult.Action.CONTINUE_DECORATED);
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(files).add(eq("chromedriver"), pathCaptor.capture());
    assertThat(pathCaptor.getValue()).contains("linux/v150_0_7871_46/chromedriver");
    assertThat(pathCaptor.getValue()).doesNotContain("untrusted");
  }

  /** Returns a decorator whose {@code waitForPort} is the real implementation, not a no-op. */
  private AndroidChromeDriverProviderDecorator newRealWaitDecorator() {
    return new AndroidChromeDriverProviderDecorator(
        decoratedDriver,
        testInfo,
        device,
        packageManagerUtil,
        systemUtil,
        chromedriverBaseDir,
        commandExecutor);
  }
}
