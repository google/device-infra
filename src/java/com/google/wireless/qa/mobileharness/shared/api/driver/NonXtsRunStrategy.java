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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.XtsTradefedTestDriverSpec;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** An implementation of {@link TradefedRunStrategy} for non-XTS runs. */
final class NonXtsRunStrategy implements TradefedRunStrategy {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TF_PATH_KEY = "TF_PATH";
  private static final String CONSOLE_CLASS = "com.android.tradefed.command.Console";
  private static final SystemUtil SYSTEM_UTIL = new SystemUtil();
  private final LocalFileUtil localFileUtil;

  NonXtsRunStrategy(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  @Override
  public void setUpWorkDir(
      XtsTradefedTestDriverSpec spec, Path workDir, String xtsType, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    localFileUtil.prepareDir(workDir);
    localFileUtil.grantFileOrDirFullAccess(workDir);
  }

  @Override
  public String getConcatenatedJarPath(Path workDir, XtsTradefedTestDriverSpec spec, String xtsType)
      throws MobileHarnessException {
    try {
      Path tradefedDir = Path.of("/usr/local/google/home/jiuchangz/Downloads/google-tradefed");
      ImmutableList.Builder<String> jarPaths = ImmutableList.builder();
      if (localFileUtil.isDirExist(tradefedDir)) {
        localFileUtil
            .listFilePaths(
                tradefedDir,
                /* recursively= */ false,
                path -> path.getFileName().toString().endsWith(".jar"))
            .forEach(path -> jarPaths.add(path.toString()));
      } else {
        logger.atWarning().log(
            "Generic Tradefed directory %s not found for generic TF run.", tradefedDir);
      }
      return Joiner.on(':').join(jarPaths.build());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_LIST_JARS_ERROR,
          "Failed to list jars in generic tradefed directory for generic TF run.",
          e);
    }
  }

  @Override
  public ImmutableMap<String, String> getEnvironment(
      Path workDir, String xtsType, XtsTradefedTestDriverSpec spec, Device device, String envPath)
      throws MobileHarnessException, InterruptedException {
    Map<String, String> environmentToTradefedConsole = new HashMap<>();
    environmentToTradefedConsole.put("PATH", envPath);
    environmentToTradefedConsole.put("TF_WORK_DIR", workDir.toString());
    if (device.hasDimension(Dimension.Name.DEVICE_CLASS_NAME, "AndroidJitEmulator")) {
      environmentToTradefedConsole.put(
          "TF_GLOBAL_CONFIG", AndroidJitEmulatorUtil.getHostConfigPath());
    }
    if (!spec.getEnvVars().isEmpty()) {
      String envVarJson = spec.getEnvVars();
      Map<String, String> envVar =
          new Gson().fromJson(envVarJson, new TypeToken<Map<String, String>>() {}.getType());
      for (Map.Entry<String, String> entry : envVar.entrySet()) {
        if (entry.getKey().isEmpty() || entry.getValue().isEmpty()) {
          continue;
        }
        String value = entry.getValue().replace("${TF_WORK_DIR}", workDir.toString());
        if (entry.getKey().equals(TF_PATH_KEY)) {
          // For NON_XTS, merge provided TF_PATH with scanned jars.
          environmentToTradefedConsole.put(
              TF_PATH_KEY, String.join(":", value, getConcatenatedJarPath(workDir, spec, xtsType)));
        } else {
          // This will override the existing entry if it exists.
          environmentToTradefedConsole.put(entry.getKey(), value);
        }
      }
    }

    return ImmutableMap.copyOf(environmentToTradefedConsole);
  }

  @Override
  public String getJavaPath(Path workDir, String xtsType) {
    return SYSTEM_UTIL.getJavaBin();
  }

  @Override
  public String getMainClass() {
    return CONSOLE_CLASS;
  }

  @Override
  public ImmutableList<String> getJvmDefines(Path workDir, String xtsType) {
    return ImmutableList.of();
  }

  @Override
  public ImmutableSet<String> getPreviousResultDirNames() {
    return ImmutableSet.of();
  }
}
