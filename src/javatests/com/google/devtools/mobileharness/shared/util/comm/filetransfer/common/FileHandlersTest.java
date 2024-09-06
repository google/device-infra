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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.common;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.FileHandlers.Handler;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.FileHandlersTest.FtMeta;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FileHandlers}. */
@RunWith(JUnit4.class)
public class FileHandlersTest {

  @Test
  public void getMetadataClass() throws MobileHarnessException {
    @SuppressWarnings("unchecked")
    Handler<FtMeta> handler = (Handler<FtMeta>) mock(Handler.class);
    assertThrows(MobileHarnessException.class, () -> FileHandlers.getMetadataClass(handler));

    assertThrows(
        MobileHarnessException.class,
        () -> FileHandlers.getMetadataClass((meta, receivedPath, originalPath) -> {}));

    assertThat(
            FileHandlers.getMetadataClass(
                new Handler<FtMeta>() {
                  @Override
                  public void onReceived(FtMeta meta, Path receivedPath, Path originalPath)
                      throws MobileHarnessException, InterruptedException {}
                }))
        .isEqualTo(FtMeta.class);
  }
}
