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
import com.google.devtools.mobileharness.shared.constant.closeable.MobileHarnessAutoCloseable;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.FileInfoProto.FileInfo;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher.FileTransferWatcher;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import javax.annotation.Nullable;

/** Sender of file transferring. */
public interface FileTransferClient {

  /**
   * Sends files to receiver server.
   *
   * @param fileId ID of file
   * @param tag tag of file
   * @param path path of file to send
   * @param checksum checksum of file to send
   * @param originalPath original path of transferred file
   */
  void sendFile(
      String fileId, String tag, String path, @Nullable String checksum, String originalPath)
      throws MobileHarnessException, InterruptedException;

  boolean isSendable(String path, @Nullable String checksum)
      throws MobileHarnessException, InterruptedException;

  /**
   * Gets file {@code remote} from peer side, and saves it to {@code local}. Both {@code remote} and
   * {@code local} should be relative path.
   *
   * @param remote path of file in peer side
   * @param local path of file in local side
   * @return size of download file
   */
  @CanIgnoreReturnValue
  long downloadFile(String remote, String local)
      throws MobileHarnessException, InterruptedException;

  /**
   * Gets the file information list from peer side.
   *
   * @param dir remote path of directory in peer side
   * @return the file information list
   */
  List<FileInfo> listFiles(String dir) throws MobileHarnessException;

  /**
   * Adds {@code watcher} to watcher list.
   *
   * @return closer of the added {@code watcher} to remove it automatically
   */
  MobileHarnessAutoCloseable addWatcher(FileTransferWatcher watcher);

  /** Shuts down servers. Must be called at the end. */
  void shutdown();
}
