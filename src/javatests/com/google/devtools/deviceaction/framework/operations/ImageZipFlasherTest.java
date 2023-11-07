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

package com.google.devtools.deviceaction.framework.operations;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.testing.FakeCommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager.Lease;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
public final class ImageZipFlasherTest {

  private static final CommandResult SUCCESS = FakeCommandResult.of("", "", 0);

  private static final CommandResult FAILURE = FakeCommandResult.of("", "", 1);

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private LocalFileUtil mockFileUtil;
  @Mock private ResourceHelper mockHelper;
  @Mock private QuotaManager mockManager;
  @Mock private AndroidPhone mockDevice;
  @Mock private CommandExecutor executor;
  @Mock private Lease lease;

  private File zipFile;

  private File unZippedDir;

  private ImageZipFlasher flasher;

  @Before
  public void setUp() throws Exception {
    zipFile = tmpFolder.newFile("image.zip");
    unZippedDir = tmpFolder.newFolder("targetDir");
    when(mockHelper.getTmpFileDir()).thenReturn(Path.of("tmp"));
    when(mockFileUtil.createTempDir(anyString())).thenReturn(unZippedDir.getAbsolutePath());
    doAnswer(
            invocation -> {
              Files.createFile(unZippedDir.toPath().resolve("script"));
              return null;
            })
        .when(mockFileUtil)
        .unzipFile(zipFile.getAbsolutePath(), unZippedDir.getAbsolutePath());
    when(mockDevice.getUuid()).thenReturn("id");
    when(mockManager.acquire(any(), anyInt())).thenReturn(lease);
    flasher =
        new ImageZipFlasher(
            mockDevice, mockFileUtil, mockHelper, mockManager, executor, Duration.ofMillis(200));
  }

  @Test
  public void flashDevice_success() throws Exception {
    when(executor.exec(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Thread.sleep(1000);
              return SUCCESS;
            });

    flasher.flashDevice(zipFile, "script", Duration.ofSeconds(2));

    verify(mockDevice).waitUntilReady();
  }

  @Test
  public void flashDevice_fail() throws Exception {
    when(executor.exec(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Thread.sleep(1000);
              return FAILURE;
            });

    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> flasher.flashDevice(zipFile, "script", Duration.ofSeconds(2)));
    assertThat(t.getErrorId().name()).isEqualTo("VERIFICATION_FAILED");
    verify(mockDevice, never()).waitUntilReady();
  }

  @Test
  public void flashDevice_acquireQuotaTimeout() throws Exception {
    // 200 millis < 500 millis < timeout (500) + 200 millis
    when(mockManager.acquire(any(), anyInt()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(500);
              return lease;
            });

    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> flasher.flashDevice(zipFile, "script", Duration.ofMillis(500)));
    assertThat(t.getErrorId().name()).isEqualTo("VERIFICATION_FAILED");
    verify(executor, never()).exec(any(Command.class));
    verify(mockDevice, never()).waitUntilReady();
  }

  @Test
  public void flashDevice_acquireQuotaLongerThanTotalTimeout() throws Exception {
    // 1000 millis > timeout (500) + 200 millis
    when(mockManager.acquire(any(), anyInt()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(1000);
              return lease;
            });

    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> flasher.flashDevice(zipFile, "script", Duration.ofMillis(500)));
    assertThat(t.getErrorId().name()).isEqualTo("TIMEOUT");
    verify(executor, never()).exec(any(Command.class));
    verify(mockDevice, never()).waitUntilReady();
  }
}
