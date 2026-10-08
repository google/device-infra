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
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil.XtsTradefedRuntimeInfoFileDetail;
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
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
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
  @Mock private XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil;
  private TestInfo testInfo;
  private JobInfo jobInfo;
  @Mock private Device device;

  private static final Path WORK_DIR = Path.of("/path/to/work");
  private static final String TRADEFED_DIR = "/path/to/tradefed";
  private static final String RESULT_FROM_INVOCATION_FLAG =
      "enable_non_xts_tradefed_result_from_invocation";
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
    nonXtsRunStrategy =
        new NonXtsRunStrategy(localFileUtil, systemUtil, xtsTradefedRuntimeInfoFileUtil);
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

  @Test
  public void getExtraRunCommandArgs_withShardCountAndIndex() {
    int shardCount = 10;
    int shardIndex = 2;
    jobInfo.params().add("ate_shard_count", Integer.toString(shardCount));
    jobInfo.params().add("ate_shard_index", Integer.toString(shardIndex));

    ImmutableList<String> extraArgs = nonXtsRunStrategy.getExtraRunCommandArgs(testInfo);

    assertThat(extraArgs)
        .containsExactly(
            "--shard-count",
            Integer.toString(shardCount),
            "--shard-index",
            Integer.toString(shardIndex))
        .inOrder();
  }

  @Test
  public void getExtraRunCommandArgs_withShardCountOne_doesNotAddShardArgs() {
    jobInfo.params().add("ate_shard_count", "1");
    jobInfo.params().add("ate_shard_index", "0");

    ImmutableList<String> extraArgs = nonXtsRunStrategy.getExtraRunCommandArgs(testInfo);

    assertThat(extraArgs).isEmpty();
  }

  @Test
  public void setTestResult_flagDisabled_exitCodeZero_pass() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "false");
    mockFailedInvocation("com.android.tradefed.build.BuildRetrievalError: no build");

    nonXtsRunStrategy.setTestResult(testInfo, Optional.of(0));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(testInfo.properties().has(XtsConstants.TRADEFED_INVOCATION_ERROR)).isFalse();
  }

  @Test
  public void setTestResult_exitCodeEmpty_error() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "true");

    nonXtsRunStrategy.setTestResult(testInfo, Optional.empty());

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(getResultErrorId()).isEqualTo(AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR);
    assertThat(getResultErrorMessage()).contains("Tradefed command didn't start");
  }

  @Test
  public void setTestResult_exitCodeNonZero_error() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "true");

    nonXtsRunStrategy.setTestResult(testInfo, Optional.of(1));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(getResultErrorId()).isEqualTo(AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR);
    assertThat(getResultErrorMessage()).contains("Non-zero Tradefed command exit code: 1");
  }

  @Test
  public void setTestResult_noRuntimeFiles_pass() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "true");
    when(localFileUtil.isFileExist(any(Path.class))).thenReturn(false);

    nonXtsRunStrategy.setTestResult(testInfo, Optional.of(0));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
  }

  @Test
  public void setTestResult_resultAlreadySet_keepsExistingResult() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "true");
    mockFailedInvocation("com.android.tradefed.build.BuildRetrievalError: no build");
    testInfo
        .resultWithCause()
        .setNonPassing(
            TestResult.TIMEOUT,
            new MobileHarnessException(AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR, "timeout"));

    nonXtsRunStrategy.setTestResult(testInfo, Optional.of(0));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.TIMEOUT);
    assertThat(testInfo.properties().has(XtsConstants.TRADEFED_INVOCATION_ERROR)).isFalse();
  }

  @Test
  public void setTestResult_invocationError_errorWithRawTradefedMessage() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "true");
    mockFailedInvocation(
        "com.android.tradefed.build.BuildRetrievalError: Failed to download build\n"
            + "\tat com.android.tradefed.build.FileDownloadCache.fetch(FileDownloadCache.java:1)");

    nonXtsRunStrategy.setTestResult(testInfo, Optional.of(0));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(getResultErrorId()).isEqualTo(AndroidErrorId.XTS_TRADEFED_INVOCATION_ERROR);
    assertThat(getResultErrorMessage())
        .contains(
            "Tradefed invocation failed: [device1]"
                + " com.android.tradefed.build.BuildRetrievalError: Failed to download build");
    assertThat(getResultErrorMessage()).contains(XtsConstants.TRADEFED_OUTPUT_FILE_NAME);
    assertThat(testInfo.properties().get(XtsConstants.TRADEFED_INVOCATION_ERROR))
        .isEqualTo(
            "[device1] com.android.tradefed.build.BuildRetrievalError: Failed to download build");
  }

  @Test
  public void setTestResult_invocationDeviceError_error() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "true");
    mockFailedInvocation(
        "com.android.tradefed.device.DeviceNotAvailableException: device1 not available");

    nonXtsRunStrategy.setTestResult(testInfo, Optional.of(0));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(getResultErrorId()).isEqualTo(AndroidErrorId.XTS_TRADEFED_INVOCATION_ERROR);
    assertThat(getResultErrorMessage())
        .contains("DeviceNotAvailableException: device1 not available");
  }

  @Test
  public void setTestResult_runningInvocationWithoutError_ignored() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "true");
    mockRuntimeInfo(
        new XtsTradefedRuntimeInfo(
            ImmutableList.of(
                new TradefedInvocation(
                    /* isRunning= */ true, ImmutableList.of("device1"), "running", ""),
                new TradefedInvocation(
                    /* isRunning= */ false, ImmutableList.of("device1"), "done", "")),
            Instant.now()));

    nonXtsRunStrategy.setTestResult(testInfo, Optional.of(0));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
  }

  @Test
  public void setTestResult_runtimeInfoReadFails_pass() throws Exception {
    flags.set(RESULT_FROM_INVOCATION_FLAG, "true");
    Path runtimeInfoPath = genFile(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_NAME);
    when(localFileUtil.isFileExist(runtimeInfoPath)).thenReturn(true);
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(runtimeInfoPath, null))
        .thenThrow(new IOException("read error"));

    nonXtsRunStrategy.setTestResult(testInfo, Optional.of(0));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
  }

  @Test
  public void summarizeInvocationErrors_firstLineOnly_joinsAndTruncates() {
    String longMessage = "x".repeat(2000);
    ImmutableList.Builder<TradefedInvocation> invocations = ImmutableList.builder();
    invocations.add(
        new TradefedInvocation(
            /* isRunning= */ false,
            ImmutableList.of("d1", "d2"),
            "",
            "first line\n\tat second line"));
    for (int i = 0; i < 5; i++) {
      invocations.add(
          new TradefedInvocation(/* isRunning= */ false, ImmutableList.of(), "", longMessage));
    }

    String summary = NonXtsRunStrategy.summarizeInvocationErrors(invocations.build());

    assertThat(summary).startsWith("[d1,d2] first line; xxx");
    assertThat(summary).doesNotContain("second line");
    assertThat(summary).endsWith("...");
    assertThat(summary.length()).isEqualTo(1000 + "...".length());
  }

  @Test
  public void summarizeInvocationErrors_moreThanLimit_appendsRemainingCount() {
    ImmutableList.Builder<TradefedInvocation> invocations = ImmutableList.builder();
    for (int i = 0; i < 7; i++) {
      invocations.add(
          new TradefedInvocation(/* isRunning= */ false, ImmutableList.of(), "", "e" + i));
    }

    assertThat(NonXtsRunStrategy.summarizeInvocationErrors(invocations.build()))
        .isEqualTo("e0; e1; e2; e3; e4; ... (2 more)");
  }

  private Path genFile(String fileName) throws Exception {
    return Path.of(testInfo.getGenFileDir()).resolve(fileName);
  }

  private void mockRuntimeInfo(XtsTradefedRuntimeInfo runtimeInfo) throws Exception {
    Path runtimeInfoPath = genFile(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_NAME);
    when(localFileUtil.isFileExist(runtimeInfoPath)).thenReturn(true);
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(runtimeInfoPath, null))
        .thenReturn(Optional.of(new XtsTradefedRuntimeInfoFileDetail(runtimeInfo, Instant.now())));
  }

  private void mockFailedInvocation(String errorMessage) throws Exception {
    mockRuntimeInfo(
        new XtsTradefedRuntimeInfo(
            ImmutableList.of(
                new TradefedInvocation(
                    /* isRunning= */ false, ImmutableList.of("device1"), "done", errorMessage)),
            Instant.now()));
  }

  private ErrorId getResultErrorId() {
    return testInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId();
  }

  private String getResultErrorMessage() {
    return testInfo.resultWithCause().get().causeExceptionNonEmpty().getMessage();
  }
}
