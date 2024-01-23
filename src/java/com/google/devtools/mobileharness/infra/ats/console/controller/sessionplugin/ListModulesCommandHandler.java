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

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.XtsType;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListModulesCommand;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters;
import java.util.Map;

/** Handler for "list modules" commands. */
class ListModulesCommandHandler {

  AtsSessionPluginOutput handle(ListModulesCommand command) throws MobileHarnessException {
    TestSuiteHelper testSuiteHelper =
        getTestSuiteHelper(command.getXtsRootDir(), command.getXtsType());
    testSuiteHelper.setParameterizedModules(true);
    testSuiteHelper.setOptionalParameterizedModules(true);
    if (!command.getModuleParameter().isEmpty()) {
      testSuiteHelper.setModuleParameter(
          ModuleParameters.valueOf(Ascii.toUpperCase(command.getModuleParameter())));
    }
    Map<String, Configuration> configs = testSuiteHelper.loadTests();

    return getListModuleOutput(configs, command.getXtsRootDir());
  }

  /** Gets the output of the "list modules" command. */
  private AtsSessionPluginOutput getListModuleOutput(
      Map<String, Configuration> configs, String xtsRootDir) {
    if (configs.isEmpty()) {
      return AtsSessionPluginOutput.newBuilder()
          .setFailure(
              Failure.newBuilder()
                  .setErrorMessage(String.format("No modules found within %s", xtsRootDir)))
          .build();
    }
    return AtsSessionPluginOutput.newBuilder()
        .setSuccess(Success.newBuilder().setOutputMessage(Joiner.on("\n").join(configs.keySet())))
        .build();
  }

  private TestSuiteHelper getTestSuiteHelper(String xtsRootDir, XtsType xtsType) {
    return new TestSuiteHelper(xtsRootDir, xtsType);
  }
}
