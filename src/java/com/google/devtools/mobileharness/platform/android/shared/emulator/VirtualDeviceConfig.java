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

package com.google.devtools.mobileharness.platform.android.shared.emulator;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

/** Configuration for virtual device / CVD hardware and runtime specs. */
@AutoValue
public abstract class VirtualDeviceConfig {

  public static final int DEFAULT_CPUS = 4;
  public static final int DEFAULT_MEMORY_MB = 8192;

  public abstract int cpus();

  public abstract int memoryMb();

  public static Builder builder() {
    return new AutoValue_VirtualDeviceConfig.Builder()
        .setCpus(DEFAULT_CPUS)
        .setMemoryMb(DEFAULT_MEMORY_MB);
  }

  public static VirtualDeviceConfig getDefaultInstance() {
    return builder().build();
  }

  /** Builder for {@link VirtualDeviceConfig}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCpus(int cpus);

    public abstract Builder setMemoryMb(int memoryMb);

    abstract VirtualDeviceConfig autoBuild();

    public VirtualDeviceConfig build() {
      VirtualDeviceConfig config = autoBuild();
      Preconditions.checkArgument(config.cpus() > 0, "cpus must be > 0: %s", config.cpus());
      Preconditions.checkArgument(
          config.memoryMb() > 0, "memoryMb must be > 0: %s", config.memoryMb());
      return config;
    }
  }
}
