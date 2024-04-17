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

package com.google.devtools.deviceinfra.shared.logging;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.shared.logging.controller.LogEntryUploadManager;
import com.google.devtools.deviceinfra.shared.logging.controller.handler.UdclusterLogHandler;
import com.google.devtools.deviceinfra.shared.logging.controller.queue.LogEntryQueue;
import com.google.devtools.deviceinfra.shared.logging.controller.uploader.LogUploader;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link UdclusterHostLogManager}. */
@RunWith(JUnit4.class)
public class UdclusterHostLogManagerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private UdclusterLogHandler udclusterLogHandler;
  @Mock private LogEntryQueue logEntryQueue;
  @Mock private LogUploader logUploader;
  @Mock private FileHandler fileHandler;

  @Test
  public void init() throws Exception {
    when(logUploader.isEnabled()).thenReturn(true);
    UdclusterHostLogManager udclusterHostLogManager =
        new UdclusterHostLogManager(
            new LogEntryUploadManager(logEntryQueue, logUploader),
            udclusterLogHandler,
            () -> Optional.of(fileHandler));

    udclusterHostLogManager.init();
    Logger rootConfig = getRootLogger();
    Handler[] handlers = rootConfig.getHandlers();
    assertThat(Arrays.stream(handlers).anyMatch(handler -> handler instanceof UdclusterLogHandler))
        .isTrue();
    assertThat(Arrays.stream(handlers).anyMatch(handler -> handler instanceof FileHandler))
        .isTrue();
  }

  private static Logger getRootLogger() {
    return Logger.getLogger("");
  }
}
