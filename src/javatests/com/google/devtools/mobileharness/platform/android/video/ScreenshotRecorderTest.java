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

package com.google.devtools.mobileharness.platform.android.video;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
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
public final class ScreenshotRecorderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private Adb adb;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private Sleeper sleeper;

  private ScreenshotRecorder recorder;

  @Before
  public void setUp() {
    recorder = new ScreenshotRecorder(adb, androidFileUtil, localFileUtil, sleeper);
  }

  @Test
  public void start_makesDirectory() throws Exception {
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("localhost:12345", "fake-test-id", tempFolder.getRoot().toPath(), spec);

    verify(androidFileUtil)
        .makeDirectory("localhost:12345", "/data/local/tmp/mh_screenshots_fake-test-id");
  }

  @Test
  public void stop_pullsAndRemovesFiles() throws Exception {
    Path genDir = tempFolder.getRoot().toPath();

    // Mock localFileUtil.listFilesOrDirs to return some files
    File mockFile1 = tempFolder.newFile("screenshot_00000001.png");
    File mockFile2 = tempFolder.newFile("screenshot_00000002.png");
    when(localFileUtil.listFilesOrDirs(genDir.resolve("screenshots").toString()))
        .thenReturn(new File[] {mockFile1, mockFile2});

    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("device_id", "test_id", genDir, spec);

    List<Path> result = recorder.stop();

    verify(localFileUtil).prepareDir(genDir.resolve("screenshots").toString());
    verify(androidFileUtil)
        .pull(
            "device_id",
            "/data/local/tmp/mh_screenshots_test_id/.",
            genDir.resolve("screenshots").toString());
    verify(androidFileUtil).removeFiles("device_id", "/data/local/tmp/mh_screenshots_test_id");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).toString()).isEqualTo(mockFile1.getPath());
    assertThat(result.get(1).toString()).isEqualTo(mockFile2.getPath());
  }

  @Test
  public void stop_throwsExceptionIfPullFails() throws Exception {
    Path genDir = tempFolder.getRoot().toPath();

    doThrow(mock(MobileHarnessException.class))
        .when(androidFileUtil)
        .pull(anyString(), anyString(), anyString());

    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("device_id", "test_id", genDir, spec);

    assertThrows(MobileHarnessException.class, () -> recorder.stop());
  }

  @Test
  public void stop_throwsExceptionIfRemoveFails() throws Exception {
    Path genDir = tempFolder.getRoot().toPath();

    doThrow(mock(MobileHarnessException.class))
        .when(androidFileUtil)
        .removeFiles(anyString(), anyString());

    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("device_id", "test_id", genDir, spec);

    assertThrows(MobileHarnessException.class, () -> recorder.stop());
  }
}
