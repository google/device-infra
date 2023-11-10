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

package com.google.wireless.qa.mobileharness.shared.constant;

/** Mobile Harness property name constants. */
public interface PropertyName {

  /** Mobile Harness job property name constants. */
  enum Job implements PropertyName {
    /** Name of the job property that contains the first test allocation time in seconds. */
    FIRST_TEST_ALLOCATION_TIME_SEC,

    /** The pre-run job time in milliseconds. */
    PRE_RUN_JOB_TIME_MS,

    /** The actual user LDAP rather than run_as. */
    ACTUAL_USER,

    /** The run as user. */
    RUN_AS,

    /** The changelist number. */
    CL,

    /** The build id. */
    BUILD_ID,

    /** Execution mode (lower case of ExecModeUtil.getModeName()). */
    EXEC_MODE,

    /** Session id. */
    SESSION_ID,

    /** Sponge link of test. */
    SPONGE_LINK_OF_TEST,

    CLIENT_VERSION,

    CLIENT_HOSTNAME,

    /** Client type. Examples: moscar_native_executor, mobile_test_bin. */
    CLIENT_TYPE,

    /** The MHFE link for the job. */
    JOB_LINK_IN_MHFE,

    // The streamz label for the job. */
    STREAMZ_LABEL,

    /** The trace id for the job. */
    TRACE_ID,

    /** The stack driver trace link for the job. */
    STACK_DRIVER_TRACE_LINK,

    /** The job allocation fails after start_timeout is reached. */
    ALLOCATION_FAIL_AFTER_START_TIMEOUT,

    /**
     * Whether it is a resumed job from a previous interrupted one
     * (http://go/mh-longevity-short-term-desi
     */
    _IS_RESUMED_JOB,

    /** Whether the job is resumable. (http://go/mh-longevity-short-term-design). */
    IS_RESUMABLE,

    /** The parameters to be recorded from mobile_test macro to the job creation. */
    _PARAM_STATS,

    /** Whether upload runfiles to CNS. */
    UPLOAD_RUNFILES,

    /** The master spec. */
    MASTER_SPEC
  }

  /** Mobile Harness test property name constants. */
  enum Test implements PropertyName {
    /**
     * The deep-dive classification of allocation failure. The value is the name of the allocation
     * failure error ids.
     */
    ALLOCATION_FAILURE_CLASSIFICATION,

    /**
     * Only available in Remote Mode. This is the epoch time when a test successfully allocated a
     * device. For b/71722259.
     */
    ALLOCATION_RECEIVED_EPOCH_MS,

    /**
     * Only available in Remote Mode. This is the epoch time when a pending test is firstly reported
     * to Master. For b/71722259.
     */
    ALLOCATION_REQUESTED_EPOCH_MS,

    /**
     * Recording the duration between the test starting and the test getting allocation in
     * milliseconds.
     */
    ALLOCATION_TIME_MS,

    /**
     * Recording the duration between the test starting and the test getting allocation in seconds.
     */
    ALLOCATION_TIME_SEC,

    /** Android instrumentation test method repeat times. */
    ANDROID_INST_TEST_METHOD_REPEAT_TIMES,

    /** Whether the test runs in MH container mode. Value type is boolean. */
    CONTAINER_MODE,

    /** Only available in Remote Mode. This is the duration the device is occupied for this test. */
    DEVICE_USAGE_DURATION_MS,

    /** The comma separated string of device ids to indicate devices order in the this test. */
    DEVICE_ID_LIST,

    /** The id of the device which runs the test. */
    DIMENSION_ID,

    /** The test id who initiates this test. */
    FOREGOING_TEST_ID,

    /** The result (name()) of the test who initiates this retry test. */
    FOREGOING_TEST_RESULT,

    /** Boolean. True to indicate that the test has user specified UTP configs (e.g., plugins). */
    HAS_USER_UTP_CONFIG,

    /**
     * Present to indicate that hybrid UTP mode will be forcibly disabled for the test.
     *
     * <p>Its value should be a {@code InfraIncompatibleReason}'s lowercase name.
     */
    HYBRID_UTP_FORCIBLY_DISABLE,

