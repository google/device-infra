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

package com.google.devtools.mobileharness.platform.android.appcrawler;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import java.util.Optional;
import javax.inject.Inject;

/** Utility to extract UTP binaries. */
public class UtpBinariesExtractor {
  private static final String CLI_RESOURCE_PATH =
      "/com/google/devtools/mobileharness/platform/android/appcrawler/cli/UtpRoboCli_deploy.jar";
  private static final String UTP_LAUNCHER_RESOURCE_PATH =
      "/com/google/testing/platform/launcher/launcher_with_protobuf_deploy.jar";
  private static final String UTP_MAIN_RESOURCE_PATH =
      "/com/google/testing/platform/main/main_deploy.jar";
  private static final String UTP_DEVICE_PROVIDER_RESOURCE_PATH =
      "/com/google/testing/platform/runtime/android/provider/local/local_android_device_provider_java_binary_deploy.jar";
  private static final String UTP_ANDROID_ROBO_DRIVER_RESOURCE_PATH =
      "/com/google/testing/helium/utp/android/driver/robo/android_robo_driver_deploy.jar";

  private final ResUtil resourcesUtil;

  @Inject
  UtpBinariesExtractor(ResUtil resUtil) {
    this.resourcesUtil = resUtil;
  }

  /** Setup UTP binaries needed to run the test. */
  public UtpBinaries setUpUtpBinaries() throws MobileHarnessException {
    String cliPath = getResourceFile(CLI_RESOURCE_PATH);
    String launcherPath = getResourceFile(UTP_LAUNCHER_RESOURCE_PATH);
    String mainPath = getResourceFile(UTP_MAIN_RESOURCE_PATH);
    String driverPath = getResourceFile(UTP_ANDROID_ROBO_DRIVER_RESOURCE_PATH);
    String providerPath = getResourceFile(UTP_DEVICE_PROVIDER_RESOURCE_PATH);
    return UtpBinaries.create(cliPath, launcherPath, mainPath, providerPath, driverPath);
  }

  private String getResourceFile(String resPath) throws MobileHarnessException {
    Optional<String> externalResFile = resourcesUtil.getExternalResourceFile(resPath);
    if (externalResFile.isPresent()) {
      return externalResFile.get();
    }
    String extractedPath = resourcesUtil.getResourceFile(this.getClass(), resPath);
    if (extractedPath.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_MH_ROBO_DEPS_EXTRACTION_ERROR,
          "Resource absent: " + resPath);
    }
    return extractedPath;
  }

  /** Autovalue class holding paths to UTP binaries. */
  @AutoValue
  public abstract static class UtpBinaries {
    public abstract String cliPath();

    public abstract String launcherPath();

    public abstract String mainPath();

    public abstract String providerPath();

    public abstract String driverPath();

    public static UtpBinaries create(
        String cliPath,
        String launcherPath,
        String mainPath,
        String providerPath,
        String driverPath) {
      return new AutoValue_UtpBinariesExtractor_UtpBinaries(
          cliPath, launcherPath, mainPath, providerPath, driverPath);
    }
  }
}
