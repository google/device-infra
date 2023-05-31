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

package com.google.devtools.deviceaction.common.schemas;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.util.Collection;
import java.util.Optional;

/** Data structure for an Android package. */
@AutoValue
public abstract class AndroidPackage {
  public abstract PackageInfo info();

  /** The archived apks file for installation. */
  public abstract Optional<File> apksFile();

  public abstract ImmutableList<File> files();

  public abstract boolean isSplit();

  public static Builder builder() {
    return new AutoValue_AndroidPackage.Builder().setIsSplit(false).setFiles(ImmutableList.of());
  }

  public abstract Builder toBuilder();

  /** Builder for {@link AndroidPackage}. */
  @AutoValue.Builder
  public abstract static class Builder {
    abstract ImmutableList.Builder<File> filesBuilder();

    public abstract Builder setInfo(PackageInfo value);

    public abstract Builder setApksFile(File value);

    public abstract Builder setFiles(ImmutableList<File> value);

    public abstract Builder setIsSplit(boolean value);

    public abstract AndroidPackage build();

    @CanIgnoreReturnValue
    public Builder addFiles(File... files) {
      filesBuilder().add(files);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addFiles(Collection<File> files) {
      filesBuilder().addAll(files);
      return this;
    }
  }
}