    /**
     * Reasons why the test can not run in hybrid UTP mode.
     *
     * <p>Its value is a sorted concatenated string, in which each part is a formatted lowercase
     * {@code IncompatibleReason} with context and the delimiter is "{@code ;}".
     */
    HYBRID_UTP_SUMMARY_INCOMPATIBLE_REASON,

    /** Whether the test attempt is the final one. */
    IS_FINAL_ATTEMPT,

    /** The local path of the test gen file dir of the lab side (local) test runner. */
    LAB_TEST_GEN_FILE_DIR,

    /**
     * The name of the test property which indicates that the current test is non-passing, but will
     * trigger a retry and get pass. Values of this test property: true or null.
     */
    NONPASSING_BEFORE_RETRY_PASS,

    /**
     * The name of the test property which indicates that one of the Oxygen devices we leased is
     * prewarmed.
     */
    OXYGEN_DEVICE_IS_PREWARMED,

    /**
     * The name of the test property which indicates the virtualization type of this Oxygen test.
     */
    OXYGEN_VIRTUALIZATION_TYPE,

    /**
     * The name of the test property which indicates that the current test is pass after a
     * non-passing attempt. Values of this test property: true or null.
     */
    PASS_AFTER_RETRY,

    /**
     * Only present in remote-mode tests. Execution time between execute() invocation and
     * postRunTest() of the remote test runner in milliseconds.
     */
    REMOTE_EXECUTION_TIME_MS,

    /**
     * Only present in remote-mode tests, delay time between start() and execute() of the test
     * runner on lab server side in milliseconds.
     */
    REMOTE_START_DELAY_MS,

    /**
     * Only present in remote-mode and container-mode tests. Time it takes to setup test engine in
     * milliseconds.
     */
    REMOTE_TEST_ENGINE_SETUP_TIME_MS,

    /** Only present when --repeat_runs (or RETRY_LEVEL=ALL) is set. Started from 1. */
    REPEAT_INDEX,

    /** Whether the test is started because a forgoing container-mode test fails. */
    RETRY_AFTER_CONTAINER_FAILS,

    /** Whether the test is started because a forgoing sandbox-mode test fails. */
    RETRY_AFTER_SANDBOX_FAILS,

    /**
     * Whether the test fails with
     * AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED.
     */
    RETRY_AFTER_NO_VALID_UID_ASSIGNED,

    /**
     * Retry index. Only count valid attempts. Null for the first attempt and starts from 1. Eg. the
     * second attempt with 1 previous valid attempt is the first retry and its retry index is 1.
     */
    RETRY_INDEX,

    /**
     * The name of the test property indicates that the current test is a retry run. The property
     * value will be the brief reason.
     */
    RETRY_REASON,

    /** The ID of another new test, when the current test is retried by the new test. */
    RETRY_TEST_ID,

    /** Whether the test runs in MH sandbox mode. Value type is boolean. */
    SANDBOX_MODE,

    /** Delay time between start() and execute() of the test runner in milliseconds. */
    START_DELAY_MS,

    /** The MHFE link for the test. */
    TEST_LINK_IN_MHFE,

    /**
     * The current UTP mode. Values are: {@linkplain
     * java/com/google/devtools/mobileharness/infra/controller/test/local/utp/common/UtpMode.java}.
     */
    UTP_MODE,

    /**
     * Whether the information of the test is volatile and may be modified by other tests or the job
     * after itself ends, and the modified information also needs to be uploaded to storage service.
     * Value type is boolean, by default is false.
     */
    VOLATILE_TEST_INFO_AFTER_TEST_ENDS,

    /**
     * Present to indicate that device preRunTest and postRunTest will be forcibly disabled for the
     * {@code com.google.devtools.mobileharness.infra.controller.test.local.LocalTestFlow}.
     */
    LOCAL_TEST_DISABLE_DEVICE_PRE_RUN_POST_RUN,

    /**
     * Present to indicate that preRunTest of NoOpDevice has been called.
     *
     * <p>ONLY used in {@code
     * com.google.devtools.deviceinfra.host.utrs.controller.testrunner.backend
     * .UtrsHtrApiIntegrationTest}.
     */
    NO_OP_DEVICE_PRE_RUN_TEST_CALLED,

