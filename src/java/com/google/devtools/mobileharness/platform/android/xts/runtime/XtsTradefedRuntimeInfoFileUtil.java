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

package com.google.devtools.mobileharness.platform.android.xts.runtime;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/** A file utility to read/write {@link XtsTradefedRuntimeInfo} for XTS Tradefed tests. */
public class XtsTradefedRuntimeInfoFileUtil {

  public void writeInfo(Path infoPath, XtsTradefedRuntimeInfo info) throws IOException {
    Path lockFilePath = prepareLockFile(infoPath);

    try (FileChannel lockFile = new FileOutputStream(lockFilePath.toString()).getChannel();
        FileLock ignored = lockFile.lock()) {
      Files.writeString(infoPath, info.encodeToString());
    }
  }

  /**
   * Returns empty if the file doesn't exist or the file is unchanged since {@code
   * lastModifiedTime}.
   */
  public Optional<XtsTradefedRuntimeInfoFileDetail> readInfo(
      Path runtimeInfoFilePath, @Nullable Instant lastModifiedTime) throws IOException {
    if (!Files.exists(runtimeInfoFilePath)) {
      return Optional.empty();
    }

    if (lastModifiedTime != null
        && !Files.getLastModifiedTime(runtimeInfoFilePath).toInstant().isAfter(lastModifiedTime)) {
      return Optional.empty();
    }

    Path lockFilePath = prepareLockFile(runtimeInfoFilePath);

    try (FileChannel lockFile = new FileOutputStream(lockFilePath.toString()).getChannel();
        FileLock ignored = lockFile.lock()) {
      String content = Files.readString(runtimeInfoFilePath);
      Instant fileLastModifiedTime = Files.getLastModifiedTime(runtimeInfoFilePath).toInstant();
      return Optional.of(
          new XtsTradefedRuntimeInfoFileDetail(
              XtsTradefedRuntimeInfo.decodeFromString(content), fileLastModifiedTime));
    }
  }

  private Path prepareLockFile(Path infoPath) throws IOException {
    Path lockFilePath = Path.of(infoPath + ".lck");
    if (!Files.exists(lockFilePath)) {
      Files.createFile(lockFilePath);
    }
    Files.setLastModifiedTime(lockFilePath, FileTime.fromMillis(System.currentTimeMillis()));
    return lockFilePath;
  }

  /** Details of runtime info file. */
  public static class XtsTradefedRuntimeInfoFileDetail {

    private final XtsTradefedRuntimeInfo runtimeInfo;
    private final Instant lastModifiedTime;

    public XtsTradefedRuntimeInfoFileDetail(
        XtsTradefedRuntimeInfo runtimeInfo, Instant lastModifiedTime) {
      this.runtimeInfo = runtimeInfo;
      this.lastModifiedTime = lastModifiedTime;
    }

    public XtsTradefedRuntimeInfo runtimeInfo() {
      return runtimeInfo;
    }

    /** Last modified time of the file. Empty when the file doesn't exist. */
    public Instant lastModifiedTime() {
      return lastModifiedTime;
    }
  }
}
