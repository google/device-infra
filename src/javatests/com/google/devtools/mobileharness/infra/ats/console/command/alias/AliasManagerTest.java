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

package com.google.devtools.mobileharness.infra.ats.console.command.alias;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.Guice;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AliasManagerTest {

  @Inject private AliasManager aliasManager;

  @Before
  public void setUp() {
    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void addAlias_success() {
    aliasManager.addAlias("a", "b");
    assertThat(aliasManager.getAlias("a")).hasValue("b");
  }

  @Test
  public void addAlias_overrideExistingAlias_success() {
    aliasManager.addAlias("a", "b");
    aliasManager.addAlias("a", "c");
    assertThat(aliasManager.getAlias("a")).hasValue("c");
  }

  @Test
  public void getAlias_aliasNotFound() {
    assertThat(aliasManager.getAlias("a")).isEmpty();
  }
}