    /**
     * Present to indicate that postRunTest of NoOpDevice has been called.
     *
     * <p>ONLY used in {@code
     * com.google.devtools.deviceinfra.host.utrs.controller.testrunner.backend
     * .UtrsHtrApiIntegrationTest}.
     */
    NO_OP_DEVICE_POST_RUN_TEST_CALLED,

    /** The epoch time when the test execution is completed and the device is released. */
    DEVICE_DONE_EPOCH_MS,

    /**
     * id of MH HaTS survey shown in sponge starting with '_' so sponge can make it invisible
     * http://b/169158354
     */
    _HATS_SURVEY_ID_FOR_MH,

    /**
     * trigger id of MH HaTS survey shown in sponge starting with '_' so sponge can make it
     * invisible http://b/169158354
     */
    _HATS_TRIGGER_ID_FOR_MH,

    /** If the UTRS is running in DM process. */
    _IS_RUN_IN_DM,

    /** Locator of test engine stub. */
    _TEST_ENGINE_LOCATOR,

    /**
     * Count of drain timeout retry attempts in this job shown in sponge starting with '_' so sponge
     * can make it invisible http://b/169158354.
     */
    _DRAIN_TIMEOUT_RETRY_ATTEMPTS;

    /**
     * Name prefix of the test property that contains the decorator run time in milliseconds.
     *
     * <p>Should fill with the decorator name.
     */
    public static final String PREFIX_DECORATOR_RUN_TIME_MS = "decorator_run_time_ms_";

    /**
     * Test property prefix of the primary device dimensions.
     *
     * <p>For example: dimension_host_name
     */
    public static final String PREFIX_DIMENSION = "dimension_";

    /** Test property prefix for MH file transfer. */
    public static final String PREFIX_FILE_TRANSFER = "mh_ft_";

    /** Test property prefix for MH flash result. */
    public static final String PREFIX_FLASH_RESULT = "flash_result_";

    /** Same as {@link #PREFIX_DIMENSION} only for tests with multiple subdevices. */
    public static final String PREFIX_SUBDEVICE_DIMENSION = "dimension_subdevice_";

    /**
     * Test property prefix for reasons why a device/driver/decorator can not run in hybrid UTP
     * mode.
     *
     * <p>The prefix should be followed with a converter name prefix.
     *
     * <p>Its value is a sorted concatenated string, in which each part is a formatted lowercase
     * {@code IncompatibleReason} without context and the delimiter is "{@code ;}".
     */
    public static final String PREFIX_HYBRID_UTP_DETAILED_INCOMPATIBLE_REASON =
        "hybrid_utp_detailed_incompatible_reason_";

    /** Gateway service property name constants. */
    public enum Gateway implements PropertyName {

      /** The ID of the session of Gateway. */
      GATEWAY_SESSION,

      /** The MH Gateway client. */
      GATEWAY_CLIENT_NAME,
    }

    /** Moreto test property name constants. */
    public enum Moreto implements PropertyName {

      /** Driver init time in ms. */
      DRIVER_INIT_TIME_MS,

      /** Driver run time in ms. */
      DRIVER_RUN_TIME_MS,

      /** Driver end time in ms. */
      DRIVER_END_TIME_MS,

      /** The name of Moreto device location. */
      MORETO_DEVICE_LOCATION,

      /** Prefix name of Moreto user location. */
      MORETO_USER_LOCATION,

      /** Prefix name of Moreto Remote control latency in ms. */
      MORETO_LATENCY
    }

    /** AndroidInstrumentation driver property name constants. */
    public enum AndroidInstrumentation implements PropertyName {

      /** Android instrumentation install apk time in sec. */
      ANDROID_INSTRUMENTATION_INSTALL_APK_TIME_SEC,

      /** Android instrumentation install apk time in milliseconds. */
      ANDROID_INSTRUMENTATION_INSTALL_APK_TIME_MS,

      /** Android instrumentation execution time in sec. */
      ANDROID_INSTRUMENTATION_EXECUTION_TIME_SEC,

      /** Android instrumentation execution time in milliseconds. */
      ANDROID_INSTRUMENTATION_EXECUTION_TIME_MS,

