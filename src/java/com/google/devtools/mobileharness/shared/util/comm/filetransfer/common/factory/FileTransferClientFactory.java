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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.factory;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.comm.filetransfer.FileTransferClient;

/** Factory of {@link FileTransferClient}. */
public interface FileTransferClientFactory {

  /**
   * Creates a new instance of {@link FileTransferClient} with default {@link
   * FileTransferParameters}.
   */
  default FileTransferClient create() throws MobileHarnessException, InterruptedException {
    return create(FileTransferParameters.DEFAULT);
  }

  /** Creates a new instance of {@link FileTransferClient} with {@code parameters}. */
  FileTransferClient create(FileTransferParameters parameters)
      throws MobileHarnessException, InterruptedException;

  /** Shutdown any shared resources in all created clients. */
  void shutdown();
}
