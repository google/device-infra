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

package com.google.devtools.mobileharness.shared.util.system.constant;

/** Exit codes for Universal Device Cluster. */
public interface ExitCode {
  /** Exit codes which are shared by different components. The code should be between 0 ~ 29. */
  enum Shared implements ExitCode {
    OK(0),
    VERSION_ERROR(1),
    CODING_ERROR(2),
    FILE_OPERATION_ERROR(3),
    PERISCOPE_ERROR(4), // deprecated
    API_CONFIG_ERROR(5),
    ANDROID_SDK_ERROR(6),
    IOS_TOOL_DEPLOYMENT_ERROR(7),
    USER_ERROR(8),
    OPENURL_ERROR(9),
    MONSOON_TOOL_ERROR(10),
    BIGTABLE_ERROR(11),
    ACCESS_ERROR(12),
    SCHEDULER_ERROR(13),
    CLOUD_RPC_ERROR(14),
    IOS_INVALID_CERT_ERROR(15),
    BATTOR_TOOL_ERROR(16),
    RES_NOT_FOUND(17),
    RELEASE_SERVER_SPEC_ERROR(18),
    FILE_TRANSFER_SERVICE_ERROR(19),
    LAMEDUCK_ERROR(20),
    SPANNER_ERROR(21),
    DEBUG_ERROR(22);

    private final int code;

    private Shared(int exitCode) {
      code = exitCode;
    }

    @Override
    public int code() {
      return code;
    }
  }

  /** Exit codes used by Lab Server V4. The code should be between 30 ~ 59. */
  enum Lab implements ExitCode {
    NO_DETECTOR(30),
    APACHE_ERROR(31),
    GSE_ERROR(32),
    MAC_RES_ERROR(33),
    RUN_AS_ROOT(34),
    INIT_ERROR(35);

    private final int code;

    private Lab(int exitCode) {
      code = exitCode;
    }

    @Override
    public int code() {
      return code;
    }
  }

  /** Exit codes used by Client V4. The code should be between 60 ~ 89. */
  enum Client implements ExitCode {
    TEST_FAILURE(60),
    JOB_INFO_ERROR(61),
    START_JOB_ERROR(62),
    JOB_SPEC_ERROR(63),
    PARAMS_FORMAT_ERROR(64),
    INVALID_FLAG_ERROR(65);

    private final int code;

    private Client(int exitCode) {
      code = exitCode;
    }

    @Override
    public int code() {
      return code;
    }
  }

  /** Exit codes used by Daemon V4. The code should be between 90 ~ 119. */
  enum Daemon implements ExitCode {
    SOCKET_SERVER_ERROR(90), // For MH backward compatibility only, do NOT use in UDCluster daemon
    MASTER_SYNC_ERROR(91),
    PORT_ERROR(92),
    RELEASE_SERVER_ERROR(93),
    COMMAND_SERVER_ERROR(94),
    RELEASE_SYNC_ERROR(95),
    GRPC_SERVER_ERROR(96),
    EXIT_ERROR(97);

    private final int code;

    private Daemon(int code) {
      this.code = code;
    }

    @Override
    public int code() {
      return code;
    }
  }

  /** Exit codes used by Integration test. The code should be between 120 ~ 149. */
  enum Integration implements ExitCode {
    FLAG_SET_ERROR(120);

    private final int code;

    private Integration(int code) {
      this.code = code;
    }

    @Override
    public int code() {
      return code;
    }
  }

  /** Exit codes used by Release server. The code should be between 150 ~ 179. */
  enum Release implements ExitCode {
    SPANNER_ERROR(151);

    private final int code;

    private Release(int code) {
      this.code = code;
    }

    @Override
    public int code() {
      return code;
    }
  }

  /** Exit codes used by Command Server. The code should be between 180 ~ 199. */
  enum CommandServer implements ExitCode {
    GRPC_SERVER_ERROR(180);

    private final int code;

    private CommandServer(int code) {
      this.code = code;
    }

    @Override
    public int code() {
      return code;
    }
  }

  /** Returns the exit code. */
  int code();

  /** Returns the name of the exit code. */
  String name();
}
