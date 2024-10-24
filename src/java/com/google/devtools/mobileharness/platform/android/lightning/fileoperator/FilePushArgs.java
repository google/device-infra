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

package com.google.devtools.mobileharness.platform.android.lightning.fileoperator;

import com.google.auto.value.AutoValue;
import java.time.Duration;
import java.util.Optional;

/** Wrapper for arguments used for pushing file/dir to device. */
@AutoValue
public abstract class FilePushArgs {

  /** The path of source file/dir on host machine being pushed to device. */
  public abstract String srcPathOnHost();

  /** The destination path of pushed file/dir on device. */
  public abstract String desPathOnDevice();

  /** Timeout for pushing file/dir to device. */
  public abstract Optional<Duration> pushTimeout();

  /**
   * Whether to create the destination dir (including parent dirs) if it doesn't exist.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>src=/host/file, des=/device/path_not_exist/: create dir /device/path_not_exist/, and the
   *       source file will be copied as /device/path_not_exist/file
   *   <li>src=/host/file, des=/device/path_not_exist: won't create a direcotry, and the source file
   *       will be copied as /device/path_not_exist.
   *   <li>src=/host/file, des=/device/path_not_exist/not_exist/: create dir
   *       /device/path_not_exist/not_exist/, and the source file will be copied as
   *       /device/path_not_exist/not_exist/file
   *   <li>src=/host/file, des=/device/path_not_exist/not_exist/filename: create dir
   *       /device/path_not_exist/not_exist/, and the source file will be copied as
   *       /device/path_not_exist/not_exist/filename
   *   <li>src=/host/dir or /host/dir/, des=/device/path_not_exist or des=/device/path_not_exist/:
   *       won't create a direcotry, and the source files under /host/dir, supposing to be
   *       /host/dir/file1 and /host/dir/file2, will be copied as /device/path_not_exist/file1 and
   *       /device/path_not_exist/file2, which follows "adb push" default behavior.
   * </ul>
   */
  public abstract Optional<Boolean> prepareDesDirWhenSrcIsFile();

  public static Builder builder() {
    return new AutoValue_FilePushArgs.Builder();
  }

  /** Auto value builder for {@link FilePushArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setSrcPathOnHost(String srcPathOnHost);

    public abstract Builder setDesPathOnDevice(String desPathOnDevice);

    public abstract Builder setPushTimeout(Duration pushTimeout);

    public abstract Builder setPrepareDesDirWhenSrcIsFile(Boolean prepareDesDirWhenSrcIsFile);

    public abstract FilePushArgs build();
  }
}