      /** The extra options that Android instrumentation will pass to the test */
      ANDROID_INSTRUMENTATION_EXTRA_OPTIONS,

      /** Test specific Android instrumentation test arguments. */
      TEST_SPECIFIC_TEST_ARGS,
    }

    /** AndroidSetPropDecorator property name constants. */
    public enum AndroidSetPropDecorator implements PropertyName {

      /** The extra Android device properties to be set before running test. */
      ANDROID_SET_PROP_DECORATOR_EXTRA_PROPERTIES
    }

    /** AndroidAndIosAccountDecorator property name constants. */
    public enum AndroidAndIosAccountDecorator implements PropertyName {
      /** The status of validating a list of Google accounts. * */
      ACCOUNT_VALIDATION_STATUS,

      /** Error message for validation failure * */
      ACCOUNT_VALIDATION_FAILURE_ERROR_MESSAGE,

      /** Error adding account using LST form TestaccountService * */
      ADD_ACCOUNT_USING_LST_FROM_TAS_ERROR_MESSAGE,

      /** Missing LST or Obfuscated GaiaID or both the test properties * */
      TEST_PROPERTY_FOR_LST_OR_OBFUSCATED_GAIA_ID_MISSING,

      /** LST and Obfuscated GaiaID mismatch * */
      TAS_LST_AND_OBFUSCATED_GAIA_ID_MISMATCH
    }

    /** AndroidAccountDecorator property name constants. */
    public enum AndroidAccountDecorator implements PropertyName {

      /** Comma separated email addresses of the Google accounts to set on the device. */
      ANDROID_ACCOUNT_DECORATOR_EMAILS,

      /** Comma separated passwords of given accounts in the order of given accounts in emails. */
      ANDROID_ACCOUNT_DECORATOR_PASSWORDS,

      /** Comma separated LSTs from TestaccountService of accounts in order of emails. */
      ANDROID_ACCOUNT_DECORATOR_LSTS_FROM_TAS,

      /** Comma separated auth code of given accounts in the order of given accounts in emails. */
      ANDROID_ACCOUNT_DECORATOR_AUTHCODES,
    }

    /** IosGoogleAccountDecorator property name constants. */
    public enum IosGoogleAccountDecorator implements PropertyName {

      /** Comma separated email addresses of the Google accounts to set on the device. */
      IOS_GOOGLE_ACCOUNT_DECORATOR_EMAILS,

      /** Comma separated passwords of given accounts in the order of given accounts in emails. */
      IOS_GOOGLE_ACCOUNT_DECORATOR_PASSWORDS,

      /** Comma separated LSTs of given accounts in the order of given accounts in emails. */
      IOS_GOOGLE_ACCOUNT_DECORATOR_LSTS_FROM_TAS,

      /**
       * Comma separated Obfuscated GaiaId of given accounts in the order of given accounts in
       * emails.
       */
      IOS_GOOGLE_ACCOUNT_DECORATOR_OBFUSCATED_GAIA_IDS,
    }

    /** iOS Passcode property name constants. */
    public enum IosPasscode implements PropertyName {
      /** Whether the decide has been set a passcode or not. */
      DEVICE_PASSCODE_SET,
      /** The path to unlock toke file. */
      DEVICE_PASSCODE_TOKEN_FILE_PATH,
    }

    /** iOS test property name constants. */
    public enum IosTest implements PropertyName {
      /** The signing identity of the test bundle/app under test. */
      TEST_SIGNING_IDENTITY,
      /** Whether the job uses shared lab Google device for testing. */
      USE_SHARED_LAB_GOOGLE_DEVICE,
    }

    /** Lab property name constants. */
    public enum Lab implements PropertyName {}

    /** Battery test property name constants. */
    public enum Battery implements PropertyName {
      /** Duration property of the test */
      BATTERY_TEST_DURATION,

      /** Sample number. */
      BATTERY_TEST_SAMPLE,

      /** Average current consumption property of the test. */
      BATTERY_TEST_AVERAGE_CONSUMPTION,

      /** Highest peak current of the test. */
      BATTERY_TEST_HIGHEST_PEAK,

      /** Average voltage property of the test. */
      BATTERY_TEST_AVERAGE_VOLTAGE,

