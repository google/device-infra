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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModuleArgTest {

  @Test
  public void testCreateModuleArg_success_full() throws Exception {
    ModuleArg moduleArg = ModuleArg.create("module_name:arg_name:file:=arg_value");
    assertThat(moduleArg.moduleName()).isEqualTo("module_name");
    assertThat(moduleArg.argName()).isEqualTo("arg_name");
    assertThat(moduleArg.argKey()).isEqualTo("file");
    assertThat(moduleArg.argValue()).isEqualTo("arg_value");
    assertThat(moduleArg.isFile()).isTrue();
  }

  @Test
  public void testCreateModuleArg_success_noArgKey() throws Exception {
    ModuleArg moduleArg = ModuleArg.create("module_name:arg_name:arg_value");
    assertThat(moduleArg.moduleName()).isEqualTo("module_name");
    assertThat(moduleArg.argName()).isEqualTo("arg_name");
    assertThat(moduleArg.argKey()).isEmpty();
    assertThat(moduleArg.argValue()).isEqualTo("arg_value");
  }

  @Test
  public void testCreateModuleArg_success_withAbi() throws Exception {
    ModuleArg moduleArg = ModuleArg.create("arm64-v8a module_name:arg_name:arg_value");
    assertThat(moduleArg.moduleName()).isEqualTo("arm64-v8a module_name");
    assertThat(moduleArg.argName()).isEqualTo("arg_name");
    assertThat(moduleArg.argKey()).isEmpty();
    assertThat(moduleArg.argValue()).isEqualTo("arg_value");
  }

  @Test
  public void testCreateModuleArg_success_withAbiAndParam() throws Exception {
    ModuleArg moduleArg =
        ModuleArg.create("arm64-v8a module_name[foldable:1:HALF_OPENED]:arg_name:arg_value");
    assertThat(moduleArg.moduleName()).isEqualTo("arm64-v8a module_name[foldable:1:HALF_OPENED]");
    assertThat(moduleArg.argName()).isEqualTo("arg_name");
    assertThat(moduleArg.argKey()).isEmpty();
    assertThat(moduleArg.argValue()).isEqualTo("arg_value");
  }

  @Test
  public void testCreateModuleArg_success_keyValueSeparatorInArgValue() throws Exception {
    ModuleArg moduleArg = ModuleArg.create("module_name:arg_name:arg_key:=arg_:=value");
    assertThat(moduleArg.moduleName()).isEqualTo("module_name");
    assertThat(moduleArg.argName()).isEqualTo("arg_name");
    assertThat(moduleArg.argKey()).isEqualTo("arg_key");
    assertThat(moduleArg.argValue()).isEqualTo("arg_:=value");
  }

  @Test
  public void testCreateModuleArg_invalid() {
    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> ModuleArg.create("module_name:arg_name"))
                .getErrorId())
        .isEqualTo(InfraErrorId.ATSC_INVALID_MODULE_ARG);
    assertThat(ModuleArg.isValid("module_name:arg_name")).isFalse();
  }
}
