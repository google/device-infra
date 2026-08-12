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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.TradefedTestDriverSpec;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class NonXtsRunStrategyTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlags flags = new SetFlags();

  @Mock private LocalFileUtil localFileUtil;
  @Mock private SystemUtil systemUtil;
  private TestInfo testInfo;
  private JobInfo jobInfo;
  @Mock private Device device;

  private static final Path WORK_DIR = Path.of("/path/to/work");
  private static final String TRADEFED_DIR = "/path/to/tradefed";
  private NonXtsRunStrategy nonXtsRunStrategy;

  @Before
  public void setUp() throws Exception {
    flags.set("tradefed_binary_dir", TRADEFED_DIR);
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("TradefedTest")
                    .build())
            .build();
    testInfo = jobInfo.tests().add("test_id", "test_name");
    nonXtsRunStrategy = new NonXtsRunStrategy(localFileUtil, systemUtil);
  }

  @Test
  public void setUpWorkDir_success() throws Exception {
    nonXtsRunStrategy.setUpWorkDir(TradefedTestDriverSpec.getDefaultInstance(), WORK_DIR, testInfo);

    verify(localFileUtil).prepareDir(WORK_DIR);
    verify(localFileUtil).grantFileOrDirFullAccess(WORK_DIR);
    Path tfTmpDir = WORK_DIR.resolve("tf_tmp");
    verify(localFileUtil).prepareDir(tfTmpDir);
    verify(localFileUtil).grantFileOrDirFullAccess(tfTmpDir);
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
  public void getEnvironment_withTfHostConfig() throws Exception {
    flags.set("tradefed_host_config", "/path/to/host-config.xml");

    ImmutableMap<String, String> env =
        nonXtsRunStrategy.getEnvironment(
            WORK_DIR, TradefedTestDriverSpec.getDefaultInstance(), device, "/path/to/env");

    assertThat(env)
        .containsExactly(
            "PATH",
            "/path/to/env",
            "TF_WORK_DIR",
            WORK_DIR.toString(),
            "TF_GLOBAL_CONFIG",
            "/path/to/host-config.xml");
  }

  @Test
  public void getEnvironment_withTfServiceAccountKeyFile() throws Exception {
    flags.set("tradefed_service_account_key_file", "/path/to/key.json");

    ImmutableMap<String, String> env =
        nonXtsRunStrategy.getEnvironment(
            WORK_DIR, TradefedTestDriverSpec.getDefaultInstance(), device, "/path/to/env");

    assertThat(env)
        .containsExactly(
            "PATH",
            "/path/to/env",
            "TF_WORK_DIR",
            WORK_DIR.toString(),
            "GOOGLE_APPLICATION_CREDENTIALS",
            "/path/to/key.json");
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
    when(systemUtil.getJavaBin()).thenReturn("/path/to/java");
    assertThat(nonXtsRunStrategy.getJavaPath(WORK_DIR)).isEqualTo("/path/to/java");
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
  public void getLogsDirInWorkDir_withHostLog() throws Exception {
    Path hostLog = WORK_DIR.resolve("some_dir/host_log_123.txt");
    ArgumentCaptor<DirectoryStream.Filter<Path>> filterCaptor =
        ArgumentCaptor.forClass(DirectoryStream.Filter.class);
    when(localFileUtil.listFilePaths(eq(WORK_DIR), eq(true), filterCaptor.capture()))
        .thenReturn(ImmutableList.of(hostLog));

    assertThat(nonXtsRunStrategy.getLogsDirInWorkDir(WORK_DIR).toString())
        .isEqualTo(WORK_DIR.resolve("some_dir").toString());

    DirectoryStream.Filter<Path> filter = filterCaptor.getValue();
    assertThat(filter.accept(Path.of("host_log_123.txt"))).isTrue();
    assertThat(filter.accept(Path.of("host_log_"))).isFalse();
    assertThat(filter.accept(Path.of("host_log_.txt"))).isTrue();
    assertThat(filter.accept(Path.of("host_log_abc.txt"))).isTrue();
    assertThat(filter.accept(Path.of("other_log.txt"))).isFalse();
    assertThat(filter.accept(Path.of("host_log_123.log"))).isFalse();
  }

  @Test
  public void getGenFileDir_success() throws Exception {
    assertThat(nonXtsRunStrategy.getGenFileDir(testInfo).toString())
        .isEqualTo(Path.of(testInfo.getGenFileDir(), "non-xts-gen-files").toString());
  }

  @Test
  public void getExtraJvmFlags_success() {
    assertThat(nonXtsRunStrategy.getExtraJvmFlags(WORK_DIR))
        .containsExactly("-Djava.io.tmpdir=" + WORK_DIR.resolve("tf_tmp"));
  }

  @Test
  public void getExtraRunCommandArgs_withAntsAndRdb() {
    when(systemUtil.getEnv("APPEND_ANTS_INVOCATION_DATA")).thenReturn("true");
    when(systemUtil.getEnv("APPEND_RDB_INVOCATION_DATA")).thenReturn("true");

    jobInfo.properties().add("ab_invocation_id", "test_inv_123");
    testInfo.properties().add("ab_workunit_id", "test_wu_456");
    testInfo.properties().add(PropertyName.Test.RESULTDB_INVOCATION_ID, "rdb_inv_789");
    testInfo.properties().add(PropertyName.Test.RESULTDB_UPDATE_TOKEN, "rdb_tok_abc");
    testInfo.properties().add(PropertyName.Test.RESULTDB_ROOT_INVOCATION_ID, "rdb_root_101");
    testInfo.properties().add(PropertyName.Test.RESULTDB_WORK_UNIT_ID, "rdb_wu_202");
    testInfo.properties().add(PropertyName.Test.RESULTDB_WORK_UNIT_UPDATE_TOKEN, "rdb_wu_tok_def");

    ImmutableList<String> extraArgs = nonXtsRunStrategy.getExtraRunCommandArgs(testInfo);

    assertThat(extraArgs)
        .containsExactly(
            "--invocation-data",
            "invocation_id=test_inv_123",
            "--invocation-data",
            "work_unit_id=test_wu_456",
            "--invocation-data",
            "resultdb_invocation_id=rdb_inv_789",
            "--invocation-data",
            "resultdb_invocation_update_token=rdb_tok_abc",
            "--invocation-data",
            "resultdb_root_invocation_id=rdb_root_101",
            "--invocation-data",
            "resultdb_work_unit_id=rdb_wu_202",
            "--invocation-data",
            "resultdb_work_unit_update_token=rdb_wu_tok_def")
        .inOrder();
  }

  @Test
  public void getExtraRunCommandArgs_disableAnts() {
    when(systemUtil.getEnv("APPEND_ANTS_INVOCATION_DATA")).thenReturn("false");
    when(systemUtil.getEnv("APPEND_RDB_INVOCATION_DATA")).thenReturn("true");

    jobInfo.properties().add("ab_invocation_id", "test_inv_123");
    testInfo.properties().add("ab_workunit_id", "test_wu_456");
    testInfo.properties().add(PropertyName.Test.RESULTDB_INVOCATION_ID, "rdb_inv_789");
    testInfo.properties().add(PropertyName.Test.RESULTDB_UPDATE_TOKEN, "rdb_tok_abc");

    ImmutableList<String> extraArgs = nonXtsRunStrategy.getExtraRunCommandArgs(testInfo);

    assertThat(extraArgs)
        .containsExactly(
            "--invocation-data",
            "resultdb_invocation_id=rdb_inv_789",
            "--invocation-data",
            "resultdb_invocation_update_token=rdb_tok_abc")
        .inOrder();
  }

  @Test
  public void getExtraRunCommandArgs_disableRdb() {
    when(systemUtil.getEnv("APPEND_ANTS_INVOCATION_DATA")).thenReturn("true");
    when(systemUtil.getEnv("APPEND_RDB_INVOCATION_DATA")).thenReturn("false");

    jobInfo.properties().add("ab_invocation_id", "test_inv_123");
    testInfo.properties().add("ab_workunit_id", "test_wu_456");
    testInfo.properties().add(PropertyName.Test.RESULTDB_INVOCATION_ID, "rdb_inv_789");
    testInfo.properties().add(PropertyName.Test.RESULTDB_UPDATE_TOKEN, "rdb_tok_abc");

    ImmutableList<String> extraArgs = nonXtsRunStrategy.getExtraRunCommandArgs(testInfo);

    assertThat(extraArgs)
        .containsExactly(
            "--invocation-data",
            "invocation_id=test_inv_123",
            "--invocation-data",
            "work_unit_id=test_wu_456")
        .inOrder();
  }

  @Test
  public void getExtraRunCommandArgs_defaultEnvDisabled_returnsEmpty() {
    jobInfo.properties().add("ab_invocation_id", "test_inv_123");
    testInfo.properties().add("ab_workunit_id", "test_wu_456");

    ImmutableList<String> extraArgs = nonXtsRunStrategy.getExtraRunCommandArgs(testInfo);

    assertThat(extraArgs).isEmpty();
  }
}
