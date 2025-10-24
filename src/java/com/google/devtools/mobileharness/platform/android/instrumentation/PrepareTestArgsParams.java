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

package com.google.devtools.mobileharness.platform.android.instrumentation;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import java.util.Optional;

/** Wrapper for arguments used for preparing test args used in AndroidInstrumentationUtil. */
@AutoValue
public abstract class PrepareTestArgsParams {

  /** Serial number for the device. */
  public abstract String serial();

  /** Test args, the map to be propagated to the test. */
  public abstract ImmutableMap<String, String> testArgs();

  /** Device external storage path on the device, like /sdcard/googletest/internal_use/. */
  public abstract Optional<String> deviceExternalStoragePath();

  /** Tmp file directory on the host. */
  public abstract String hostTmpFileDir();

  /** Output channel for logs to the end user. */
  public abstract Log log();

  /** Output channel for errors to the end user. */
  public abstract Warnings warnings();

  /**
   * Whether to force usage of adb push to push test args file to device as shell content write is
   * not working in some cases, for rooted devices adb push should always work.
   */
  public abstract boolean forceAdbPush();

  /** Whether to skip clear media provider for multi user case. */
  public abstract boolean skipClearMediaProviderForMultiUserCase();

  public static Builder builder() {
    return new AutoValue_PrepareTestArgsParams.Builder()
        .setTestArgs(ImmutableMap.of())
        .setForceAdbPush(false)
        .setSkipClearMediaProviderForMultiUserCase(false);
  }

  /** Auto value builder for {@link PrepareTestArgsParams}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setSerial(String serial);

    public abstract Builder setTestArgs(ImmutableMap<String, String> testArgs);

    public abstract Builder setDeviceExternalStoragePath(String deviceExternalStoragePath);

    public abstract Builder setHostTmpFileDir(String hostTmpFileDir);

    public abstract Builder setLog(Log log);

    public abstract Builder setWarnings(Warnings warnings);

    public abstract Builder setForceAdbPush(boolean forceAdbPush);

    public abstract Builder setSkipClearMediaProviderForMultiUserCase(
        boolean skipClearMediaProviderForMultiUserCase);

    public abstract PrepareTestArgsParams build();
  }
}
