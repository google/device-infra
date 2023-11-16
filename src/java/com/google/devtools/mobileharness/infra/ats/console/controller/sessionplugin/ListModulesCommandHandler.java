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

package com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil.getConfigDirs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListModulesCommand;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import java.io.File;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/** Handler for "list modules" commands. */
class ListModulesCommandHandler {

  private final ConfigurationUtil configurationUtil;

  @Inject
  ListModulesCommandHandler(ConfigurationUtil configurationUtil) {
    this.configurationUtil = configurationUtil;
  }

  AtsSessionPluginOutput handle(ListModulesCommand command) {
    return getListModuleOutput(getConfigDirs(command.getXtsRootDir()));
  }

  /**
   * Gets the output of the "list modules" command.
   *
   * @param configDirs a list of directories that contains ATS2.0 configs
   * @return {@code ExitCode.SOFTWARE} if error occurs, otherwise {@code ExitCode.OK}
   */
  private AtsSessionPluginOutput getListModuleOutput(List<File> configDirs) {
    ImmutableMap<String, Configuration> configs = configurationUtil.getConfigsFromDirs(configDirs);
    ImmutableList<String> modules = getModuleList(ImmutableSet.copyOf(configs.values()));
    if (modules.isEmpty()) {
      return AtsSessionPluginOutput.newBuilder()
          .setFailure(
              Failure.newBuilder()
                  .setErrorMessage(String.format("No modules found at %s", configDirs)))
          .build();
    }
    return AtsSessionPluginOutput.newBuilder()
        .setSuccess(Success.newBuilder().setOutputMessage(String.join("\n", modules)))
        .build();
  }

  /**
   * Gets a sorted list of ATS2.0 modules from ATS2.0 configurations.
   *
   * @param configs a collection of ATS2.0 configuration proto
   */
  private ImmutableList<String> getModuleList(Set<Configuration> configs) {
    // TODO: append configuration options to each module, e.g. abi, secondary-user.
    return configs.stream()
        .filter(config -> !config.getMetadata().getXtsModule().isEmpty())
        .map(config -> config.getMetadata().getXtsModule())
        .sorted()
        .collect(toImmutableList());
  }
}
