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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalModeRuleTest {

  @Rule public final LocalModeRule rule = new LocalModeRule();

  @Test
  public void getLocalMode_returnsInstance() {
    assertThat(rule.getLocalMode()).isNotNull();
  }

  @Test
  public void getModule_bindsExecModeAndLocalMode() {
    Injector injector = Guice.createInjector(rule.getModule());

    assertThat(injector.getInstance(ExecMode.class)).isSameInstanceAs(rule.getLocalMode());
    assertThat(injector.getInstance(LocalMode.class)).isSameInstanceAs(rule.getLocalMode());
    assertThat(injector.getInstance(LocalModeEnvironment.class)).isNotNull();
  }
}
