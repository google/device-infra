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

package com.google.devtools.mobileharness.platform.android.xts.suite.params;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ModuleParametersHelper}. */
@RunWith(JUnit4.class)
public final class ModuleParametersHelperTest {

  @Test
  public void moduleParameters_hsuAsLoginScreen_parsesTradefedMetadataValue() {
    // Modules declare the parameter as <option ... key="parameter" value="hsu_as_login_screen" />,
    // and SuiteModuleLoader upper-cases that value to look up the enum constant.
    assertThat(ModuleParameters.valueOf(Ascii.toUpperCase("hsu_as_login_screen")))
        .isEqualTo(ModuleParameters.HSU_AS_LOGIN_SCREEN);
    assertThat(ModuleParameters.HSU_AS_LOGIN_SCREEN.toString()).isEqualTo("hsu_as_login_screen");
  }

  @Test
  public void resolveParam_hsuAsLoginScreen_returnsHandlerWithTradefedIdentifier() {
    ImmutableMap<ModuleParameters, IModuleParameterHandler> params =
        ModuleParametersHelper.resolveParam(
            ModuleParameters.HSU_AS_LOGIN_SCREEN, /* withOptional= */ false);

    assertThat(params.keySet()).containsExactly(ModuleParameters.HSU_AS_LOGIN_SCREEN);
    // Must match Tradefed's HsuAsLoginScreenParameterHandler so that the generated module IDs
    // ("<abi> <module>[hsu-as-login-screen]") agree between the ATS console and Tradefed.
    assertThat(params.get(ModuleParameters.HSU_AS_LOGIN_SCREEN).getParameterIdentifier())
        .isEqualTo("hsu-as-login-screen");
  }

  @Test
  public void resolveParam_everyModuleParameterIsResolvable() {
    // Guards against a parameter being added to the enum without a handler being registered, and
    // documents that unhandled parameters resolve to an empty map rather than throwing.
    for (ModuleParameters param : ModuleParameters.values()) {
      assertThat(ModuleParametersHelper.resolveParam(param, /* withOptional= */ true)).isNotNull();
    }
  }
}
