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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.client;

import com.google.auto.value.AutoValue;

/**
 * A wrapper class of file operation status.
 *
 * <p>File operations include uploading and downloading.
 */
@AutoValue
public abstract class FileOperationStatus {
  /** Whether the file operation is finished. */
  abstract boolean isFinished();

  /**
   * The size of the file in bytes.
   *
   * <p>It is only valid when {@link #isFinished()} is true.
   */
  abstract long fileSize();

  public static FileOperationStatus create(boolean isFinished, long fileSize) {
    return new AutoValue_FileOperationStatus(isFinished, fileSize);
  }
}
