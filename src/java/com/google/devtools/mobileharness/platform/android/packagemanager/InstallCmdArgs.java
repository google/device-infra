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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Common args for adb app installation: install, install-multiple, install-multi-package. Only used
 * as param of {@link
 * com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil}.
 */
@AutoValue
public abstract class InstallCmdArgs {
  private static final String ARG_REPLACE_EXISTING_APP = "-r";

  private static final String ARG_ALLOW_TEST_PACKAGES = "-t";

  private static final String ARG_ALLOW_VERSION_CODE_DOWNGRADE = "-d";

  private static final String ARG_GRANT_PERMISSIONS = "-g";

  private static final String ARG_INSTANT = "--instant";

  private static final String ARG_FORCE_NO_STREAMING = "--no-streaming";

  private static final String ARG_FORCE_QUERYABLE = "--force-queryable";

  private static final String ARG_BYPASS_LOW_TARGET_SDK_BLOCK = "--bypass-low-target-sdk-block";

  /** Whether to replace existing application. Default to be false. */
  public abstract boolean replaceExistingApp();

  /** Whether to allow test packages. Default to be false. */
  public abstract boolean allowTestPackages();

  /** Whether to allow version code downgrade (debuggable packages only). Default to be false. */
  public abstract boolean allowVersionCodeDowngrade();

  /** Whether to grant all runtime permissions. Default to be false. */
  public abstract boolean grantPermissions();

  /** If true, causes the app to be installed as an ephemeral install app. Default to be false. */
  public abstract boolean instant();

  /**
   * If true, always pushes APK to device and invoke Package Manager as separate steps. Default to
   * be false.
   */
  public abstract boolean forceNoStreaming();

  /** If true, forces the app to be visible to all other apps on the device. Default to be false. */
  public abstract boolean forceQueryable();

  /**
   * If true, bypasses the potential blocking of the install due to low target sdk. Default to be
   * false.
   */
  public abstract boolean bypassLowTargetSdkBlock();

  /** The list of all extra args passing to adb cmd. Default to be empty. */
  public abstract ImmutableList<String> extraArgs();

  abstract Builder toBuilder();

  /** Gets the install args array. */
  public String[] getInstallArgsArray() {
    return getInstallArgsArray(Integer.MAX_VALUE);
  }

  /**
   * Gets the install args array for the given sdk version.
   *
   * @param sdkVersion the sdk version of the device, used to determine if the arg is supported.
   */
  public String[] getInstallArgsArray(int sdkVersion) {
    ImmutableList.Builder<String> args = ImmutableList.builder();
    if (replaceExistingApp()) {
      args.add(ARG_REPLACE_EXISTING_APP);
    }
    if (allowTestPackages()) {
      args.add(ARG_ALLOW_TEST_PACKAGES);
    }
    if (allowVersionCodeDowngrade() && sdkVersion >= 17) {
      args.add(ARG_ALLOW_VERSION_CODE_DOWNGRADE);
    }
    if (grantPermissions() && sdkVersion >= 23) {
      args.add(ARG_GRANT_PERMISSIONS);
    }
    if (instant() && sdkVersion >= 26) {
      args.add(ARG_INSTANT);
    }
    if (forceNoStreaming() && sdkVersion >= 27) {
      args.add(ARG_FORCE_NO_STREAMING);
    }
    if (forceQueryable() && sdkVersion >= 30) {
      args.add(ARG_FORCE_QUERYABLE);
    }
    if (bypassLowTargetSdkBlock() && sdkVersion >= 34) {
      args.add(ARG_BYPASS_LOW_TARGET_SDK_BLOCK);
    }
    args.addAll(extraArgs());
    return args.build().toArray(new String[0]);
  }

  /** Gets default InstallArgs builder instance. */
  public static Builder builder() {
    return new AutoValue_InstallCmdArgs.Builder()
        .setReplaceExistingApp(false)
        .setAllowTestPackages(false)
        .setAllowVersionCodeDowngrade(false)
        .setGrantPermissions(false)
        .setInstant(false)
        .setForceNoStreaming(false)
        .setForceQueryable(false)
        .setBypassLowTargetSdkBlock(false);
  }

  /** Auto value builder for {@link InstallCmdArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setReplaceExistingApp(boolean value);

    public abstract Builder setAllowTestPackages(boolean value);

    public abstract Builder setAllowVersionCodeDowngrade(boolean value);

    public abstract Builder setGrantPermissions(boolean value);

    public abstract Builder setInstant(boolean value);

    public abstract Builder setForceNoStreaming(boolean value);

    public abstract Builder setForceQueryable(boolean value);

    public abstract Builder setBypassLowTargetSdkBlock(boolean value);

    public abstract ImmutableList.Builder<String> extraArgsBuilder();

    @CanIgnoreReturnValue
    public Builder addExtraArgs(String... extras) {
      extraArgsBuilder().add(extras);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAllExtraArgs(Iterable<String> extras) {
      extraArgsBuilder().addAll(extras);
      return this;
    }

    public abstract InstallCmdArgs build();
  }
}
