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

package com.google.devtools.mobileharness.platform.android.lightning.bundletool;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Arguments for {@code bundletool get-device-spec}.
 *
 * <p>See https://developer.android.com/tools/bundletool for detailed usage of each argument.
 */
public record GetDeviceSpecArgs(
    Path output, String deviceId, boolean overwrite, Duration commandTimeout) {

  ImmutableList<String> toBundletoolCommand(String adbPath) {
    ImmutableList.Builder<String> args =
        ImmutableList.<String>builder()
            .add("get-device-spec")
            .add("--adb=" + adbPath)
            .add("--output=" + output);
    if (!deviceId.isEmpty()) {
      args.add("--device-id=" + deviceId);
    }
    if (overwrite) {
      args.add("--overwrite");
    }
    return args.build();
  }

  public static Builder builder() {
    return new AutoBuilder_GetDeviceSpecArgs_Builder()
        .setDeviceId("")
        .setOverwrite(false)
        .setCommandTimeout(Duration.ofMinutes(10));
  }

  /** Builder for {@link GetDeviceSpecArgs}. */
  @AutoBuilder
  public abstract static class Builder {

    /** The required output file path for the device spec. Must have {@code .json} extension. */
    public abstract Builder setOutput(Path output);

    /** The device ID to get the device spec for (default empty). */
    public abstract Builder setDeviceId(String deviceId);

    /** Whether to overwrite the output file if it already exists (default false). */
    public abstract Builder setOverwrite(boolean overwrite);

    /** Timeout for the command (default 10 minutes). */
    public abstract Builder setCommandTimeout(Duration commandTimeout);

    public abstract GetDeviceSpecArgs build();
  }
}
