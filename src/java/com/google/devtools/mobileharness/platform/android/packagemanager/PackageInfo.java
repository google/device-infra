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

package com.google.devtools.mobileharness.platform.android.packagemanager;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ComparisonChain;

/** Data queried from Android {@code android.content.pm.PackageInfo}. */
@AutoValue
public abstract class PackageInfo implements Comparable<PackageInfo> {
  public abstract String packageName();

  public abstract long versionCode();

  public abstract boolean isApex();

  /** The installed path of the package. For split packages, it is the path to the base apk. */
  public abstract String sourceDir();

  public static Builder builder() {
    return new AutoValue_PackageInfo.Builder().setIsApex(false);
  }

  @Override
  public int compareTo(PackageInfo that) {
    return ComparisonChain.start()
        .compare(this.packageName(), that.packageName())
        .compare(this.versionCode(), that.versionCode())
        .result();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPackageName(String packageName);

    public abstract Builder setVersionCode(long versionCode);

    public abstract Builder setIsApex(boolean isApex);

    public abstract Builder setSourceDir(String sourceDir);

    public abstract PackageInfo build();
  }
}
