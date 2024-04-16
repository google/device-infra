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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
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
import java.util.Set;

/** Utility class for test suite. */
public class TestSuiteHelper {

  private static final ImmutableList<String> CONFIG_FILE_EXTENSIONS =
      ImmutableList.of(".config", ".configv2");

  private ModuleParameters forcedModuleParameter = null;
  private boolean allowParameterizedModules = false;
  private boolean allowOptionalParameterizedModules = false;

  private final String xtsRootDir;
  private final String xtsType;
  private final LocalFileUtil localFileUtil;

  public TestSuiteHelper(String xtsRootDir, String xtsType) {
    this(xtsRootDir, xtsType, new LocalFileUtil());
  }

  TestSuiteHelper(String xtsRootDir, String xtsType, LocalFileUtil localFileUtil) {
    this.xtsRootDir = xtsRootDir;
    this.xtsType = xtsType;
    this.localFileUtil = localFileUtil;
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
  public Map<String, Configuration> loadTests() throws MobileHarnessException {
    File testsDir = getTestsDir();
    Set<String> abiStrings = getAbisForBuildTargetArchFromSuite();
    Set<Abi> abis = new LinkedHashSet<>();
    for (String abi : abiStrings) {
      if (AbiUtil.isAbiSupportedByCompatibility(abi)) {
        abis.add(Abi.of(abi, AbiUtil.getBitness(abi)));
      }
    }

    SuiteModuleLoader suiteModuleLoader = createModuleLoader();
    suiteModuleLoader.setParameterizedModules(allowParameterizedModules);
    suiteModuleLoader.setOptionalParameterizedModules(allowOptionalParameterizedModules);
    suiteModuleLoader.setModuleParameter(forcedModuleParameter);
    return loadingStrategy(suiteModuleLoader, abis, ImmutableList.of(testsDir));
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
  private Set<String> getAbisForBuildTargetArchFromSuite() {
    Set<String> abis = new LinkedHashSet<>();
    for (String arch : TestSuiteInfo.getInstance(xtsRootDir, xtsType).getTargetArchs()) {
      abis.addAll(AbiUtil.getAbisForArch(arch));
    }
    return abis;
  }

  private SuiteModuleLoader createModuleLoader() {
    return new SuiteModuleLoader();
  }
}
