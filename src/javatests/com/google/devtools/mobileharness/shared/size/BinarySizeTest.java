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

package com.google.devtools.mobileharness.shared.size;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.shared.util.file.local.BinarySizeChecker;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BinarySizeTest {

  private static final ImmutableSet<String> BASE_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST =
      ImmutableSet.of();
  private static final ImmutableSet<String> ATS_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST =
      ImmutableSet.of();
  private static final ImmutableSet<String>
      ATS_OLC_SERVER_LOCAL_MODE_LARGE_RESOURCE_PATH_ALLOWLIST =
          ImmutableSet.of(
              "com/google/devtools/mobileharness/platform/android/xts/agent/tradefed_invocation_agent_deploy.jar");
  private static final ImmutableSet<String> LAB_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST =
      ImmutableSet.of(
          "com/google/devtools/mobileharness/platform/android/xts/agent/tradefed_invocation_agent_deploy.jar");
  private static final ImmutableSet<String> ATS_CONSOLE_LARGE_RESOURCE_PATH_ALLOWLIST =
      ImmutableSet.of();

  // ================================================================================
  // Please keep all binary sizes below in the precision "xxx_x50_000L" bytes.
  // ================================================================================
  private static final long MAX_BASE_OLC_SERVER_BINARY_SIZE_BYTE = 29_350_000L;
  private static final long MAX_ATS_OLC_SERVER_BINARY_SIZE_BYTE = 38_350_000L;
  private static final long MAX_ATS_OLC_SERVER_LOCAL_MODE_BINARY_SIZE_BYTE = 40_350_000L;
  private static final long MAX_LAB_SERVER_BINARY_SIZE_BYTE = 37_350_000L;
  private static final long MAX_ATS_CONSOLE_BINARY_SIZE_BYTE = 23_350_000L;
  private static final long MAX_XTS_TRADEFED_AGENT_BINARY_SIZE_BYTE = 4_550_000L;

  private static final long MAX_RESOURCE_FILE_SIZE_BYTE = 800_000L;

  private static final String BINARY_SIZE_TEST_SOURCE_PATH =
      "google3/third_party/deviceinfra/src/javatests/com/google/devtools/mobileharness"
          + "/shared/size/BinarySizeTest.java";

  private static final String BASE_OLC_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/shared/size/base_olc_server_deploy.jar");
  private static final String BASE_OLC_SERVER_BINARY_SOURCE_PATH =
      "google3/third_party/deviceinfra/src/javatests/com/google/devtools/mobileharness"
          + "/shared/size:base_olc_server_deploy.jar";

  private static final String ATS_OLC_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness"
              + "/infra/ats/common/olcserver/ats_olc_server_deploy.jar");
  private static final String ATS_OLC_SERVER_BINARY_SOURCE_PATH =
      "google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness"
          + "/infra/ats/common/olcserver:ats_olc_server_deploy.jar";

  private static final String ATS_OLC_SERVER_LOCAL_MODE_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness"
              + "/infra/ats/common/olcserver/ats_olc_server_local_mode_deploy.jar");
  private static final String ATS_OLC_SERVER_LOCAL_MODE_BINARY_SOURCE_PATH =
      "google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness"
          + "/infra/ats/common/olcserver:ats_olc_server_local_mode_deploy.jar";

  private static final String LAB_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/infra/lab/lab_server_oss_deploy.jar");
  private static final String LAB_SERVER_BINARY_SOURCE_PATH =
      "google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness/infra"
          + "/lab:lab_server_oss_deploy.jar";

  private static final String ATS_CONSOLE_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/infra/ats/console/ats_console_deploy.jar");
  private static final String ATS_CONSOLE_BINARY_SOURCE_PATH =
      "google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness"
          + "/infra/ats/console:ats_console_deploy.jar";

  private static final String XTS_TRADEFED_AGENT_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/platform/"
              + "android/xts/agent/tradefed_invocation_agent_deploy.jar");
  private static final String XTS_TRADEFED_AGENT_SOURCE_PATH =
      "google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness/platform/"
          + "android/xts/agent:tradefed_invocation_agent_deploy.jar";

  @Test
  public void checkBaseOlcServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "base_olc_server_deploy.jar",
        MAX_BASE_OLC_SERVER_BINARY_SIZE_BYTE,
        BASE_OLC_SERVER_BINARY_FILE_PATH,
        BASE_OLC_SERVER_BINARY_SOURCE_PATH);
  }

  @Test
  public void checkBaseOlcServerBinaryLargeResources() throws Exception {
    BinarySizeChecker.checkBinaryLargeResourceFiles(
        "olc_server_for_testing_deploy.jar",
        BASE_OLC_SERVER_BINARY_FILE_PATH,
        MAX_RESOURCE_FILE_SIZE_BYTE,
        BASE_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST,
        BASE_OLC_SERVER_BINARY_SOURCE_PATH,
        BINARY_SIZE_TEST_SOURCE_PATH + "#BASE_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST");
  }

  @Test
  public void checkAtsOlcServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "ats_olc_server_deploy.jar",
        MAX_ATS_OLC_SERVER_BINARY_SIZE_BYTE,
        ATS_OLC_SERVER_BINARY_FILE_PATH,
        ATS_OLC_SERVER_BINARY_SOURCE_PATH);
  }

  @Test
  public void checkAtsOlcServerBinaryLargeResources() throws Exception {
    BinarySizeChecker.checkBinaryLargeResourceFiles(
        "ats_olc_server_deploy.jar",
        ATS_OLC_SERVER_BINARY_FILE_PATH,
        MAX_RESOURCE_FILE_SIZE_BYTE,
        ATS_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST,
        ATS_OLC_SERVER_BINARY_SOURCE_PATH,
        BINARY_SIZE_TEST_SOURCE_PATH + "#ATS_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST");
  }

  @Test
  public void checkAtsOlcServerLocalModeBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "ats_olc_server_local_mode_deploy.jar",
        MAX_ATS_OLC_SERVER_LOCAL_MODE_BINARY_SIZE_BYTE,
        ATS_OLC_SERVER_LOCAL_MODE_BINARY_FILE_PATH,
        ATS_OLC_SERVER_LOCAL_MODE_BINARY_SOURCE_PATH);
  }

  @Test
  public void checkAtsOlcServerLocalModeBinaryLargeResources() throws Exception {
    BinarySizeChecker.checkBinaryLargeResourceFiles(
        "ats_olc_server_local_mode_deploy.jar",
        ATS_OLC_SERVER_LOCAL_MODE_BINARY_FILE_PATH,
        MAX_RESOURCE_FILE_SIZE_BYTE,
        ATS_OLC_SERVER_LOCAL_MODE_LARGE_RESOURCE_PATH_ALLOWLIST,
        ATS_OLC_SERVER_LOCAL_MODE_BINARY_SOURCE_PATH,
        BINARY_SIZE_TEST_SOURCE_PATH + "#ATS_OLC_SERVER_LOCAL_MODE_LARGE_RESOURCE_PATH_ALLOWLIST");
  }

  @Test
  public void checkLabServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "lab_server_oss_deploy.jar",
        MAX_LAB_SERVER_BINARY_SIZE_BYTE,
        LAB_SERVER_BINARY_FILE_PATH,
        LAB_SERVER_BINARY_SOURCE_PATH);
  }

  @Test
  public void checkLabServerBinaryLargeResources() throws Exception {
    BinarySizeChecker.checkBinaryLargeResourceFiles(
        "lab_server_oss_deploy.jar",
        LAB_SERVER_BINARY_FILE_PATH,
        MAX_RESOURCE_FILE_SIZE_BYTE,
        LAB_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST,
        LAB_SERVER_BINARY_SOURCE_PATH,
        BINARY_SIZE_TEST_SOURCE_PATH + "#LAB_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST");
  }

  @Test
  public void checkAtsConsoleBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "ats_console_deploy.jar",
        MAX_ATS_CONSOLE_BINARY_SIZE_BYTE,
        ATS_CONSOLE_BINARY_FILE_PATH,
        ATS_CONSOLE_BINARY_SOURCE_PATH);
  }

  @Test
  public void checkAtsConsoleBinaryLargeResources() throws Exception {
    BinarySizeChecker.checkBinaryLargeResourceFiles(
        "ats_console_deploy.jar",
        ATS_CONSOLE_BINARY_FILE_PATH,
        MAX_RESOURCE_FILE_SIZE_BYTE,
        ATS_CONSOLE_LARGE_RESOURCE_PATH_ALLOWLIST,
        ATS_CONSOLE_BINARY_SOURCE_PATH,
        BINARY_SIZE_TEST_SOURCE_PATH + "#ATS_CONSOLE_LARGE_RESOURCE_PATH_ALLOWLIST");
  }

  @Test
  public void checkXtsTradefedAgentBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "xts_tradefed_agent_deploy.jar",
        MAX_XTS_TRADEFED_AGENT_BINARY_SIZE_BYTE,
        XTS_TRADEFED_AGENT_PATH,
        XTS_TRADEFED_AGENT_SOURCE_PATH);
  }
}
