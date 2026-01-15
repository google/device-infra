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

package com.google.devtools.mobileharness.infra.ats.tradefed;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.TradefedTestDriverSpec;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class NonXtsRunStrategyTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Mock private LocalFileUtil localFileUtil;
  @Mock private TestInfo testInfo;
  @Mock private Device device;

  private static final Path WORK_DIR = Path.of("/path/to/work");
  private static final String TRADEFED_DIR = "/path/to/tradefed";
  private NonXtsRunStrategy nonXtsRunStrategy;

  @Before
  public void setUp() throws Exception {
    flags.setAllFlags(ImmutableMap.of("tradefed_binary_dir", TRADEFED_DIR));
    nonXtsRunStrategy = new NonXtsRunStrategy(localFileUtil);
  }

  @Test
  public void setUpWorkDir_success() throws Exception {
    nonXtsRunStrategy.setUpWorkDir(TradefedTestDriverSpec.getDefaultInstance(), WORK_DIR, testInfo);

    verify(localFileUtil).prepareDir(WORK_DIR);
    verify(localFileUtil).grantFileOrDirFullAccess(WORK_DIR);
  }

  @Test
  public void getConcatenatedJarPath_dirExist_returnJars() throws Exception {
    when(localFileUtil.isDirExist(Path.of(TRADEFED_DIR))).thenReturn(true);
    when(localFileUtil.listFilePaths(any(Path.class), anyBoolean(), any()))
        .thenReturn(
            ImmutableList.of(Path.of(TRADEFED_DIR, "jar1.jar"), Path.of(TRADEFED_DIR, "jar2.jar")));

    String jarPath =
        nonXtsRunStrategy.getConcatenatedJarPath(
            WORK_DIR, TradefedTestDriverSpec.getDefaultInstance());

    assertThat(jarPath).isEqualTo(TRADEFED_DIR + "/jar1.jar:" + TRADEFED_DIR + "/jar2.jar");
  }

  @Test
  public void getConcatenatedJarPath_dirNotExist_returnEmpty() throws Exception {
    when(localFileUtil.isDirExist(Path.of(TRADEFED_DIR))).thenReturn(false);

    String jarPath =
        nonXtsRunStrategy.getConcatenatedJarPath(
            WORK_DIR, TradefedTestDriverSpec.getDefaultInstance());

    assertThat(jarPath).isEmpty();
  }

  @Test
  public void getEnvironment_default() throws Exception {
    when(localFileUtil.isDirExist(Path.of(TRADEFED_DIR))).thenReturn(false);

    ImmutableMap<String, String> env =
        nonXtsRunStrategy.getEnvironment(
            WORK_DIR, TradefedTestDriverSpec.getDefaultInstance(), device, "/path/to/env");

    assertThat(env).containsExactly("PATH", "/path/to/env", "TF_WORK_DIR", WORK_DIR.toString());
  }

  @Test
  public void getEnvironment_withEnvVars() throws Exception {
    when(localFileUtil.isDirExist(Path.of(TRADEFED_DIR))).thenReturn(true);
    when(localFileUtil.listFilePaths(any(Path.class), anyBoolean(), any()))
        .thenReturn(ImmutableList.of(Path.of(TRADEFED_DIR, "jar1.jar")));
    TradefedTestDriverSpec spec =
        TradefedTestDriverSpec.newBuilder()
            .setEnvVars("{\"key1\":\"value1\", \"TF_PATH\":\"${TF_WORK_DIR}/tf\"}")
            .build();

    ImmutableMap<String, String> env =
        nonXtsRunStrategy.getEnvironment(WORK_DIR, spec, device, "/path/to/env");

    assertThat(env)
        .containsExactly(
            "PATH",
            "/path/to/env",
            "TF_WORK_DIR",
            WORK_DIR.toString(),
            "key1",
            "value1",
            "TF_PATH",
            WORK_DIR + "/tf:" + TRADEFED_DIR + "/jar1.jar");
  }

  @Test
  public void getJavaPath_success() {
    assertThat(nonXtsRunStrategy.getJavaPath(WORK_DIR)).isNotNull();
  }

  @Test
  public void getMainClass_success() {
    assertThat(nonXtsRunStrategy.getMainClass()).isEqualTo("com.android.tradefed.command.Console");
  }

  @Test
  public void getJvmDefines_success() {
    assertThat(nonXtsRunStrategy.getJvmDefines(WORK_DIR)).isEmpty();
  }

  @Test
  public void getCurrentSessionResultFilter_success() {
    Predicate<Path> filter = nonXtsRunStrategy.getCurrentSessionResultFilter();
    assertThat(filter.test(Path.of("any"))).isTrue();
  }

  @Test
  public void getResultsDirInWorkDir_success() {
    assertThat(nonXtsRunStrategy.getResultsDirInWorkDir(WORK_DIR).toString())
        .isEqualTo(WORK_DIR.resolve("results").toString());
  }

  @Test
  public void getLogsDirInWorkDir_success() {
    assertThat(nonXtsRunStrategy.getLogsDirInWorkDir(WORK_DIR).toString())
        .isEqualTo(WORK_DIR.resolve("logs").toString());
  }

  @Test
  public void getGenFileDir_success() throws Exception {
    when(testInfo.getGenFileDir()).thenReturn("/path/to/gen");
    assertThat(nonXtsRunStrategy.getGenFileDir(testInfo).toString())
        .isEqualTo(Path.of("/path/to/gen", "non-xts-gen-files").toString());
  }
}
