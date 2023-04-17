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

import com.google.auto.value.AutoValue;
import java.time.Duration;
import java.util.Optional;

/** Wrapper for arguments used for apk installation. */
@AutoValue
public abstract class ApkInstallArgs {

  /** The path for the apk being installed on device. */
  public abstract String apkPath();

  /** The path for the dex metadata file being installed with the apk. */
  public abstract Optional<String> dexMetadataPath();

  /** Whether to skip installing apk if it's a downgrade. */
  public abstract Optional<Boolean> skipDowngrade();

  /** Whether to force reinstall APK if it already exist on device for user. */
  public abstract Optional<Boolean> skipIfCached();

  /** Whether to skip installing APK if same version already exist on device. */
  public abstract Optional<Boolean> skipIfVersionMatch();

  /**
   * Whether to skip checking device compatibility with the being installed GMSCore apk. This arg
   * only takes effect if the installed apk is a GMSCore apk.
   *
   * <p>In most of cases, you don't want to skip this unless you want to work around issues like
   * b/150316447. And installing a potential noncompatible GMSCore apk on device may cause
   * unpredictable issues.
   */
  public abstract Optional<Boolean> skipGmsCompatCheck();

  /** Whether to clear app data after installation. */
  public abstract Optional<Boolean> clearAppData();

  /** Whether to grant runtime permissions. */
  public abstract Optional<Boolean> grantPermissions();

  /** Whether to force no-streaming install. When set to false, use default install. */
  public abstract Optional<Boolean> forceNoStreaming();

  /**
   * Whether to add arg "--force-queryable" along with the install command. This is required for all
   * the service and orchestrator apks on API30+ (b/189348612).
   */
  public abstract Optional<Boolean> forceQueryable();

  /** Whether to bypass low target sdk check, only works on the device with sdk >= 34. */
  public abstract Optional<Boolean> bypassLowTargetSdkBlock();

  /** Timeout for apk installation. */
  public abstract Optional<Duration> installTimeout();

  /** Sleep after GMS installation. */
  public abstract Optional<Duration> sleepAfterInstallGms();

  /** User ID for apk installation. */
  public abstract Optional<String> userId();

  public static Builder builder() {
    return new AutoValue_ApkInstallArgs.Builder();
  }

  abstract Builder toBuilder();

  /** Add skipDowngrade to existing arguments. */
  public ApkInstallArgs withSkipDowngrade(boolean skipDowngrade) {
    return toBuilder().setSkipDowngrade(skipDowngrade).build();
  }

  /** Add skipOnCache to existing arguments. */
  public ApkInstallArgs withSkipIfCached(boolean skipIfCached) {
    return toBuilder().setSkipIfCached(skipIfCached).build();
  }

  /** Add skipOnVersionMatch to existing arguments. */
  public ApkInstallArgs withSkipIfVersionMatch(boolean skipIfVersionMatch) {
    return toBuilder().setSkipIfVersionMatch(skipIfVersionMatch).build();
  }

  /** Auto value builder for {@link ApkInstallArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setApkPath(String apkPath);

    public abstract Builder setDexMetadataPath(String dexMetadataPath);

    public abstract Builder setSkipDowngrade(boolean skipDowngrade);

    public abstract Builder setSkipIfCached(boolean skipIfCached);

    public abstract Builder setSkipIfVersionMatch(boolean skipIfVersionMatch);

    public abstract Builder setSkipGmsCompatCheck(boolean skipGmsCompatCheck);

    public abstract Builder setClearAppData(boolean clearAppData);

    public abstract Builder setGrantPermissions(boolean grantPermissions);

    public abstract Builder setForceNoStreaming(boolean forceNoStreaming);

    public abstract Builder setForceQueryable(boolean forceQueryable);

    public abstract Builder setBypassLowTargetSdkBlock(boolean bypassLowTargetSdkBlock);

    public abstract Builder setInstallTimeout(Duration installTimeout);

    public abstract Builder setSleepAfterInstallGms(Duration sleepAfterInstallGms);

    public abstract Builder setUserId(String userId);

    public abstract ApkInstallArgs build();
  }
}