      /** Highest peak voltage of the test. */
      BATTERY_TEST_PEAK_VOLTAGE,

      /** Average power consumption of the test. */
      BATTERY_TEST_AVERAGE_POWER,

      /** Highest power consumption of the test. */
      BATTERY_TEST_PEAK_POWER,

      /** Lowest power consumption of the test. */
      BATTERY_TEST_LOWEST_POWER,

      /** Stddev of power consumption of the test. */
      BATTERY_TEST_STDDEV_POWER,

      /** Original array of power consumption. */
      BATTERY_TEST_POWER_ITERATIONS,

      /** Average power consumption of all iterations in the test. */
      BATTERY_TEST_POWER_ITERATIONS_AVERAGE,

      /** Stddev of power consumption of all iterations in the test. */
      BATTERY_TEST_POWER_ITERATIONS_STDDEV,

      /** Iteration number in the test. */
      BATTERY_TEST_POWER_ITERATIONS_NUMBER,

      /** Average CPU system time of all iterations in the test. */
      BATTERY_TEST_CPU_SYSTEM_TIME_ITERATIONS_AVERAGE,

      /** Stddev of CPU system time of all iterations in the test. */
      BATTERY_TEST_CPU_SYSTEM_TIME_ITERATIONS_STDDEV,

      /** Average CPU user time of all iterations in the test. */
      BATTERY_TEST_CPU_USER_TIME_ITERATIONS_AVERAGE,

      /** Stddev of CPU user time of all iterations in the test. */
      BATTERY_TEST_CPU_USER_TIME_ITERATIONS_STDDEV,

      /** Average full wakelock count of all iterations in the test. */
      BATTERY_TEST_FULL_WAKELOCK_COUNT_ITERATIONS_AVERAGE,

      /** Stddev of full wakelock count of all iterations in the test. */
      BATTERY_TEST_FULL_WAKELOCK_COUNT_ITERATIONS_STDDEV,

      /** Average partial wakelock count of all iterations in the test. */
      BATTERY_TEST_PARTIAL_WAKELOCK_COUNT_ITERATIONS_AVERAGE,

      /** Stddev of partial wakelock count of all iterations in the test. */
      BATTERY_TEST_PARTIAL_WAKELOCK_COUNT_ITERATIONS_STDDEV,

      /** Original array of cpu user time. */
      BATTERY_TEST_CPU_USER_TIME,

      /** Original array of cpu system time. */
      BATTERY_TEST_CPU_SYSTEM_TIME,

      /** Average WIFI received data of all iterations in the test. */
      BATTERY_TEST_WIFI_RX_AVERAGE,

      /** Stddev of WIFI received data of all iterations in the test. */
      BATTERY_TEST_WIFI_RX_STDDEV,

      /** Average WIFI transmitted data of all iterations in the test. */
      BATTERY_TEST_WIFI_TX_AVERAGE,

      /** Stddev of WIFI transmitted data of all iterations in the test. */
      BATTERY_TEST_WIFI_TX_STDDEV,

      /** Original array of WIFI received bytes. */
      BATTERY_TEST_WIFI_RX,

      /** Original array of WIFI transmitted bytes. */
      BATTERY_TEST_WIFI_TX,

      /** Additional Monsoon monitoring command timeout. */
      ADDITIONAL_MONSOON_MONITORING_TIMEOUT_MS,
    }

    /** Apk information property names. */
    public enum ApkInfo implements PropertyName {
      /**
       * Property name prefix of version code. Will append package name (replaced '.' with '_')
       * after it.
       */
      VERSION_CODE_,

      /**
       * Property name prefix of version name. Will append package name (replaced '.' with '_')
       * after it.
       */
      VERSION_NAME_,

      /**
       * Property name prefix of apk size. Will append package name (replaced '.' with '_') after
       * it.
       */
      APK_SIZE_,

      /**
       * Main package name. To tell system health push lib which package the default measurements
       * belongs to.
       */
      MAIN_PACKAGE_NAME,
    }

    /** App info property name constants. */
    public enum AppInfo implements PropertyName {
      /**
       * The id of app under test. For Android, it is build package name. For iOS, it is bundle id.
       */
      AUT_ID,
    }

