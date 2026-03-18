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

package com.google.devtools.mobileharness.platform.android.lightning.apkinstaller;

import com.google.auto.value.AutoBuilder;
import com.google.devtools.mobileharness.platform.android.lightning.bundletool.InstallApksArgs;
import java.nio.file.Path;
import java.time.Duration;

/** An APK Set to be installed using Bundletool. */
public record ApkSet(
    Path apks,
    boolean allowDowngrade,
    boolean allowTestOnly,
    boolean grantRuntimePermissions,
    boolean allowUninstallAndRetry,
    Duration commandTimeout)
    implements Installable {

  InstallApksArgs toInstallApksArgs(String deviceId) {
    return InstallApksArgs.builder()
        .setApks(apks())
        .setDeviceId(deviceId)
        .setAllowDowngrade(allowDowngrade())
        .setAllowTestOnly(allowTestOnly())
        .setGrantRuntimePermissions(grantRuntimePermissions())
        .setCommandTimeout(commandTimeout())
        .setAdbCommandTimeout(commandTimeout())
        .build();
  }

  public static Builder builder() {
    return new AutoBuilder_ApkSet_Builder()
        .setAllowDowngrade(false)
        .setAllowTestOnly(false)
        .setGrantRuntimePermissions(false)
        .setAllowUninstallAndRetry(false)
        .setCommandTimeout(Duration.ofMinutes(5));
  }

  /** Builder for {@link ApkSet}. */
  @AutoBuilder
  public abstract static class Builder {

    /** The required Apk Set file to install. */
    public abstract Builder setApks(Path apks);

    /** Whether to allow downgrading the app (default false). */
    public abstract Builder setAllowDowngrade(boolean allowDowngrade);

    /** Whether to allow test only apps (default false). */
    public abstract Builder setAllowTestOnly(boolean allowTestOnly);

    /** Whether to grant runtime permissions (default false). */
    public abstract Builder setGrantRuntimePermissions(boolean grantRuntimePermissions);

    /** Whether to uninstall and retry installation if the 1st attempt fails (default false). */
    public abstract Builder setAllowUninstallAndRetry(boolean allowUninstallAndRetry);

    /** Command timeout for underlying tooling (default 5 minutes). */
    public abstract Builder setCommandTimeout(Duration commandTimeout);

    public abstract ApkSet build();
  }
}
