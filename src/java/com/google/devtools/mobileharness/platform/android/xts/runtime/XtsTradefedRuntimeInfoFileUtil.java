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

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
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

/** A file utility to read/write {@code XtsTradefedRuntimeInfo} for XTS Tradefed tests. */
public class XtsTradefedRuntimeInfoFileUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public void writeInfo(Path infoPath, XtsTradefedRuntimeInfo info) throws IOException {
    Path lockFilePath = prepareLockFile(infoPath);

    logger.atInfo().log("Acquiring file lock to write XtsTradefedRuntimeInfo to %s", infoPath);
    try (FileChannel lockFile = new FileOutputStream(lockFilePath.toString()).getChannel();
        FileLock ignored = lockFile.lock()) {
      Files.writeString(infoPath, info.encodeToString());
    } catch (IOException e) {
      logger.atWarning().log("Failed to write XtsTradefedRuntimeInfo to %s", infoPath);
      throw e;
    }
  }

  /**
   * Returns empty if the file doesn't exist or the file is unchanged since {@code
   * lastModifiledTime}.
   */
  public Optional<XtsTradefedRuntimeInfoFileDetail> readInfo(
      Path infoPath, @Nullable Instant lastModifiledTime) throws IOException {

    if (!Files.exists(infoPath)) {
      return Optional.empty();
    }

    if (lastModifiledTime != null
        && !Files.getLastModifiedTime(infoPath).toInstant().isAfter(lastModifiledTime)) {
      return Optional.empty();
    }

    Path lockFilePath = prepareLockFile(infoPath);
    logger.atInfo().log("Acquiring file lock to read XtsTradefedRuntimeInfo from %s", infoPath);
    try (FileChannel lockFile = new FileOutputStream(lockFilePath.toString()).getChannel();
        FileLock ignored = lockFile.lock()) {
      String content = Files.readString(infoPath);
      Instant fileLastModifiedTime = Files.getLastModifiedTime(infoPath).toInstant();
      return Optional.of(
          XtsTradefedRuntimeInfoFileDetail.of(
              XtsTradefedRuntimeInfo.decodeFromString(content), fileLastModifiedTime));
    } catch (IOException e) {
      logger.atWarning().log("Failed to read XtsTradefedRuntimeInfo from %s", infoPath);
      throw e;
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

  @AutoValue
  abstract static class XtsTradefedRuntimeInfoFileDetail {
    abstract XtsTradefedRuntimeInfo info();

    /** Last modified time of the file. Empty when the file doesn't exist. */
    abstract Instant lastModifiedTime();

    private static XtsTradefedRuntimeInfoFileDetail of(
        XtsTradefedRuntimeInfo info, Instant lastModifiedTime) {
      return new AutoValue_XtsTradefedRuntimeInfoFileUtil_XtsTradefedRuntimeInfoFileDetail(
          info, lastModifiedTime);
    }
  }
}
