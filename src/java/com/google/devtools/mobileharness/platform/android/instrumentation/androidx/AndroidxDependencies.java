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

package com.google.devtools.mobileharness.platform.android.instrumentation.androidx;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import java.util.Optional;

/**
 * Provides bundled AndroidX Orchestrator and Test Services APK artifacts.
 *
 * <p>Supported versions:
 *
 * <ul>
 *   <li>1.4.1 (also resolved from "auto")
 *   <li>1.6.1
 * </ul>
 */
public class AndroidxDependencies {

  private record BundledApks(String orchestratorApk, String testServicesApk) {}

  private static final String DEFAULT_VERSION = "1.4.1";
  private static final String RES_PATH_PREFIX =
      "/com/google/devtools/mobileharness/platform/android/instrumentation/androidx/res/";
  private static final ImmutableMap<String, BundledApks> VERSION_TO_APKS =
      ImmutableMap.of(
          "1.4.1",
              new BundledApks(
                  RES_PATH_PREFIX + "orchestrator-1.4.1.apk",
                  RES_PATH_PREFIX + "test-services-1.4.1.apk"),
          // There is no 1.6.1 release of test services, pairing 1.6.1 orchestrator with 1.6.0.
          "1.6.1",
              new BundledApks(
                  RES_PATH_PREFIX + "orchestrator-1.6.1.apk",
                  RES_PATH_PREFIX + "test-services-1.6.0.apk"));

  private final ResUtil resUtil;

  public AndroidxDependencies() {
    this(new ResUtil());
  }

  public AndroidxDependencies(ResUtil resUtil) {
    this.resUtil = resUtil;
  }

  /** Returns the path to the AndroidX Orchestrator APK for the given version. */
  public String getOrchestratorApkPath(String version) throws MobileHarnessException {
    String resolvedVersion = resolveVersion(version);
    return getResourceFile(VERSION_TO_APKS.get(resolvedVersion).orchestratorApk());
  }

  /** Returns the path to the AndroidX Test Services APK for the given version. */
  public String getTestServicesApkPath(String version) throws MobileHarnessException {
    String resolvedVersion = resolveVersion(version);
    return getResourceFile(VERSION_TO_APKS.get(resolvedVersion).testServicesApk());
  }

  private String resolveVersion(String version) throws MobileHarnessException {
    String resolvedVersion = version.equals("auto") ? DEFAULT_VERSION : version;
    if (!VERSION_TO_APKS.containsKey(resolvedVersion)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_UNSUPPORTED_ORCHESTRATOR_VERSION,
          String.format("Unsupported AndroidX orchestrator version: %s", version));
    }
    return resolvedVersion;
  }

  private String getResourceFile(String resPath) throws MobileHarnessException {
    Optional<String> externalResFile = resUtil.getExternalResourceFile(resPath);
    if (externalResFile.isPresent()) {
      return externalResFile.get();
    }
    return resUtil.getResourceFile(this.getClass(), resPath);
  }
}
