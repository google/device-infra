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

package com.google.devtools.mobileharness.platform.android.media;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Arguments for screenrecord command. */
@AutoValue
public abstract class ScreenRecordArgs {

  static final String COMMAND_NAME = "screenrecord";

  @VisibleForTesting
  public abstract Optional<Integer> bitRate();

  @VisibleForTesting
  public abstract Optional<String> size();

  @VisibleForTesting
  public abstract boolean verbose();

  @VisibleForTesting
  public abstract boolean bugreport();

  abstract String outputFile();

  String toShellCmd() {
    ArrayList<String> args = new ArrayList<>();
    args.add(COMMAND_NAME);
    if (bitRate().isPresent()) {
      args.add("--bit-rate");
      args.add(bitRate().get().toString());
    }

    if (size().isPresent()) {
      args.add("--size");
      args.add(size().get());
    }

    if (verbose()) {
      args.add("--verbose");
    }

    if (bugreport()) {
      args.add("--bugreport");
    }

    args.add(outputFile());

    return String.join(" ", args);
  }

  /**
   * Create a ScreenRecordArgs builder.
   *
   * @param outputFile the output file path on device
   */
  public static Builder builder(String outputFile) {
    return new AutoValue_ScreenRecordArgs.Builder()
        .setOutputFile(outputFile)
        .setVerbose(false)
        .setBugreport(false);
  }

  /** Builder for {@link ScreenRecordArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    private static final Pattern SIZE_PATTERN = Pattern.compile("\\d+x\\d+");
    private static final int MIN_BIT_RATE = 100_000;

    /** Set the video bit rate. */
    public abstract Builder setBitRate(Optional<Integer> value);

    /** Set the video bit rate. */
    public abstract Builder setBitRate(Integer value);

    /** Set the video size. e.g. 1280x720. */
    public abstract Builder setSize(Optional<String> value);

    /** Set the video size. e.g. 1280x720. */
    public abstract Builder setSize(String value);

    /** Set whether to show verbose log. */
    public abstract Builder setVerbose(boolean value);

    /** Set whether to add additional information, such as a timestamp overlay. */
    public abstract Builder setBugreport(boolean value);

    abstract Builder setOutputFile(String value);

    abstract ScreenRecordArgs autoBuild();

    public final ScreenRecordArgs build() {
      ScreenRecordArgs args = autoBuild();
      Preconditions.checkState(!args.bitRate().isPresent() || args.bitRate().get() >= MIN_BIT_RATE);
      Preconditions.checkState(
          !args.size().isPresent() || SIZE_PATTERN.matcher(args.size().get()).matches());
      return args;
    }
    ;
  }
}
