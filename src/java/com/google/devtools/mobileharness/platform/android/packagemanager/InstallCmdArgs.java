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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

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

  private static final String ARG_PARTIAL_APPLICATION_INSTALL = "-p";

  private static final String ARG_GRANT_PERMISSIONS = "-g";

  private static final String ARG_INSTANT = "--instant";

  private static final String ARG_FORCE_NO_STREAMING = "--no-streaming";

  /** Whether to replace existing application. Default to be false. */
  public abstract boolean replaceExistingApp();

  /** Whether to allow test packages. Default to be false. */
  public abstract boolean allowTestPackages();

  /** Whether to allow version code downgrade (debuggable packages only). Default to be false. */
  public abstract boolean allowVersionCodeDowngrade();

  /** Partial application install (intall-multiple only). Default to be false. */
  public abstract boolean partialApplicationInstall();

  /** Whether to grant all runtime permissions. Default to be false. */
  public abstract boolean grantPermissions();

  /** If true, causes the app to be installed as an ephemeral install app. Default to be false. */
  public abstract boolean instant();

  /**
   * If true, always pushes APK to device and invoke Package Manager as separate steps. Default to
   * be false.
   */
  public abstract boolean forceNoStreaming();

  /** The list of all extra args passing to adb cmd. Default to be empty. */
  public abstract ImmutableList<String> extraArgs();

  abstract Builder toBuilder();

  @Memoized
  public String[] getInstallArgsArray() {
    List<String> argList = new ArrayList<>();
    if (replaceExistingApp()) {
      argList.add(ARG_REPLACE_EXISTING_APP);
    }
    if (allowTestPackages()) {
      argList.add(ARG_ALLOW_TEST_PACKAGES);
    }
    if (allowVersionCodeDowngrade()) {
      argList.add(ARG_ALLOW_VERSION_CODE_DOWNGRADE);
    }
    if (partialApplicationInstall()) {
      argList.add(ARG_PARTIAL_APPLICATION_INSTALL);
    }
    if (grantPermissions()) {
      argList.add(ARG_GRANT_PERMISSIONS);
    }
    if (instant()) {
      argList.add(ARG_INSTANT);
    }
    if (forceNoStreaming()) {
      argList.add(ARG_FORCE_NO_STREAMING);
    }
    argList.addAll(extraArgs());
    return argList.toArray(new String[0]);
  }

  /** Gets default InstallArgs builder instance. */
  public static Builder builder() {
    return new AutoValue_InstallCmdArgs.Builder()
        .setReplaceExistingApp(false)
        .setAllowTestPackages(false)
        .setAllowVersionCodeDowngrade(false)
        .setPartialApplicationInstall(false)
        .setGrantPermissions(false)
        .setInstant(false)
        .setForceNoStreaming(false)
        .setExtraArgs(ImmutableList.of());
  }

  /** Auto value builder for {@link InstallCmdArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setReplaceExistingApp(boolean value);

    public abstract Builder setAllowTestPackages(boolean value);

    public abstract Builder setAllowVersionCodeDowngrade(boolean value);

    public abstract Builder setPartialApplicationInstall(boolean value);

    public abstract Builder setGrantPermissions(boolean value);

    public abstract Builder setInstant(boolean value);

    public abstract Builder setForceNoStreaming(boolean value);

    public abstract Builder setExtraArgs(ImmutableList<String> extras);

    public abstract InstallCmdArgs build();
  }
}
