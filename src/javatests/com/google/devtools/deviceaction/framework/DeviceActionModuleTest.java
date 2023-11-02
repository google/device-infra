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

package com.google.devtools.deviceaction.framework;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.framework.actions.Actions;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DeviceActionModuleTest {

  private static final String ADB_PATH = "adb";
  private static final String AAPT_PATH = "aapt";
  private static final Path BUNDLETOOL_JAR = Path.of("bundletool.jar");
  private static final Path CRED_FILE = Path.of("cred_file.json");
  private static final Path JAVA_BIN = Path.of("java");
  private static final Path TMP_FILE_DIR = Path.of("tmp_file_dir");
  private static final Path GEN_FILE_DIR = Path.of("gen_file_dir");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock private ResourceHelper mockHelper;
  @Mock private QuotaManager quotaManager;
  @Mock private Adb adb;
  @Mock private Aapt aapt;

  @Before
  public void doBeforeEachTest() throws Exception {
    when(adb.getAdbPath()).thenReturn(ADB_PATH);
    when(aapt.getAaptPath()).thenReturn(AAPT_PATH);
    when(mockHelper.getBundletoolJar()).thenReturn(Optional.of(BUNDLETOOL_JAR));
    when(mockHelper.getCredFile()).thenReturn(Optional.of(CRED_FILE));
    when(mockHelper.getAdb()).thenReturn(Optional.of(adb));
    when(mockHelper.getAapt()).thenReturn(Optional.of(aapt));
    when(mockHelper.getJavaBin()).thenReturn(JAVA_BIN);
    when(mockHelper.getTmpFileDir()).thenReturn(TMP_FILE_DIR);
    when(mockHelper.getGenFileDir()).thenReturn(GEN_FILE_DIR);
    when(mockHelper.getCommandExecutor()).thenReturn(new CommandExecutor());
  }

  @Test
  public void injectsActions() {
    Injector injector =
        Guice.createInjector(
            new DeviceActionModule(mockHelper, quotaManager),
            Modules.disableCircularProxiesModule());

    Actions actions = injector.getInstance(Actions.class);
    ActionConfigurer actionConfigurer = injector.getInstance(ActionConfigurer.class);

    assertNotNull(actions);
    assertThat(actionConfigurer).isInstanceOf(MergingDeviceConfigurer.class);
  }
}