    /** Moscar property name constants. */
    public enum Moscar implements PropertyName {
      /** Name of the job property which stores the sponge invocation info. */
      SPONGE_LINK_OF_TEST,
      /** Name of the job property which stores the ID of the Moscar V1 project. */
      MOSCAR_V1_PROJECT_ID,
    }

    /** Dashboard property name constants. */
    public enum Dashboard implements PropertyName {
      /** Name of the test property which stores the file path needed by the exception dashboard. */
      EXCEPTION_STACK_FILE,
      /**
       * Name of the test property which stores the file paths needed by the performance dashboard.
       */
      PERFORMANCE_ANNOTATED_FILES,
    }

    /** Test runner benchmark property name constants. */
    public enum TestRunnerBenchmark implements PropertyName {
      /** SciMark 2.0 score of a MH test runner benchmark test. */
      MH_TR_BENCHMARK_CPU_SCIMARK2_SCORE,
    }

    /** AndroidSetWifiDecorator property name constants. */
    public enum AndroidSetWifiDecorator implements PropertyName {
      /** Name of the device property for the default wifi ssid. */
      DEFAULT_WIFI_SSID,
      /** Name of the device property for the default wifi psk. */
      DEFAULT_WIFI_PSK,
    }

    /** ACID test property name constants. */
    public enum Acid implements PropertyName {
      /** scrcpy server port. */
      MH_ACID_SCRCPY_PORT,

      /** RTC server port. */
      MH_ACID_WEB_RTC_PORT,

      /** USB port */
      MH_ACID_USB_PORT,

      /** CloudRPC endpoint for RTC proxy server. */
      MH_RTC_CLOUD_RPC_PROXY_ENDPOINT,

      /** Device USB address. */
      MH_USB_ADDRESS,

      /** gRPC TPC proxy server port. */
      MH_DATA_PROXY_PORT
    }

    /** UtpDriver property name constants. */
    public enum UtpDriver implements PropertyName {

      /**
       * UTP command timeout configuration for UtpDriver.
       *
       * <p>The format of the timeout value is defined by ISO-8601 same as the output format of
       * java.time.Duration.toString().
       *
       * <p>This property is optional. When it is not present, UTP driver will use the timeout value
       * from the original count down timer of MH test info.
       */
      MH_UTP_DRIVER_UTP_COMMAND_TIMEOUT,

      /**
       * The working dir of UTP command.
       *
       * <p>This property is optional. When it is not present, UTP driver will use working directory
       * from utpRunnerPath.
       */
      MH_UTP_DRIVER_UTP_COMMAND_WORKING_DIR,

      /**
       * The path of UTP launcher binary file.
       *
       * <p>This property is optional. When it is not present, UTP driver will use a fixed file path
       * defined in UtpDriver.java.
       */
      MH_UTP_DRIVER_UTP_LAUNCHER_BINARY_FILE,

      /**
       * The path of UTP main binary file.
       *
       * <p>This property is optional. When it is not present, UTP driver will use a fixed file path
       * defined in UtpDriver.java.
       */
      MH_UTP_DRIVER_UTP_MAIN_BINARY_FILE,

      /** Exitcode of UTP runner. Format: integer. */
      MH_UTP_DRIVER_UTP_EXITCODE,

      /** Boolean field, indicating whether we need to save the command into TestInfo. */
      MH_UTP_DRIVER_NEED_COMMAND,

      /**
       * Command executed in UTP driver.
       *
       * <p>Format: The Base64 encoder result of com.google.devtools.mobileharness.infra.controller
       * .test.local.utp.CommandStringProto.CommandString.
       */
      MH_UTP_DRIVER_UTP_COMMAND,
    }

    /** AdhocTestbedDriver property name constants. */
    public enum AdhocTestbedDriver implements PropertyName {
      /** Whether the TestInfo is for a adhoc testbed sub device. */
      IS_ADHOC_TESTBED_SUB_DEVICE,
    }

    /** DefaultSpongeTreeGenerator property name constants. */
    public enum DefaultSpongeTreeGenerator implements PropertyName {
      /** Whether to omit the test info from Sponge. */
      OMIT_FROM_SPONGE,
    }
  }
}
