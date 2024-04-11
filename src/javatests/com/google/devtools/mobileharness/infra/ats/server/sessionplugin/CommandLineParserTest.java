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

package com.google.devtools.mobileharness.infra.ats.server.sessionplugin;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CommandLineParserTest {

  CommandLineParser commandLineParser;

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
        requestInfoBuilder1
            .setTestPlan("cts")
            .setCommandLineArgs(tfCommand1)
            .setXtsRootDir("xts_root_dir")
            .setXtsType("cts")
            .build();

    assertThat(requestInfo1.moduleNames()).containsExactly("module1");
    assertThat(requestInfo1.testName()).hasValue("testCase1");
    assertThat(requestInfo1.shardCount()).hasValue(1);
  }

  @Test
  public void parseCommandLine_withFilters_success() throws Exception {
    String tfCommand1 =
        "cts --shard-count 1 --include-filter \"test_module_name1 test_name1\" --exclude-filter"
            + " \"test_module_name2 test_name2\"";
    SessionRequestInfo.Builder requestInfoBuilder1 = commandLineParser.parseCommandLine(tfCommand1);

    // Fill required fields in order to build the builder.
    SessionRequestInfo requestInfo1 =
        requestInfoBuilder1
            .setTestPlan("cts")
            .setCommandLineArgs(tfCommand1)
            .setXtsRootDir("xts_root_dir")
            .setXtsType("cts")
            .build();

    assertThat(requestInfo1.includeFilters()).containsExactly("test_module_name1 test_name1");
    assertThat(requestInfo1.excludeFilters()).containsExactly("test_module_name2 test_name2");
    assertThat(requestInfo1.shardCount()).hasValue(1);
  }

  @Test
  public void parseCommandLine_multipleTimes_success() throws Exception {
    String tfCommand1 = "cts -m module1 -t testCase1 --shard-count 1";
    String tfCommand2 = "cts -m module2 -t testCase2 --shard-count 2";
    SessionRequestInfo.Builder requestInfoBuilder1 = commandLineParser.parseCommandLine(tfCommand1);
    SessionRequestInfo.Builder requestInfoBuilder2 = commandLineParser.parseCommandLine(tfCommand2);

    // Fill required fields in order to build the builder.
    SessionRequestInfo requestInfo1 =
        requestInfoBuilder1
            .setTestPlan("cts")
            .setCommandLineArgs(tfCommand1)
            .setXtsRootDir("xts_root_dir")
            .setXtsType("cts")
            .build();
    SessionRequestInfo requestInfo2 =
        requestInfoBuilder2
            .setTestPlan("cts")
            .setCommandLineArgs(tfCommand2)
            .setXtsRootDir("xts_root_dir")
            .setXtsType("cts")
            .build();

    assertThat(requestInfo1.moduleNames()).containsExactly("module1");
    assertThat(requestInfo1.testName()).hasValue("testCase1");
    assertThat(requestInfo1.shardCount()).hasValue(1);
    assertThat(requestInfo2.moduleNames()).containsExactly("module2");
    assertThat(requestInfo2.testName()).hasValue("testCase2");
    assertThat(requestInfo2.shardCount()).hasValue(2);
  }

  @Test
  public void parseCommandLine_commandSyntaxError_throwExcpetion() throws Exception {
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
