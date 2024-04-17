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

package com.google.devtools.deviceinfra.shared.logging.controller.handler;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.Stage;
import java.io.File;
import java.util.logging.FileHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link LocalFileHandlerModule}. */
@RunWith(JUnit4.class)
public class LocalFileHandlerModuleTest {
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LocalFileUtil localFileUtil;

  @Test
  public void inject_success() {
    Guice.createInjector(Stage.DEVELOPMENT, new LocalFileHandlerModule(null))
        .getProvider(FileHandler.class);
  }

  @Test
  public void provideLocalFileHandler_logFirDirNotSet() throws Exception {
    LocalFileHandlerModule localFileHandlerModule = new LocalFileHandlerModule(null);

    assertThat(localFileHandlerModule.provideFileHandler(localFileUtil)).isEmpty();
  }

  @Test
  public void provideLocalFileHandler_logFirDirEmpty() {
    LocalFileHandlerModule localFileHandlerModule = new LocalFileHandlerModule("");

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> localFileHandlerModule.provideFileHandler(localFileUtil));
    assertThat(thrown.getErrorId()).isEqualTo(InfraErrorId.LOGGER_CREATE_FILE_HANDLER_ERROR);
  }

  @Test
  public void provideLocalFileHandler_logFirDirInvalid() {
    LocalFileHandlerModule localFileHandlerModule = new LocalFileHandlerModule("log");

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> localFileHandlerModule.provideFileHandler(localFileUtil));
    assertThat(thrown.getErrorId()).isEqualTo(InfraErrorId.LOGGER_CREATE_FILE_HANDLER_ERROR);
  }

  @Test
  public void provideLocalFileHandler_success() throws Exception {
    File logDir = tmpFolder.newFolder("log");
    LocalFileHandlerModule localFileHandlerModule =
        new LocalFileHandlerModule(logDir.getAbsolutePath());

    localFileHandlerModule.provideFileHandler(localFileUtil);
  }
}
