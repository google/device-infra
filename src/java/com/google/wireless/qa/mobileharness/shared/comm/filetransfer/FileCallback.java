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

package com.google.wireless.qa.mobileharness.shared.comm.filetransfer;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** Callback when receives a file. */
public interface FileCallback {

  /**
   * Callback when receives a file.
   *
   * @param fileId the ID of the file
   * @param tag file tag
   * @param path file path on the receiver side
   * @param originalPath file path on the sender side
   */
  void onReceived(String fileId, String tag, String path, String originalPath)
      throws MobileHarnessException, InterruptedException;
}
