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

package com.google.devtools.mobileharness.shared.util.system;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;

/** Memory information of the current process and the machine. */
@AutoValue
public abstract class MemoryInfo {

  public abstract long jvmFreeMemory();

  public abstract long jvmTotalMemory();

  public abstract long jvmMaxMemory();

  public abstract long heapUsedMemory();

  public abstract long freeMemory();

  public abstract long totalMemory();

  @Memoized
  public long jvmUsedMemory() {
    return jvmTotalMemory() - jvmFreeMemory();
  }

  @Memoized
  public long usedMemory() {
    return totalMemory() - freeMemory();
  }

  @Override
  @Memoized
  public String toString() {
    return String.format(
        "jvm_used=%s, jvm_total=%s, jvm_max=%s, heap_used=%s, free=%s, used=%s," + " total=%s",
        StrUtil.getHumanReadableSize(jvmUsedMemory()),
        StrUtil.getHumanReadableSize(jvmTotalMemory()),
        StrUtil.getHumanReadableSize(jvmMaxMemory()),
        StrUtil.getHumanReadableSize(heapUsedMemory()),
        StrUtil.getHumanReadableSize(freeMemory()),
        StrUtil.getHumanReadableSize(usedMemory()),
        StrUtil.getHumanReadableSize(totalMemory()));
  }

  public static MemoryInfo of(
      long jvmFreeMemory,
      long jvmTotalMemory,
      long jvmMaxMemory,
      long heapUsedMemory,
      long freeMemory,
      long totalMemory) {
    return new AutoValue_MemoryInfo(
        jvmFreeMemory, jvmTotalMemory, jvmMaxMemory, heapUsedMemory, freeMemory, totalMemory);
  }
}
