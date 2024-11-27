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
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Splitter;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListModulesCommand;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ListModulesCommandHandlerTest {

  private static final String TEST_CTS_ROOT_DIR =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin/testdata/listmodules");

  @Inject private ListModulesCommandHandler listModulesCommandHandler;

  @Before
  public void setUp() {
    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void handle_moduleParamHasMultiUser_success() throws Exception {
    AtsSessionPluginOutput atsSessionPluginOutput =
        listModulesCommandHandler.handle(
            ListModulesCommand.newBuilder()
                .setXtsRootDir(TEST_CTS_ROOT_DIR)
                .setXtsType("cts")
                .build());

    assertThat(atsSessionPluginOutput.hasSuccess()).isTrue();
    assertThat(
            Splitter.on("\n")
                .trimResults()
                .omitEmptyStrings()
                .splitToStream(atsSessionPluginOutput.getSuccess().getOutputMessage())
                .map(entry -> Splitter.on(" ").splitToList(entry).get(1))
                .collect(toImmutableList()))
        .containsAtLeast(
            "CtsMultiUserTestCases[run-on-clone-profile]",
            "CtsMultiUserTestCases[run-on-secondary-user]",
            "CtsMultiUserTestCases[run-on-work-profile]",
            "CtsMultiUserTestCases[run-on-private-profile]");
  }
}
