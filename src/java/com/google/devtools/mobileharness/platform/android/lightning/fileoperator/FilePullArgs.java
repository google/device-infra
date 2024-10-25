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

package com.google.devtools.mobileharness.platform.android.lightning.fileoperator;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Wrapper for arguments used for pulling file/dir from device. */
@AutoValue
public abstract class FilePullArgs {

  /** The path of source file/dir on device being pulled. */
  public abstract String srcPathOnDevice();

  /** The destination path of pulled file/dir on host machine. */
  public abstract String desPathOnHost();

  /**
   * Whether to prepare destination path on host as a directory.
   *
   * <p>This is useful if destination path on host is a directory and wants to ensure it exists
   * before pulling.
   */
  public abstract Optional<Boolean> prepareDesPathOnHostAsDir();

  /** Whether to grant full access to the destination path on host. */
  public abstract Optional<Boolean> grantDesPathOnHostFullAccess();

  /** Whether to log caught MobileHarnessException only, instead of throwing it out. */
  public abstract Optional<Boolean> logFailuresOnly();

  public static Builder builder() {
    return new AutoValue_FilePullArgs.Builder();
  }

  /** Auto value builder for {@link FilePullArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setSrcPathOnDevice(String srcPathOnDevice);

    public abstract Builder setDesPathOnHost(String desPathOnHost);

    public abstract Builder setPrepareDesPathOnHostAsDir(boolean prepareDesPathOnHostAsDir);

    public abstract Builder setGrantDesPathOnHostFullAccess(boolean grantDesPathOnHostFullAccess);

    public abstract Builder setLogFailuresOnly(boolean logFailuresOnly);

    public abstract FilePullArgs build();
  }
}
