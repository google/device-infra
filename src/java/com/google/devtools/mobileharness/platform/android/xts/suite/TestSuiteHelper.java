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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.platform.android.xts.common.util.AbiFormatter;
import com.google.devtools.mobileharness.platform.android.xts.common.util.AbiUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Utility class for test suite. */
public class TestSuiteHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableList<String> CONFIG_FILE_EXTENSIONS =
      ImmutableList.of(".config", ".configv2");

  private final Set<Abi> abisSet = new LinkedHashSet<>();

  private ModuleParameters forcedModuleParameter = null;
  private boolean allowParameterizedModules = false;
  private boolean allowOptionalParameterizedModules = false;

  private final String xtsRootDir;
  private final String xtsType;
  private final LocalFileUtil localFileUtil;

  public TestSuiteHelper(String xtsRootDir, String xtsType) {
    this(xtsRootDir, xtsType, new LocalFileUtil());
  }

  @VisibleForTesting
  TestSuiteHelper(String xtsRootDir, String xtsType, LocalFileUtil localFileUtil) {
    this.xtsRootDir = xtsRootDir;
    this.xtsType = xtsType;
    this.localFileUtil = localFileUtil;
  }

  /** Sets the abis that should be run against. */
  public void setAbis(Set<Abi> abis) {
    abisSet.addAll(abis);
  }

  /** Sets the only {@link ModuleParameters} type that should be run. */
  public void setModuleParameter(ModuleParameters param) {
    forcedModuleParameter = param;
  }

  /** Sets whether or not to allow parameterized modules. */
  public void setParameterizedModules(boolean allowed) {
    allowParameterizedModules = allowed;
  }

  /** Sets whether or not to allow optional parameterized modules. */
  public void setOptionalParameterizedModules(boolean allowed) {
    allowOptionalParameterizedModules = allowed;
  }

  /**
   * Loads the test module configuration that will be run. Each module is defined by a {@link
   * Configuration} and a unique name under which it will report results.
   */
  public Map<String, Configuration> loadTests(@Nullable DeviceInfo deviceInfo)
      throws MobileHarnessException, InterruptedException {
    File testsDir = getTestsDir();
    Set<Abi> abis = getAbis(deviceInfo);
    logger.atInfo().log("The set of abis running against: %s", abis);

    SuiteModuleLoader suiteModuleLoader = createModuleLoader();
    suiteModuleLoader.setParameterizedModules(allowParameterizedModules);
    suiteModuleLoader.setOptionalParameterizedModules(allowOptionalParameterizedModules);
    suiteModuleLoader.setModuleParameter(forcedModuleParameter);
    return loadingStrategy(suiteModuleLoader, abis, ImmutableList.of(testsDir));
  }

  @VisibleForTesting
  Set<Abi> getAbis(@Nullable DeviceInfo deviceInfo)
      throws MobileHarnessException, InterruptedException {
    if (!abisSet.isEmpty()) {
      logger.atInfo().log("Using the provided abis: %s", abisSet);
      return abisSet;
    }

    Set<String> archAbis = getAbisForBuildTargetArchFromSuite();
    Set<Abi> abis = new LinkedHashSet<>();

    ImmutableSet<String> deviceAbis = getDeviceAbis(deviceInfo);
    if (deviceAbis.isEmpty()) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_SUITE_DEVICE_ABIS_MISSING,
          String.format(
              "Couldn't determinate the abi of the device '%s'.",
              deviceInfo == null ? "UNKNOWN" : deviceInfo.deviceId()));
    }

    for (String abi : deviceAbis) {
      if (archAbis.contains(abi) && AbiUtil.isAbiSupportedByCompatibility(abi)) {
        abis.add(Abi.of(abi, AbiUtil.getBitness(abi)));
      } else {
        logger
            .atInfo()
            .with(IMPORTANCE, IMPORTANT)
            .log(
                "abi '%s' is supported by device but not by this suite build (%s), tests will not"
                    + " run against it.",
                abi, archAbis);
      }
    }

    if (abis.isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          ExtErrorId.TEST_SUITE_NO_ABIS_SUPPORTED,
          String.format(
              "None of the abi supported by this tests suite build ('%s') are supported by the"
                  + " device ('%s').",
              archAbis, deviceAbis),
          /* cause= */ null);
    }
    return abis;
  }

  private ImmutableSet<String> getDeviceAbis(@Nullable DeviceInfo deviceInfo)
      throws MobileHarnessException, InterruptedException {
    if (deviceInfo == null) {
      logger.atInfo().with(IMPORTANCE, IMPORTANT).log("No device info provided, using host abis.");
      return ImmutableSet.copyOf(getHostAbis());
    }

    return ImmutableSet.copyOf(
        AbiFormatter.getSupportedAbis(
            deviceInfo.supportedAbiList().orElse(null), deviceInfo.supportedAbi().orElse(null)));
  }

  /** Gets the host machine abis. */
  @VisibleForTesting
  Set<String> getHostAbis() throws MobileHarnessException, InterruptedException {
    return AbiUtil.getHostAbi();
  }

  /**
   * Default loading strategy will load from the tests directory.
   *
   * @param suiteModuleLoader The suite module loader.
   * @param abis The set of abis to run against.
   * @param testsDirs The tests directory.
   * @return A list of loaded configuration for the suite.
   */
  private Map<String, Configuration> loadingStrategy(
      SuiteModuleLoader suiteModuleLoader, Set<Abi> abis, List<File> testsDirs)
      throws MobileHarnessException {
    LinkedHashMap<String, Configuration> loadedConfigs = new LinkedHashMap<>();
    loadedConfigs.putAll(
        suiteModuleLoader.loadConfigsFromDirectory(testsDirs, abis, CONFIG_FILE_EXTENSIONS));
    return loadedConfigs;
  }

  private File getTestsDir() throws MobileHarnessException {
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_SUITE_ROOT_DIR_NOT_EXIST, "Test suite root dir does not exist.");
    }
    return XtsDirUtil.getXtsTestCasesDir(Path.of(xtsRootDir), xtsType).toFile();
  }

  /** Gets the possible abis from the TestSuiteInfo. */
  public Set<String> getAbisForBuildTargetArchFromSuite() {
    Set<String> abis = new LinkedHashSet<>();
    for (String arch : TestSuiteInfo.getInstance(xtsRootDir, xtsType).getTargetArchs()) {
      abis.addAll(AbiUtil.getAbisForArch(arch));
    }
    return abis;
  }

  private SuiteModuleLoader createModuleLoader() {
    return new SuiteModuleLoader();
  }

  /** Device info used to load tests. */
  @AutoValue
  public abstract static class DeviceInfo {

    /** Device ID. */
    public abstract String deviceId();

    /** Device's supported abi list. From Android device property `ro.product.cpu.abilist`. */
    public abstract Optional<String> supportedAbiList();

    /** Device's supported abi. From Android device property `ro.product.cpu.abi`. */
    public abstract Optional<String> supportedAbi();

    public static Builder builder() {
      return new AutoValue_TestSuiteHelper_DeviceInfo.Builder();
    }

    /** Builder for {@link DeviceInfo}. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setDeviceId(String deviceId);

      public abstract Builder setSupportedAbiList(String supportedAbiList);

      public abstract Builder setSupportedAbi(String supportedAbi);

      public abstract DeviceInfo build();
    }
  }
}
