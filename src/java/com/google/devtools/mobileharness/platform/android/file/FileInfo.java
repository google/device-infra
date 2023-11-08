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

package com.google.devtools.mobileharness.platform.android.file;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.util.Optional;

/** An abstract representation of file and directory pathnames on Android device. */
@AutoValue
public abstract class FileInfo {

  /** Path for the file/dir. */
  public abstract String path();

  /** Optional: Type of file/dir. */
  public abstract Optional<FileType> type();

  /** Optional: Actual type of symbolic link file/dir. */
  public abstract Optional<FileType> symlinkType();

  /** Optional: Permissions for the file/dir to user, group and others. */
  public abstract Optional<FilePermissions> permissions();

  public static Builder builder() {
    return new AutoValue_FileInfo.Builder();
  }

  /** Auto value builder for {@link FileInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract FileInfo.Builder setPath(String path);

    public abstract FileInfo.Builder setType(FileType type);

    public abstract FileInfo.Builder setSymlinkType(FileType symlinkType);

    public abstract FileInfo.Builder setPermissions(FilePermissions permissions);

    abstract String path();

    abstract FileInfo autoBuild();

    public FileInfo build() {
      setPath(PathUtil.removeExtraneousSlashes(path()));
      return autoBuild();
    }
  }
}
