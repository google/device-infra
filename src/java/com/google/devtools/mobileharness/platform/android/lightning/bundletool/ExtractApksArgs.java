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
 * Arguments for {@code bundletool extract-apks}.
 *
 * <p>See https://developer.android.com/tools/bundletool for detailed usage of each argument.
 */
public record ExtractApksArgs(Path apks, Path outputDir, Path deviceSpec, Duration commandTimeout) {

  ImmutableList<String> toBundletoolCommand() {
    return ImmutableList.of(
        "extract-apks",
        "--apks=" + apks,
        "--output-dir=" + outputDir,
        "--device-spec=" + deviceSpec);
  }

  public static Builder builder() {
    return new AutoBuilder_ExtractApksArgs_Builder().setCommandTimeout(Duration.ofMinutes(10));
  }

  /** Builder for {@link ExtractApksArgs}. */
  @AutoBuilder
  public abstract static class Builder {

    /** The required Apk Set file to extract. */
    public abstract Builder setApks(Path apks);

    /** The required output directory to extract the APKs to. */
    public abstract Builder setOutputDir(Path outputDir);

    /** The required device spec file to use for the extraction. */
    public abstract Builder setDeviceSpec(Path deviceSpec);

    /** Timeout for the command (default 10 minutes). */
    public abstract Builder setCommandTimeout(Duration commandTimeout);

    public abstract ExtractApksArgs build();
  }
}
