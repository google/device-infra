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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.AbiUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationXmlParser;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationDescriptorMetadata;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.FoldableExpandingHandler;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.IModuleParameterHandler;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParametersHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.NegativeHandler;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.NotMultiAbiHandler;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Retrieves compatibility test module definitions from the suite. */
public class SuiteModuleLoader {

  private static final String TEST_SUITE_PARAMETER_KEY = "parameter";

  private ModuleParameters forcedModuleParameter = null;
  private boolean allowParameterizedModules = false;
  private boolean allowOptionalParameterizedModules = false;
  private final Set<DeviceFoldableState> foldableStates = new LinkedHashSet<>();

  private final ConfigurationUtil configurationUtil;

  public SuiteModuleLoader() {
    this(new ConfigurationUtil());
  }

  @VisibleForTesting
  SuiteModuleLoader(ConfigurationUtil configurationUtil) {
    this.configurationUtil = configurationUtil;
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

  /** Main loading of configurations, looking into a list of folders. */
  public Map<String, Configuration> loadConfigsFromDirectory(
      List<File> testsDirs, Set<Abi> abis, List<String> configFileExtensions)
      throws MobileHarnessException {
    LinkedHashMap<String, Configuration> toRun = new LinkedHashMap<>();
    List<File> listConfigFiles = new ArrayList<>();
    listConfigFiles.addAll(
        configurationUtil.getConfigFilesFromDirsByExt(testsDirs, configFileExtensions));
    // Ensure stable initial order of configurations.
    Collections.sort(listConfigFiles);
    toRun.putAll(loadConfigsFromSpecifiedPaths(listConfigFiles, abis));
    return toRun;
  }

  /** Main loading of configurations, looking into the specified files */
  public Map<String, Configuration> loadConfigsFromSpecifiedPaths(
      List<File> listConfigFiles, Set<Abi> abis) throws MobileHarnessException {
    LinkedHashMap<String, Configuration> toRun = new LinkedHashMap<>();
    for (File configFile : listConfigFiles) {
      toRun.putAll(loadOneConfig(configFile.getName(), configFile.getAbsolutePath(), abis));
    }
    return toRun;
  }

  /**
   * Loads a single config location. It can result in several {@link Configuration}. If a single
   * configuration get expanded in different ways.
   *
   * @param configName The actual config name only. (no path)
   * @param configFullName The fully qualified config name. (with path, if any).
   * @param abis The set of all abis that needs to run.
   * @return A map of loaded configuration.
   */
  private Map<String, Configuration> loadOneConfig(
      String configName, String configFullName, Set<Abi> abis) throws MobileHarnessException {
    LinkedHashMap<String, Configuration> toRun = new LinkedHashMap<>();
    String name = Files.getNameWithoutExtension(configName);
    File configFile = new File(configFullName);
    boolean primaryAbi = true;
    boolean shouldCreateMultiAbi = true;
    // If a particular parameter was requested to be run, find it.
    boolean hasForcedModuleParameter = false;
    Set<Class<?>> forcedModuleParameterHandlerClasses = null;
    if (forcedModuleParameter != null) {
      hasForcedModuleParameter = true;
      ImmutableMap<ModuleParameters, IModuleParameterHandler> moduleParameters =
          ModuleParametersHelper.resolveParam(
              forcedModuleParameter, /* withOptional= */ allowOptionalParameterizedModules);
      forcedModuleParameterHandlerClasses = new HashSet<>();
      for (IModuleParameterHandler parameter : moduleParameters.values()) {
        if (parameter instanceof FoldableExpandingHandler) {
          for (IModuleParameterHandler fParam :
              ((FoldableExpandingHandler) parameter).expandHandler(foldableStates)) {
            forcedModuleParameterHandlerClasses.add(fParam.getClass());
          }
        } else {
          forcedModuleParameterHandlerClasses.add(parameter.getClass());
        }
      }
    }

    // Invokes parser to process the test module config file
    // Need to generate a different config for each ABI as we cannot guarantee the
    // configs are idempotent. This however means we parse the same file multiple times
    for (Abi abi : abis) {
      // Filter non-primary abi no matter what if not_multi_abi specified
      if (!shouldCreateMultiAbi && !primaryAbi) {
        continue;
      }
      // Base ID format: "<abi> <module_name>"
      String baseId = AbiUtil.createId(abi.name(), name);
      Configuration config = ConfigurationXmlParser.parse(configFile);

      boolean skipCreatingBaseConfig = false;
      List<IModuleParameterHandler> paramHandlers = getModuleParameters(name, config);

      // Use the not_multi_abi metadata even if not in parameterized mode.
      shouldCreateMultiAbi = shouldCreateMultiAbiForBase(paramHandlers);
      // Handle parameterized modules if enabled.
      if (allowParameterizedModules) {
        // If we find any parameterized combination.
        for (IModuleParameterHandler paramHandler : paramHandlers) {
          if (paramHandler instanceof NegativeHandler) {
            if (hasForcedModuleParameter
                && !forcedModuleParameterHandlerClasses.contains(paramHandler.getClass())) {
              skipCreatingBaseConfig = true;
            }
            continue;
          }
          if (hasForcedModuleParameter) {
            // When a particular parameter is forced, only create it not the others
            if (forcedModuleParameterHandlerClasses.contains(paramHandler.getClass())) {
              skipCreatingBaseConfig = true;
            } else {
              continue;
            }
          }
          // Only create primary abi of parameterized modules
          if (!primaryAbi) {
            continue;
          }
          String fullId = String.format("%s[%s]", baseId, paramHandler.getParameterIdentifier());
          Configuration paramConfig = ConfigurationXmlParser.parse(configFile);

          toRun.put(fullId, paramConfig);
        }
      }

      primaryAbi = false;
      // If a parameterized form of the module was forced, we don't create the standard version of
      // it.
      if (skipCreatingBaseConfig) {
        continue;
      }

      toRun.put(baseId, config);
    }

    return toRun;
  }

  /** Gets the list of {@link IModuleParameterHandler}s associated with a module. */
  private List<IModuleParameterHandler> getModuleParameters(String moduleName, Configuration config)
      throws MobileHarnessException {
    List<IModuleParameterHandler> params = new ArrayList<>();
    Set<String> processedParameterArgs = new HashSet<>();
    // Track family of the parameters to make sure we have no duplicate.
    Map<String, ModuleParameters> duplicateModule = new LinkedHashMap<>();

    List<String> parameters =
        config
            .getConfigDescriptor()
            .getMetadataMap()
            .getOrDefault(
                TEST_SUITE_PARAMETER_KEY, ConfigurationDescriptorMetadata.getDefaultInstance())
            .getValueList();
    if (parameters.isEmpty()) {
      return params;
    }

    for (String p : parameters) {
      if (!processedParameterArgs.add(p)) {
        // Avoid processing the same parameter twice
        continue;
      }
      ImmutableMap<ModuleParameters, IModuleParameterHandler> suiteParams =
          ModuleParametersHelper.resolveParam(
              ModuleParameters.valueOf(Ascii.toUpperCase(p)),
              /* withOptional= */ allowOptionalParameterizedModules);
      for (Entry<ModuleParameters, IModuleParameterHandler> suiteParamEntry :
          suiteParams.entrySet()) {
        ModuleParameters suiteParam = suiteParamEntry.getKey();
        String family = suiteParam.getFamily();
        if (duplicateModule.containsKey(family)) {
          // Duplicate family members are not accepted.
          throw new MobileHarnessException(
              ExtErrorId.SUITE_MODULE_LOADER_DUPLICATED_FAMILY_MEMBERS,
              String.format(
                  "Module %s is declaring parameter: %s and %s when only one expected.",
                  moduleName, suiteParam, duplicateModule.get(family)));
        } else {
          duplicateModule.put(suiteParam.getFamily(), suiteParam);
        }

        if (suiteParamEntry.getValue() instanceof FoldableExpandingHandler) {
          List<IModuleParameterHandler> foldableHandlers =
              ((FoldableExpandingHandler) suiteParamEntry.getValue()).expandHandler(foldableStates);
          params.addAll(foldableHandlers);
        } else {
          params.add(suiteParamEntry.getValue());
        }
      }
    }
    return params;
  }

  /** Whether or not the base configuration should be created for all abis or not. */
  private boolean shouldCreateMultiAbiForBase(List<IModuleParameterHandler> params) {
    for (IModuleParameterHandler param : params) {
      if (param instanceof NotMultiAbiHandler) {
        return false;
      }
    }
    return true;
  }
}
