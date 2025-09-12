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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.FileHandlers.Handler;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.TaggedFileMetadataProto.TaggedFileMetadata;
import com.google.wireless.qa.mobileharness.shared.comm.filetransfer.FileCallback;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Handler of receiving tag files. It's an adapter between {@link FileCallback} and {@link Handler}.
 */
public class TaggedFileHandler implements Handler<TaggedFileMetadata> {

  /** Callback of received file. */
  private FileCallback callback;

  public TaggedFileHandler(FileCallback callback) {
    this.callback = callback;
  }

  @Override
  public void onReceived(
      TaggedFileMetadata meta, Path receivedPath, Path originalPath, @Nullable String checksum)
      throws MobileHarnessException, InterruptedException {
    callback.onReceived(
        meta.getFileId(),
        meta.getTag(),
        receivedPath.toString(),
        originalPath.toString(),
        checksum);
  }
}
