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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.SystemProperties;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AliasManagerTest {
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private static final String PREDEFINED_ALIAS_NAME_1 = "predefined_alias_name_1";
  private static final String PREDEFINED_ALIAS_VALUE_1 = "predefined_alias_value_1";
  private static final String PREDEFINED_ALIAS_NAME_2 = "predefined_alias_name_2";
  private static final String PREDEFINED_ALIAS_VALUE_2 = "predefined_alias_value_2";
  private LocalFileUtil localFileUtil;

  @Inject private AliasManager aliasManager;

  @Before
  public void setUp() throws Exception {
    String xtsRoot = "xts_root_dir";
    String xtsRootDirPath = tmpFolder.newFolder(xtsRoot).toString();
    tmpFolder.newFolder(xtsRoot, "android-cts/tools");
    ImmutableMap<String, String> systemProperties = ImmutableMap.of("XTS_ROOT", xtsRootDirPath);

    Injector injector =
        Guice.createInjector(
            binder ->
                binder
                    .bind(new TypeLiteral<ImmutableMap<String, String>>() {})
                    .annotatedWith(SystemProperties.class)
                    .toInstance(systemProperties));

    localFileUtil = injector.getInstance(LocalFileUtil.class);
    localFileUtil.writeToFile(
        xtsRootDirPath + "/android-cts/tools/aliases",
        String.format(
            "alias %s='%s'\nalias %s='%s'",
            PREDEFINED_ALIAS_NAME_1,
            PREDEFINED_ALIAS_VALUE_1,
            PREDEFINED_ALIAS_NAME_2,
            PREDEFINED_ALIAS_VALUE_2));

    injector.injectMembers(this);
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

  @Test
  public void getAllAliases_success() {
    aliasManager.addAlias("alias1", "alias1_value");
    aliasManager.addAlias("alias2", "alias2_value");

    assertThat(aliasManager.getAll())
        .containsExactly(
            "alias1",
            "alias1_value",
            "alias2",
            "alias2_value",
            PREDEFINED_ALIAS_NAME_1,
            PREDEFINED_ALIAS_VALUE_1,
            PREDEFINED_ALIAS_NAME_2,
            PREDEFINED_ALIAS_VALUE_2);
  }

  @Test
  public void getAllAliases_loadPredefinedAliasesFromFile_success() throws Exception {
    assertThat(aliasManager.getAll())
        .containsExactly(
            PREDEFINED_ALIAS_NAME_1,
            PREDEFINED_ALIAS_VALUE_1,
            PREDEFINED_ALIAS_NAME_2,
            PREDEFINED_ALIAS_VALUE_2);
  }
}
