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

/** Data from Android {@code android.content.pm.ModuleInfo}. */
@AutoValue
public abstract class ModuleInfo implements Comparable<ModuleInfo> {

  /** Public name of this module. */
  public abstract String name();

  /** Package name of the module. */
  public abstract String packageName();

  public static Builder builder() {
    return new AutoValue_ModuleInfo.Builder();
  }

  @Override
  public int compareTo(ModuleInfo that) {
    return ComparisonChain.start()
        .compare(this.name(), that.name())
        .compare(this.packageName(), that.packageName())
        .result();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(String value);

    public abstract Builder setPackageName(String value);

    public abstract ModuleInfo build();
  }
}
