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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.nio.file.Path;
import javax.inject.Inject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class MoblyTestLoaderTest {
  private static final String SUCCESS_OUTPUT_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/platform/android/xts/common/util/testdata/success_output.txt");
  private static final Path MODULE_CONFIG_PATH = Path.of("/path/to/config");
  private static final Configuration MODULE_CONFIG =
      Configuration.newBuilder()
          .setMetadata(ConfigurationMetadata.newBuilder().setXtsModule("module1").build())
          .build();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private CommandExecutor commandExecutor;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Inject private MoblyTestLoader moblyTestLoader;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(localFileUtil.listFilePaths(eq(Path.of("/path/to")), eq(true), any()))
        .thenReturn(ImmutableList.of(Path.of("/path/to/x86_64/module1")));
  }

  @Test
  public void getTestNamesInModule_success() throws Exception {
    String output = new LocalFileUtil().readFile(SUCCESS_OUTPUT_PATH);
    Command expectedCommand = Command.of("/path/to/x86_64/module1", "--", "-l");
    when(commandExecutor.run(expectedCommand)).thenReturn(output);
    assertThat(moblyTestLoader.getTestNamesInModule(MODULE_CONFIG_PATH, MODULE_CONFIG))
        .containsExactly(
            "test_associate_createsAssociation_classicBluetooth",
            "test_permissions_sync",
            "test_removeBond_associatedDevice_succeeds");
  }

  @Test
  public void getTestNamesInModule_emptyOutput() throws Exception {
    Command expectedCommand = Command.of("/path/to/x86_64/module1", "--", "-l");
    when(commandExecutor.run(expectedCommand)).thenReturn("");
    MobileHarnessException mhException =
        Assert.assertThrows(
            MobileHarnessException.class,
            () -> moblyTestLoader.getTestNamesInModule(MODULE_CONFIG_PATH, MODULE_CONFIG));
    assertThat(mhException.getErrorId()).isEqualTo(InfraErrorId.ATSC_LOAD_MOBLY_TEST_NAMES_ERROR);
    assertThat(mhException.getMessage()).contains("Failed to get test cases from the mobly binary");
  }

  @Test
  public void getTestNamesInModule_uselessOutput() throws Exception {
    Command expectedCommand = Command.of("/path/to/x86_64/module1", "--", "-l");
    when(commandExecutor.run(expectedCommand)).thenReturn("random line1 \nrandom line2");
    MobileHarnessException mhException =
        Assert.assertThrows(
            MobileHarnessException.class,
            () -> moblyTestLoader.getTestNamesInModule(MODULE_CONFIG_PATH, MODULE_CONFIG));
    assertThat(mhException.getErrorId()).isEqualTo(InfraErrorId.ATSC_LOAD_MOBLY_TEST_NAMES_ERROR);
    assertThat(mhException.getMessage()).contains("Failed to get test cases from the mobly binary");
  }

  @Test
  public void getTestNamesInModule_onlyOneLineOutput() throws Exception {
    Command expectedCommand = Command.of("/path/to/x86_64/module1", "--", "-l");
    when(commandExecutor.run(expectedCommand))
        .thenReturn("==========> CompanionDeviceManagerTestClass <==========");
    MobileHarnessException mhException =
        Assert.assertThrows(
            MobileHarnessException.class,
            () -> moblyTestLoader.getTestNamesInModule(MODULE_CONFIG_PATH, MODULE_CONFIG));
    assertThat(mhException.getErrorId()).isEqualTo(InfraErrorId.ATSC_LOAD_MOBLY_TEST_NAMES_ERROR);
    assertThat(mhException.getMessage()).contains("Failed to get test cases from the mobly binary");
  }

  @Test
  public void getTestNamesInModule_noMoblyBinary() throws Exception {
    when(localFileUtil.listFilePaths(eq(Path.of("/path/to")), eq(true), any()))
        .thenReturn(ImmutableList.of());
    MobileHarnessException mhException =
        Assert.assertThrows(
            MobileHarnessException.class,
            () -> moblyTestLoader.getTestNamesInModule(MODULE_CONFIG_PATH, MODULE_CONFIG));
    assertThat(mhException.getErrorId()).isEqualTo(InfraErrorId.ATSC_LOAD_MOBLY_TEST_NAMES_ERROR);
    assertThat(mhException.getMessage())
        .contains("The mobly binary does not exist in directory or sub-directory of /path/to");
  }
}
