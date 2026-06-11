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

package com.google.devtools.mobileharness.infra.ats.console.command.parser;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfoUtil;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CommandLineParserTest {

  @Rule public final SetFlags setFlags = new SetFlags();

  private CommandLineParser commandLineParser;

  @Before
  public void setUp() throws Exception {
    commandLineParser = new CommandLineParser();
  }

  @Test
  public void parseCommandLine_success() throws Exception {
    String tfCommand1 = "cts -m module1 -t testCase1 --shard-count 1";
    SessionRequestInfo.Builder requestInfoBuilder1 = commandLineParser.parseCommandLine(tfCommand1);

    // Fill required fields in order to build the builder.
    SessionRequestInfo requestInfo1 =
        SessionRequestInfoUtil.buildAndValidate(
            requestInfoBuilder1
                .setCommandLineArgs(tfCommand1)
                .setXtsRootDir("xts_root_dir")
                .setXtsType("cts"));

    assertThat(requestInfo1.getTestPlan()).isEqualTo("cts");
    assertThat(requestInfo1.getModuleNamesList()).containsExactly("module1");
    assertThat(
            requestInfo1.hasTestName() ? Optional.of(requestInfo1.getTestName()) : Optional.empty())
        .hasValue("testCase1");
    assertThat(
            requestInfo1.hasShardCount()
                ? Optional.of(requestInfo1.getShardCount())
                : Optional.empty())
        .hasValue(1);
  }

  @Test
  public void parseCommandLine_embeddedMode_success() throws Exception {
    setFlags.set("ats_console_olc_server_embedded_mode", "true");
    commandLineParser = new CommandLineParser();

    String tfCommand1 = "cts -m module1 -t testCase1 --shard-count 1";
    SessionRequestInfo.Builder requestInfoBuilder1 = commandLineParser.parseCommandLine(tfCommand1);

    // Fill required fields in order to build the builder.
    SessionRequestInfo requestInfo1 =
        SessionRequestInfoUtil.buildAndValidate(
            requestInfoBuilder1
                .setCommandLineArgs(tfCommand1)
                .setXtsRootDir("xts_root_dir")
                .setXtsType("cts"));

    assertThat(requestInfo1.getTestPlan()).isEqualTo("cts");
    assertThat(requestInfo1.getModuleNamesList()).containsExactly("module1");
    assertThat(
            requestInfo1.hasTestName() ? Optional.of(requestInfo1.getTestName()) : Optional.empty())
        .hasValue("testCase1");
    assertThat(
            requestInfo1.hasShardCount()
                ? Optional.of(requestInfo1.getShardCount())
                : Optional.empty())
        .hasValue(1);
  }

  @Test
  public void parseCommandLine_withFilters_success() throws Exception {
    String tfCommand1 =
        "cts --shard-count 1 --include-filter \"test_module_name1 test_name1\" --exclude-filter"
            + " \"test_module_name2 test_name2\"";
    SessionRequestInfo.Builder requestInfoBuilder1 = commandLineParser.parseCommandLine(tfCommand1);

    // Fill required fields in order to build the builder.
    SessionRequestInfo requestInfo1 =
        SessionRequestInfoUtil.buildAndValidate(
            requestInfoBuilder1
                .setCommandLineArgs(tfCommand1)
                .setXtsRootDir("xts_root_dir")
                .setXtsType("cts"));

    assertThat(requestInfo1.getTestPlan()).isEqualTo("cts");
    assertThat(requestInfo1.getIncludeFiltersList())
        .containsExactly("test_module_name1 test_name1");
    assertThat(requestInfo1.getExcludeFiltersList())
        .containsExactly("test_module_name2 test_name2");
    assertThat(
            requestInfo1.hasShardCount()
                ? Optional.of(requestInfo1.getShardCount())
                : Optional.empty())
        .hasValue(1);
  }

  @Test
  public void parseCommandLine_retryCommand_success() throws Exception {
    String tfCommand1 =
        "retry --retry 0 --retry-type NOT_EXECUTED --exclude-filter"
            + " \"test_module_name2 test_name2\"";
    SessionRequestInfo.Builder requestInfoBuilder1 = commandLineParser.parseCommandLine(tfCommand1);

    // Fill required fields in order to build the builder.
    SessionRequestInfo requestInfo1 =
        SessionRequestInfoUtil.buildAndValidate(
            requestInfoBuilder1
                .setCommandLineArgs(tfCommand1)
                .setXtsRootDir("xts_root_dir")
                .setXtsType("cts")
                .setRetrySessionId("retry_session_id")
                .setRetryResultDir("retry_result_dir"));

    assertThat(requestInfo1.getTestPlan()).isEqualTo("retry");
    assertThat(requestInfo1.getRetryType().toString()).isEqualTo("NOT_EXECUTED");
    assertThat(requestInfo1.getExcludeFiltersList())
        .containsExactly("test_module_name2 test_name2");
  }

  @Test
  public void parseCommandLine_multipleTimes_success() throws Exception {
    String tfCommand1 = "cts -m module1 -t testCase1 --shard-count 1";
    String tfCommand2 = "cts -m module2 -t testCase2 --shard-count 2";
    SessionRequestInfo.Builder requestInfoBuilder1 = commandLineParser.parseCommandLine(tfCommand1);
    SessionRequestInfo.Builder requestInfoBuilder2 = commandLineParser.parseCommandLine(tfCommand2);

    // Fill required fields in order to build the builder.
    SessionRequestInfo requestInfo1 =
        SessionRequestInfoUtil.buildAndValidate(
            requestInfoBuilder1
                .setTestPlan("cts")
                .setCommandLineArgs(tfCommand1)
                .setXtsRootDir("xts_root_dir")
                .setXtsType("cts"));
    SessionRequestInfo requestInfo2 =
        SessionRequestInfoUtil.buildAndValidate(
            requestInfoBuilder2
                .setTestPlan("cts")
                .setCommandLineArgs(tfCommand2)
                .setXtsRootDir("xts_root_dir")
                .setXtsType("cts"));

    assertThat(requestInfo1.getModuleNamesList()).containsExactly("module1");
    assertThat(
            requestInfo1.hasTestName() ? Optional.of(requestInfo1.getTestName()) : Optional.empty())
        .hasValue("testCase1");
    assertThat(
            requestInfo1.hasShardCount()
                ? Optional.of(requestInfo1.getShardCount())
                : Optional.empty())
        .hasValue(1);
    assertThat(requestInfo2.getModuleNamesList()).containsExactly("module2");
    assertThat(
            requestInfo2.hasTestName() ? Optional.of(requestInfo2.getTestName()) : Optional.empty())
        .hasValue("testCase2");
    assertThat(
            requestInfo2.hasShardCount()
                ? Optional.of(requestInfo2.getShardCount())
                : Optional.empty())
        .hasValue(2);
  }

  @Test
  public void parseCommandLine_commandSyntaxError_throwException() {
    String tfCommand1 = "cts -m module1 -t testCase1 --shard-count";
    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> commandLineParser.parseCommandLine(tfCommand1));
    assertThat(exception)
        .hasMessageThat()
        .contains("The command line contains syntax error: " + tfCommand1);
    assertThat(exception.getErrorId()).isEqualTo(InfraErrorId.ATS_SERVER_INVALID_REQUEST_ERROR);
  }
}
