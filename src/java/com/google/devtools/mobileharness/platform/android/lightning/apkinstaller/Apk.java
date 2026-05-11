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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.packagemanager.InstallCmdArgs;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * An APK installable, to be installed on a device.
 *
 * <p>Can consist of one or more APK splits, all of which sharing the same package name.
 */
public record Apk(
    ImmutableList<Path> apks,
    ImmutableList<Path> dexMetadataPaths,
    boolean allowDowngrade,
    boolean allowTestOnly,
    boolean grantRuntimePermissions,
    boolean forceNoStreaming,
    boolean forceQueryable,
    boolean bypassLowTargetSdkBlock,
    Optional<String> userId,
    boolean allowUninstallAndRetry,
    Duration sleepAfterInstall,
    Duration commandTimeout)
    implements Installable {

  InstallCmdArgs toInstallCmdArgs() {
    return InstallCmdArgs.builder()
        // The default for `-r` (replace existing app) changed around Android 10 to be by default
        // true, and a `-R` flag was added for the opposite behavior. Since all existing usages set
        // it to true, we do not expose it as a option and just set it to true here.
        // See PackageManagerShellCommand.java.
        .setReplaceExistingApp(true)
        .setAllowVersionCodeDowngrade(allowDowngrade())
        .setAllowTestPackages(allowTestOnly())
        .setGrantPermissions(grantRuntimePermissions())
        .setForceNoStreaming(forceNoStreaming())
        .setForceQueryable(forceQueryable())
        .setBypassLowTargetSdkBlock(bypassLowTargetSdkBlock())
        .build();
  }

  public static Builder builder() {
    return new AutoBuilder_Apk_Builder()
        .setAllowDowngrade(false)
        .setAllowTestOnly(false)
        .setGrantRuntimePermissions(false)
        .setForceNoStreaming(false)
        .setForceQueryable(false)
        .setBypassLowTargetSdkBlock(false)
        .setAllowUninstallAndRetry(false)
        .setSleepAfterInstall(Duration.ZERO)
        .setCommandTimeout(Duration.ofMinutes(5));
  }

  /** Builder for {@link Apk}. */
  @AutoBuilder
  public abstract static class Builder {

    public abstract ImmutableList.Builder<Path> apksBuilder();

    /** Adds an APK (or APK split) to be installed. */
    @CanIgnoreReturnValue
    public Builder addApk(Path apk) {
      apksBuilder().add(apk);
      return this;
    }

    public abstract ImmutableList.Builder<Path> dexMetadataPathsBuilder();

    /**
     * Adds a dex metadata file to be installed.
     *
     * <p>See https://source.android.com/docs/core/runtime/configure for more details.
     */
    @CanIgnoreReturnValue
    public Builder addDexMetadataPath(Path dexMetadataPath) {
      dexMetadataPathsBuilder().add(dexMetadataPath);
      return this;
    }

    /** Whether to allow downgrading the app (default false). */
    public abstract Builder setAllowDowngrade(boolean allowDowngrade);

    /** Whether to allow test only apps (default false). */
    public abstract Builder setAllowTestOnly(boolean allowTestOnly);

    /** Whether to grant runtime permissions (default false). */
    public abstract Builder setGrantRuntimePermissions(boolean grantRuntimePermissions);

    /** Whether to force no streaming (default false). */
    public abstract Builder setForceNoStreaming(boolean forceNoStreaming);

    /** Whether to force queryable (default false). */
    public abstract Builder setForceQueryable(boolean forceQueryable);

    /** Whether to bypass low target sdk block (default false). */
    public abstract Builder setBypassLowTargetSdkBlock(boolean bypassLowTargetSdkBlock);

    /** The User ID for which to install the apk (default empty / not set). */
    public abstract Builder setUserId(String userId);

    /** Whether to uninstall and retry installation if the 1st attempt fails (default false). */
    public abstract Builder setAllowUninstallAndRetry(boolean allowUninstallAndRetry);

    /** Sleep after install (default no sleep). */
    public abstract Builder setSleepAfterInstall(Duration sleepAfterInstall);

    /** Command timeout for underlying tooling (default 5 minutes). */
    public abstract Builder setCommandTimeout(Duration commandTimeout);

    public abstract Apk autoBuild();

    public Apk build() {
      Apk apk = autoBuild();
      checkState(!apk.apks().isEmpty(), "At least one apk file is required.");
      if (apk.userId().isPresent()) {
        checkState(!apk.userId().get().isEmpty(), "User ID must be non-empty if set.");
      }
      return apk;
    }
  }
}
