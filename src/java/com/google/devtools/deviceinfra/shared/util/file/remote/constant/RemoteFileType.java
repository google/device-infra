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

package com.google.devtools.deviceinfra.shared.util.file.remote.constant;

/**
 * Remote file types supported as job input files.
 *
 * <p>To add a new support remote file type, please add/extend a FileResolver under
 * java/com/google/devtools/mobileharness/shared/file/resolver/.
 */
public enum RemoteFileType {
  // The files in ATS file server.
  ATS_FILE_SERVER("ats-file-server::"),
  // Google Cloud Storage Uri
  // E.g.,
  // gs://bucket/path@google_cloud_project
  GCS("gs://");

  private final String prefix;

  RemoteFileType(String prefix) {
    this.prefix = prefix;
  }

  public String prefix() {
    return prefix;
  }
}
