import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a test with multiple errors and warnings. */
export const SCENARIO_TEST_MULTIPLE_ERRORS_WARNINGS: MockTestScenario = {
  id: 'test-multiple-errors-warnings-1',
  scenarioName: 'Test with Multiple Errors and Warnings',
  overview: {
    id: 'test-multiple-errors-warnings-1',
    name: 'com.google.devtools.mobileharness.infra.ComplexFailureTest#testExecute',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_ERROR,
    job: {
      id: 'fa789cda-123d-456e-b065-c77724b9d888',
      name: 'com.google.android.gm.GmailInstrumentationTest',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_ERROR,
      spongeLink: 'http://sponge/mock-job-link',
    },
    devices: {
      device: [
        {
          id: '43021FDAQ000UM',
          type: 'AndroidRealDevice',
        },
      ],
    },
    host: {
      name: 'mt31-dm01-a-x04.moma.google.com',
      ip: '100.107.201.12',
    },
    executionDetails: {
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:20Z',
      updateTime: '2025-07-09T10:15:20Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    properties: {
      'infra_version': 'v6.2.1',
    },
    troubleshooting: {
      resultCause: {
        error: [
          {
            message: 'DEVICE_DETECTION_ERROR: Failed to detect USB connection',
            trace: `HarnessException: Failed to detect USB connection
  at com.google.devtools.mobileharness.infra.detector.UsbDetector.detect(UsbDetector.java:45)`,
          },
          {
            message: 'ADB_COMMAND_REJECTED: adb returned status 1',
            trace: `AdbCommandRejectedException: command 'shell getprop' failed
  at com.google.devtools.mobileharness.shared.util.command.CommandExecutor.run(CommandExecutor.java:120)`,
          },
        ],
      },
      warnings: {
        warning: [
          {
            message: 'Logcat daemon restarted during test execution',
            trace: `WARNING [10:15:08] ADB connection lost temporarily, re-established.
WARNING [10:15:15] Device temperature is high (42C)`,
          },
        ],
      },
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:15:00Z',
      startTime: '2025-07-09T10:15:05Z',
      endTime: '2025-07-09T10:15:20Z',
      stages: [
        {
          name: 'Allocation',
          startTime: '2025-07-09T10:15:05Z',
          endTime: '2025-07-09T10:15:07Z',
        },
        {
          name: 'Execution',
          startTime: '2025-07-09T10:15:07Z',
          endTime: '2025-07-09T10:15:20Z',
        },
      ],
    },
  },
  log: `2026-06-09 20:31:48:923 PDT I TIMELINE:PRE_RUN_TEST:START:client Start pre-running test moreto_qiupingf_20260610033135Z on device(s) [HA8POBAQLVFYQKKZ@172.17.0.1/mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek]
2026-06-09 20:31:48:924 PDT I ========= Client: InitializeTest (71f02572-1262-4dce-9cab-20e7d3011c24) =========
2026-06-09 20:31:52:611 PDT I Versions of [lab_server_locator[ip=[172.17.0.1], host_name=[mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek], master_detected_ip=Optional.empty, ports={LAB_SERVER_RPC=9999, LAB_SERVER_GRPC=9994, LAB_SERVER_SOCKET=9998}]]: versions { version: "4.370.0" type: "LAB_VERSION" }
2026-06-09 20:31:52:611 PDT I ========= Client: CheckDevice (71f02572-1262-4dce-9cab-20e7d3011c24) =========
2026-06-09 20:31:55:389 PDT I Post TestStartingEvent to test 71f02572-1262-4dce-9cab-20e7d3011c24@3a5b6e45-d7be-40c6-933f-0067198a67f5
2026-06-09 20:31:55:419 PDT I Add test 71f02572-1262-4dce-9cab-20e7d3011c24 to Moss
2026-06-09 20:31:55:421 PDT I ========= Client: PreRunTest (71f02572-1262-4dce-9cab-20e7d3011c24) =========
2026-06-09 20:31:55:497 PDT I TIMELINE:SEND_FILE:START: Send file started
2026-06-09 20:32:31:101 PDT I TIMELINE:SEND_FILE:END: Send file finished
2026-06-09 20:32:31:102 PDT I Local test message subscribers: []
2026-06-09 20:32:31:105 PDT I Post TestStartedEvent to test 71f02572-1262-4dce-9cab-20e7d3011c24@3a5b6e45-d7be-40c6-933f-0067198a67f5
2026-06-09 20:32:31:368 PDT I TIMELINE:PRE_RUN_TEST:END:client Pre-run test moreto_qiupingf_20260610033135Z finished
2026-06-09 20:32:31:369 PDT I TIMELINE:RUN_TEST:START:client Start running test moreto_qiupingf_20260610033135Z on device(s) [HA8POBAQLVFYQKKZ@172.17.0.1/mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek]
2026-06-09 20:32:31:370 PDT I ========= Client: RunTest (71f02572-1262-4dce-9cab-20e7d3011c24) =========
2026-06-09 20:32:34:036 PDT I Test kicked off at lab side
2026-06-09 20:32:33:785 PDT I TIMELINE:PRE_RUN_TEST:START:lab Start pre-running test moreto_qiupingf_20260610033135Z on device(s) [HA8POBAQLVFYQKKZ@172.17.0.1/mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek]
2026-06-09 20:32:33:785 PDT I Start to handling cached job/test files, cache=[job_file_unit { job_locator { id: "3a5b6e45-d7be-40c6-933f-0067198a67f5" } tag: "flashstation" local_path: "/usr/local/google/mobileharness/mh_lab_tmp/mh_lab_run_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/tmp/acid-realm-alkali-xcid/tmp/mh_run_5b339d01-2cb7-41e0-88ef-7990fbb0dd35/flashstation/mpm/android/build_tools/huckle/web_flashstation/tools/live/cl_flashstation" original_path: "/tmp/acid-realm-alkali-xcid/tmp/mh_run_5b339d01-2cb7-41e0-88ef-7990fbb0dd35/flashstation/mpm/android/build_tools/huckle/web_flashstation/tools/live/cl_flashstation" checksum: "6fd05a35" }]
2026-06-09 20:32:33:785 PDT F Handle job/test file: job_file_unit { job_locator { id: "3a5b6e45-d7be-40c6-933f-0067198a67f5" } tag: "flashstation" local_path: "/usr/local/google/mobileharness/mh_lab_tmp/mh_lab_run_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/tmp/acid-realm-alkali-xcid/tmp/mh_run_5b339d01-2cb7-41e0-88ef-7990fbb0dd35/flashstation/mpm/android/build_tools/huckle/web_flashstation/tools/live/cl_flashstation" original_path: "/tmp/acid-realm-alkali-xcid/tmp/mh_run_5b339d01-2cb7-41e0-88ef-7990fbb0dd35/flashstation/mpm/android/build_tools/huckle/web_flashstation/tools/live/cl_flashstation" checksum: "6fd05a35" }
2026-06-09 20:32:33:785 PDT F Add job/test file: job_file_unit { job_locator { id: "3a5b6e45-d7be-40c6-933f-0067198a67f5" } tag: "flashstation" local_path: "/usr/local/google/mobileharness/mh_lab_tmp/mh_lab_run_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/tmp/acid-realm-alkali-xcid/tmp/mh_run_5b339d01-2cb7-41e0-88ef-7990fbb0dd35/flashstation/mpm/android/build_tools/huckle/web_flashstation/tools/live/cl_flashstation" original_path: "/tmp/acid-realm-alkali-xcid/tmp/mh_run_5b339d01-2cb7-41e0-88ef-7990fbb0dd35/flashstation/mpm/android/build_tools/huckle/web_flashstation/tools/live/cl_flashstation" checksum: "6fd05a35" }
2026-06-09 20:32:33:785 PDT I Loading lab plugins for test 71f02572-1262-4dce-9cab-20e7d3011c24
2026-06-09 20:32:33:786 PDT I Post TestStartingEvent to test 71f02572-1262-4dce-9cab-20e7d3011c24@3a5b6e45-d7be-40c6-933f-0067198a67f5
2026-06-09 20:32:33:787 PDT I --------- Device: PreRunTest for moreto_qiupingf_20260610033135Z (71f02572-1262-4dce-9cab-20e7d3011c24) ---------
2026-06-09 20:32:33:787 PDT I Pre-run test of device [HA8POBAQLVFYQKKZ]
2026-06-09 20:32:33:821 PDT I Stop package on device HA8POBAQLVFYQKKZ before test started: com.google.android.apps.internal.statusbarhider
2026-06-09 20:32:34:191 PDT I Package com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2 md5 retrieved from device HA8POBAQLVFYQKKZ property \`installed_apk:user_0:com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2\` is: ecb42d2af141b423bc849acef997a561
2026-06-09 20:32:34:191 PDT I Skip installing daemon.apk which has been installed before
2026-06-09 20:32:34:191 PDT I Starting device daemon com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2.DaemonActivity on device HA8POBAQLVFYQKKZ...
2026-06-09 20:32:34:547 PDT I Prepare extras for device daemon with device id [HA8POBAQLVFYQKKZ], labels [], hostname [mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek], owners [[mobileharness-eng, dafeng, qiupingf, tianch]], executors [[]], ssid []
2026-06-09 20:32:34:653 PDT I Pre-run test of device [HA8POBAQLVFYQKKZ] finished
2026-06-09 20:32:34:660 PDT I Driver AcidRemoteDriver uses MH classic mode
2026-06-09 20:32:34:660 PDT I Run test in MH classic mode
2026-06-09 20:32:34:676 PDT I ------------------------- Starting new test -------------------------
Start test [moreto_qiupingf_20260610033135Z] on device(s) [HA8POBAQLVFYQKKZ]:
+ Test Name: moreto_qiupingf_20260610033135Z
+ Test ID: 71f02572-1262-4dce-9cab-20e7d3011c24
+ Job Name: moreto_qiupingf_20260610033135Z
+ Job ID: 3a5b6e45-d7be-40c6-933f-0067198a67f5
+ Device Information:
+ Device: HA8POBAQLVFYQKKZ
- num_cpus = 8
- sign = release-keys
- language = en
- incremental_build = v816.0.0.2.umlmixm
- gmscore_signature = 98deb0ca
- supports_adhoc = true
- type = corot_global
- uuid = HA8POBAQLVFYQKKZ
- product_device = corot
- device_form = physical
- veritymode = enforcing
- memory_class = 256m
- host_version = 4.370.0
- gsm_operator_alpha = ,
- model = 23078pnd5g
- screen_size = 1220x2712
- id = HA8POBAQLVFYQKKZ
- release_version = 14
- brand = xiaomi
- build_type = user
- hardware = mt6985
- free_internal_storage = 209GB
- writable_external_storage = /storage/emulated/0
- mcc_mnc =
- host_os = Linux
- device_class_name = AndroidRealDevice
- memory_class_in_mb = 256
- revision = 0
- battery_status = ok
- battery_temperature = 30
- external_storage_status = ok
- screen_density = 480
- codename = rel
- free_internal_storage_percentage = 92.79%
- device = corot
- product_board = corot
- internet = false
- release_version_major = 14
- communication_type = USB
- communication_type = ADB
- iccids =
- control_id = HA8POBAQLVFYQKKZ
- free_external_storage = 209GB
- soc_model = mt6985
- launcher_1 = false
- lab_type = satellite
- uuid_volatile = false
- locale = en-gb
- launcher_3 = false
- manufacturer = xiaomi
- native_bridge = 0
- internal_storage_status = ok
- free_external_storage_percentage = 92.79%
- dm_type = mh
- total_memory = 11332 MB
- feature = android.hardware.nfc
- launcher_gel = false
- host_os_version = Ubuntu 22.04.5 LTS
- sdk_version = 34
- rooted = false
- baseband_version = moly.nr16.r2.mp1.tc8.pr1.sp.v3.p28,moly.nr16.r2.mp1.tc8.pr1.sp.v3.p28
- is_gki_kernel = true
- machine_hardware_name = aarch64
- characteristics = nosdcard
- host_ip = 172.17.0.1
- os = android
- battery_level = 100
- kernel_release_number = 5.15.104-android13-8-00010-g1dc49517c375-ab11006012
- bluetooth_mac_address = a4:cc:b3:8c:0e:1b
- screenshot_able = true
- device_supports_container = true
- abi = arm64-v8a
- gms_version = 26.08.34 (190400-876566425)
- sim_state = absent,absent
- location_type = not_in_china
- cpu_freq_in_ghz = 2.0
- supports_gmscore = true
- svelte_device = false
- build = UP1A.230905.011
- build = up1a.230905.011
- serial = HA8POBAQLVFYQKKZ
- serial = ha8pobaqlvfyqkkz
- abilist = arm64-v8a,armeabi-v7a,armeabi
- build_alias = up1a.230905.011
- preview_sdk_version = 0
- host_name = mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek
2026-06-09 20:32:34:677 PDT I Adding common dimensions [host_version, host_name] shared among testbed to test 71f02572-1262-4dce-9cab-20e7d3011c24
2026-06-09 20:32:34:678 PDT I Local test message subscribers: []
2026-06-09 20:32:34:678 PDT I Post TestStartedEvent to test 71f02572-1262-4dce-9cab-20e7d3011c24@3a5b6e45-d7be-40c6-933f-0067198a67f5
2026-06-09 20:32:34:678 PDT I TIMELINE:PRE_RUN_TEST:END:lab Pre-run test moreto_qiupingf_20260610033135Z finished
2026-06-09 20:32:34:678 PDT I TIMELINE:RUN_TEST:START:lab Start running test moreto_qiupingf_20260610033135Z on device(s) [HA8POBAQLVFYQKKZ@172.17.0.1/mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek]
2026-06-09 20:32:34:678 PDT I --------- Device: RunTest for moreto_qiupingf_20260610033135Z (71f02572-1262-4dce-9cab-20e7d3011c24) ---------
2026-06-09 20:32:34:679 PDT I TIMELINE:RUN_TEST:MARK_POINT:AndroidStartAppsDecorator:lab Starting driver [AndroidStartAppsDecorator] of test moreto_qiupingf_20260610033135Z
2026-06-09 20:32:34:679 PDT I AndroidStartAppsDecorator is starting
2026-06-09 20:32:34:679 PDT I Start application: -c android.intent.category.HOME -a android.intent.action.MAIN
on device: HA8POBAQLVFYQKKZ
2026-06-09 20:32:34:715 PDT I AndroidStartAppsDecorator is forwarding a running request
2026-06-09 20:32:34:715 PDT I TIMELINE:RUN_TEST:MARK_POINT:AndroidOrientationDecorator:lab Starting driver [AndroidOrientationDecorator] of test moreto_qiupingf_20260610033135Z
2026-06-09 20:32:34:716 PDT I AndroidOrientationDecorator is starting
2026-06-09 20:32:34:716 PDT I Rotate to PORTRAIT
2026-06-09 20:32:34:716 PDT I Disable accelerometer rotation
2026-06-09 20:32:36:708 PDT I AndroidOrientationDecorator is forwarding a running request
2026-06-09 20:32:36:709 PDT I TIMELINE:RUN_TEST:MARK_POINT:AndroidFlashstationDecorator:lab Starting driver [AndroidFlashstationDecorator] of test moreto_qiupingf_20260610033135Z
2026-06-09 20:32:36:709 PDT I AndroidFlashstationDecorator is starting
2026-06-09 20:32:36:711 PDT I Clearing device properties for flashing
2026-06-09 20:32:36:711 PDT I Clearing device files for flashing
2026-06-09 20:32:36:711 PDT I Device HA8POBAQLVFYQKKZ waits for a flash quota.
2026-06-09 20:32:36:712 PDT I using default flashing timeout PT1H
2026-06-09 20:32:36:712 PDT I Device HA8POBAQLVFYQKKZ waiting for flashing quota. Waiting for 1 tokens to be available.
2026-06-09 20:32:36:713 PDT I Writing flash time proto to file: /var/www/mh_lab_gen_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/test_71f02572-1262-4dce-9cab-20e7d3011c24/flashstation_decorator_protos/HA8POBAQLVFYQKKZ/allocation_wait_HA8POBAQLVFYQKKZ.pb.bin. Message: target: "aosp_cf_arm64_only_phone-userdebug"
branch: "aosp-android-latest-release"
build_id: "13455672"
device_control_id: "HA8POBAQLVFYQKKZ"
host_name: "mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek"
test_id: "71f02572-1262-4dce-9cab-20e7d3011c24"
job_id: "3a5b6e45-d7be-40c6-933f-0067198a67f5"
user: "qiupingf"
start {
seconds: 1781062356
nanos: 712154000
}
end {
seconds: 1781062356
nanos: 712355000
}
wait {
nanos: 201000
}

2026-06-09 20:32:36:713 PDT I Device HA8POBAQLVFYQKKZ acquired a flash quota. Allocated 1 tokens.
2026-06-09 20:32:36:715 PDT I Using cached flashstation key as fallback
2026-06-09 20:32:36:715 PDT I Flashing attempt 1/1 for device HA8POBAQLVFYQKKZ
2026-06-09 20:32:36:716 PDT I Caching device for flashing for PT1H15M
2026-06-09 20:32:36:716 PDT I Flashing about to start
2026-06-09 20:32:36:716 PDT I Running Flashstation with command [/usr/local/google/mobileharness/mh_lab_tmp/mh_lab_run_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/tmp/acid-realm-alkali-xcid/tmp/mh_run_5b339d01-2cb7-41e0-88ef-7990fbb0dd35/flashstation/mpm/android/build_tools/huckle/web_flashstation/tools/live/cl_flashstation --adbpath /usr/local/google/mobileharness/mh_res_files/devtools/mobileharness/platform/android/sdktool/binary/platform-tools/adb --nointeractive --filter_device_inspection_to_serial_number --noautoupdate --factoryreset --enable_v4_build_api --cachedir /usr/local/google/mobileharness/mh_lab_tmp/mh_lab_gen_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/test_71f02572-1262-4dce-9cab-20e7d3011c24/flashstation_cache_dir --log_to_binary_proto_file_path /var/www/mh_lab_gen_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/test_71f02572-1262-4dce-9cab-20e7d3011c24/flashstation_decorator_protos/HA8POBAQLVFYQKKZ/flashing_log_proto_attempt_1.pb -b 13455672 -t aosp_cf_arm64_only_phone-userdebug -s HA8POBAQLVFYQKKZ --private_key_file /usr/local/google/mobileharness/mh_lab_tmp/mh_lab_gen_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/test_71f02572-1262-4dce-9cab-20e7d3011c24/web_flashstation_key16440800310587029098.json --tool_tag MOBILE_HARNESS] and timeout [PT1H]
2026-06-09 20:32:39:718 PDT I [HA8POBAQLVFYQKKZ] Session ID: 1E279F3C-DDFF-4021-BE41-265A3289E705
2026-06-09 20:32:43:242 PDT W Error at attempt 1 on device HA8POBAQLVFYQKKZ, error:
MobileHarnessException: Fail to execute flashstation cli on device HA8POBAQLVFYQKKZ. See http://omnilab-android-flash-device#when-i-see-failed-to-execute-flashstation-command-what-should-i-do for how to further debug. [MH|DEPENDENCY_ISSUE|ANDROID_FLASHSTATION_COMMAND_EXEC_ERROR|109951]: Fail to execute flashstation cli on device HA8POBAQLVFYQKKZ. See http://omnilab-android-flash-device#when-i-see-failed-to-execute-flashstation-command-what-should-i-do for how to further debug. [MH|DEPENDENCY_ISSUE|ANDROID_FLASHSTATION_COMMAND_EXEC_ERROR|109951]
2026-06-09 20:32:43:243 PDT I Writing flash time proto to file: /var/www/mh_lab_gen_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/test_71f02572-1262-4dce-9cab-20e7d3011c24/flashstation_decorator_protos/HA8POBAQLVFYQKKZ/flashing_attempt_HA8POBAQLVFYQKKZ_attempt_1.pb.bin. Message: target: "aosp_cf_arm64_only_phone-userdebug"
branch: "aosp-android-latest-release"
build_id: "13455672"
device_control_id: "HA8POBAQLVFYQKKZ"
host_name: "mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek"
test_id: "71f02572-1262-4dce-9cab-20e7d3011c24"
job_id: "3a5b6e45-d7be-40c6-933f-0067198a67f5"
user: "qiupingf"
timestamp {
seconds: 1781062356
nanos: 716499000
}
attempt: 1
status: "failure"

2026-06-09 20:32:43:243 PDT S all 1 flashing attempts failed on device HA8POBAQLVFYQKKZ. Errors:: Fail to execute flashstation cli on device HA8POBAQLVFYQKKZ. See http://omnilab-android-flash-device#when-i-see-failed-to-execute-flashstation-command-what-should-i-do for how to further debug. [MH|DEPENDENCY_ISSUE|ANDROID_FLASHSTATION_COMMAND_EXEC_ERROR|109951]
2026-06-09 20:32:43:243 PDT S [Critical] Error detail:: Fail to execute flashstation cli on device HA8POBAQLVFYQKKZ. See http://omnilab-android-flash-device#when-i-see-failed-to-execute-flashstation-command-what-should-i-do for how to further debug. [MH|DEPENDENCY_ISSUE|ANDROID_FLASHSTATION_COMMAND_EXEC_ERROR|109951]
2026-06-09 20:32:43:258 PDT I Finished AndroidFlashstationDecorator on device HA8POBAQLVFYQKKZ
2026-06-09 20:32:43:258 PDT I AndroidFlashstationDecorator has ended with error: MobileHarnessException
2026-06-09 20:32:43:259 PDT I TIMELINE:RUN_TEST:MARK_POINT::lab Driver [AndroidFlashstationDecorator] of test moreto_qiupingf_20260610033135Z ended
2026-06-09 20:32:43:259 PDT I AndroidOrientationDecorator has forwarded a running request with error: MobileHarnessException
2026-06-09 20:32:43:259 PDT I AndroidOrientationDecorator has ended with error: MobileHarnessException
2026-06-09 20:32:43:259 PDT I TIMELINE:RUN_TEST:MARK_POINT::lab Driver [AndroidOrientationDecorator] of test moreto_qiupingf_20260610033135Z ended
2026-06-09 20:32:43:259 PDT I AndroidStartAppsDecorator has forwarded a running request with error: MobileHarnessException
2026-06-09 20:32:43:259 PDT I AndroidStartAppsDecorator has ended with error: MobileHarnessException
2026-06-09 20:32:43:259 PDT I TIMELINE:RUN_TEST:MARK_POINT::lab Driver [AndroidStartAppsDecorator] of test moreto_qiupingf_20260610033135Z ended
2026-06-09 20:32:43:260 PDT I TIMELINE:RUN_TEST:END:lab Run test moreto_qiupingf_20260610033135Z finished
2026-06-09 20:32:43:260 PDT W ERROR: MobileHarnessException: Fail to execute flashstation cli on device HA8POBAQLVFYQKKZ. See http://omnilab-android-flash-device#when-i-see-failed-to-execute-flashstation-command-what-should-i-do for how to further debug. [MH|DEPENDENCY_ISSUE|ANDROID_FLASHSTATION_COMMAND_EXEC_ERROR|109951]
at com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidFlashstationDecorator.executeFlashAttempts(AndroidFlashstationDecorator.java:477)
at com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidFlashstationDecorator.executeAndroidFlashstation(AndroidFlashstationDecorator.java:390)
at com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidFlashstationDecorator.run(AndroidFlashstationDecorator.java:314)
at com.google.devtools.mobileharness.infra.controller.test.local.LocalTestRunner$DriverEventGenerator.run(LocalTestRunner.java:289)
at com.google.devtools.mobileharness.infra.controller.test.local.LocalTestRunner$DecoratorEventGenerator.run(LocalTestRunner.java:399)
at com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidOrientationDecorator.run(AndroidOrientationDecorator.java:57)
at com.google.devtools.mobileharness.infra.controller.test.local.LocalTestRunner$DriverEventGenerator.run(LocalTestRunner.java:289)
at com.google.devtools.mobileharness.infra.controller.test.local.LocalTestRunner$DecoratorEventGenerator.run(LocalTestRunner.java:399)
at com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidStartAppsDecorator.run(AndroidStartAppsDecorator.java:106)
at com.google.devtools.mobileharness.infra.controller.test.local.LocalTestRunner$DriverEventGenerator.run(LocalTestRunner.java:289)
at com.google.devtools.mobileharness.infra.controller.test.local.LocalTestFlow.runTest(LocalTestFlow.java:241)
at com.google.devtools.mobileharness.infra.controller.test.local.LocalTestRunner.runTest(LocalTestRunner.java:198)
at com.google.devtools.mobileharness.infra.controller.test.BaseTestRunner.execute(BaseTestRunner.java:374)
at com.google.devtools.mobileharness.infra.controller.test.local.LocalTestRunner.execute(LocalTestRunner.java:92)
at com.google.devtools.mobileharness.infra.controller.test.AbstractTestRunner.doExecute(AbstractTestRunner.java:111)
at com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher.executeTest(TestRunnerLauncher.java:100)
at com.google.devtools.mobileharness.infra.container.controller.ProxyToDirectTestRunner$ConnectorTestRunnerLauncher.executeProxiedTest(ProxyToDirectTestRunner.java:318)
at com.google.devtools.mobileharness.infra.container.controller.ProxyToDirectTestRunner.execute(ProxyToDirectTestRunner.java:191)
at com.google.devtools.mobileharness.infra.controller.test.AbstractTestRunner.doExecute(AbstractTestRunner.java:111)
at com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher.executeTest(TestRunnerLauncher.java:100)
at com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher.access$000(LocalDeviceTestRunnerLauncher.java:30)
at com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher$PrimaryDeviceTestExecutor.doExecuteTest(LocalDeviceTestRunnerLauncher.java:67)
at com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher$AbstractDeviceTestExecutor.executeTest(LocalDeviceTestRunnerLauncher.java:127)
at com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner.checkNRunTest(LocalDeviceRunner.java:789)
at com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner.run(LocalDeviceRunner.java:304)
at com.google.devtools.mobileharness.shared.util.concurrent.Callables.lambda$threadRenaming$1(Callables.java:249)
at io.grpc.Context.run(Context.java:536)
at com.google.tracing.LocalTraceSpanRunnable.run(LocalTraceSpanRunnable.java:59)
at com.google.devtools.mobileharness.shared.context.InvocationContext$RunnableWithContext.run(InvocationContext.java:149)
at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:572)
at com.google.common.util.concurrent.TrustedListenableFutureTask$TrustedFutureInterruptibleTask.runInterruptibly(TrustedListenableFutureTask.java:131)
at com.google.common.util.concurrent.InterruptibleTask.run(InterruptibleTask.java:74)
at com.google.common.util.concurrent.TrustedListenableFutureTask$TrustedFutureInterruptibleTask.run(TrustedListenableFutureTask.java:83)
at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
at java.base/java.lang.Thread.run(Thread.java:1656)
Caused by: com.google.devtools.mobileharness.platform.android.flash.FlashstationException: flash manager: aosp_cf_arm64_only_phone build cannot be flashed to a Xiaomi 13T Pro
at com.google.devtools.mobileharness.platform.android.flash.FlashstationLogUtil.getErrorFromLogFile(FlashstationLogUtil.java:147)
at com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidFlashstationDecorator.executeFlashAttempts(AndroidFlashstationDecorator.java:467)
... 35 more

2026-06-09 20:32:43:260 PDT I TIMELINE:POST_RUN_TEST:START:lab Start post-run test moreto_qiupingf_20260610033135Z on device(s) [HA8POBAQLVFYQKKZ@172.17.0.1/mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek]
2026-06-09 20:32:43:260 PDT I Post TestEndingEvent to test 71f02572-1262-4dce-9cab-20e7d3011c24@3a5b6e45-d7be-40c6-933f-0067198a67f5
2026-06-09 20:32:43:261 PDT I Saving test command history...
2026-06-09 20:32:43:278 PDT I --------- Device: PostRunTest for moreto_qiupingf_20260610033135Z (71f02572-1262-4dce-9cab-20e7d3011c24) ---------
2026-06-09 20:32:43:278 PDT I Post-run test of device [HA8POBAQLVFYQKKZ]
2026-06-09 20:32:43:496 PDT I Package com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2 md5 retrieved from device HA8POBAQLVFYQKKZ property \`installed_apk:user_0:com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2\` is: null
2026-06-09 20:32:43:568 PDT I Md5 for package com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2 installed on device HA8POBAQLVFYQKKZ for user 0 with path /data/app/~~RjjZ_M4fuSE6Dk1CPE9zZw==/com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2-LV4ktYihJZD9OQhMUfXcoA==/base.apk is: ecb42d2af141b423bc849acef997a561
2026-06-09 20:32:43:569 PDT I Skip installing daemon.apk which has been installed before
2026-06-09 20:32:43:569 PDT I Starting device daemon com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2.DaemonActivity on device HA8POBAQLVFYQKKZ...
2026-06-09 20:32:43:762 PDT I Prepare extras for device daemon with device id [HA8POBAQLVFYQKKZ], labels [], hostname [mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek], owners [[mobileharness-eng, dafeng, qiupingf, tianch]], executors [[]], ssid []
2026-06-09 20:32:43:819 PDT I Post-run test of device [HA8POBAQLVFYQKKZ] finished, result=NONE
2026-06-09 20:32:43:821 PDT I Adding common dimensions [host_version, host_name] shared among testbed to test 71f02572-1262-4dce-9cab-20e7d3011c24
2026-06-09 20:32:43:821 PDT I Post TestEndedEvent to test 71f02572-1262-4dce-9cab-20e7d3011c24@3a5b6e45-d7be-40c6-933f-0067198a67f5
2026-06-09 20:32:43:823 PDT I TIMELINE:POST_RUN_TEST:END:lab Post-run test moreto_qiupingf_20260610033135Z finished
2026-06-09 20:32:49:306 PDT I TIMELINE:RUN_TEST:END:client Run test moreto_qiupingf_20260610033135Z finished
2026-06-09 20:32:49:307 PDT I TIMELINE:POST_RUN_TEST:START:client Start post-run test moreto_qiupingf_20260610033135Z on device(s) [HA8POBAQLVFYQKKZ@172.17.0.1/mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek]
2026-06-09 20:32:49:307 PDT I Post TestEndingEvent to test 71f02572-1262-4dce-9cab-20e7d3011c24@3a5b6e45-d7be-40c6-933f-0067198a67f5
2026-06-09 20:32:49:309 PDT I ========= Client: PostRunTest (71f02572-1262-4dce-9cab-20e7d3011c24) =========
2026-06-09 20:32:52:347 PDT I Generated properties:
- _is_run_in_dm = false
- _test_engine_locator = enable_cloud_rpc: true
cloud_rpc_locator {
proxy_metadata {
endpoint: "lab_server_endpoint"
shard: "mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek"
}
file_transfer_proxy_metadata {
endpoint: "lab_cloud_file_transfer_endpoint"
shard: "mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek"
}
}
stubby_locator {
ip: "172.17.0.1"
host_name: "mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek"
port: 9999
}
enable_stubby: true
grpc_locator {
grpc_target: "dns:///mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek:9994"
host_name: "mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek"
grpc_port: 9994
host_ip: "172.17.0.1"
}
enable_grpc: true

- allocated_devices_lst_signin_support = not_supported
- allocated_quota_pool = mobileharness-satellite-lab-pool
- allocation_received_epoch_ms = 1781062308860
- allocation_requested_epoch_ms = 1781062300542
- allocation_time_ms = 8358
- allocation_time_sec = 8
- container_mode = false
- decorator_run_time_ms_AndroidStartAppsDecorator = 35
- device_done_epoch_ms = 1781062363260
- device_id_list = HA8POBAQLVFYQKKZ
- dimension_abi = arm64-v8a
- dimension_abilist = arm64-v8a,armeabi-v7a,armeabi
- dimension_baseband_version = moly.nr16.r2.mp1.tc8.pr1.sp.v3.p28,moly.nr16.r2.mp1.tc8.pr1.sp.v3.p28
- dimension_battery_level = 100
- dimension_battery_status = ok
- dimension_battery_temperature = 30
- dimension_bluetooth_mac_address = a4:cc:b3:8c:0e:1b
- dimension_brand = xiaomi
- dimension_build = UP1A.230905.011,up1a.230905.011
- dimension_build_alias = up1a.230905.011
- dimension_build_type = user
- dimension_characteristics = nosdcard
- dimension_codename = rel
- dimension_communication_type = ADB,USB
- dimension_control_id = HA8POBAQLVFYQKKZ
- dimension_cpu_freq_in_ghz = 2.0
- dimension_device = corot
- dimension_device_class_name = AndroidRealDevice
- dimension_device_form = physical
- dimension_device_supports_container = true
- dimension_dm_type = mh
- dimension_external_storage_status = ok
- dimension_feature = android.hardware.nfc
- dimension_free_external_storage = 209GB
- dimension_free_external_storage_percentage = 92.79%
- dimension_free_internal_storage = 209GB
- dimension_free_internal_storage_percentage = 92.79%
- dimension_gms_version = 26.08.34 (190400-876566425)
- dimension_gmscore_signature = 98deb0ca
- dimension_gsm_operator_alpha = ,
- dimension_hardware = mt6985
- dimension_host_ip = 172.17.0.1
- dimension_host_name = mobileharness-hp-z240-sff-workstation.floor5-google-beijing.pek
- dimension_host_os = Linux
- dimension_host_os_version = Ubuntu 22.04.5 LTS
- dimension_host_version = 4.370.0
- dimension_iccids =
- dimension_id = HA8POBAQLVFYQKKZ
- dimension_incremental_build = v816.0.0.2.umlmixm
- dimension_internal_storage_status = ok
- dimension_internet = false
- dimension_is_gki_kernel = true
- dimension_kernel_release_number = 5.15.104-android13-8-00010-g1dc49517c375-ab11006012
- dimension_lab_type = satellite
- dimension_language = en
- dimension_launcher_1 = false
- dimension_launcher_3 = false
- dimension_launcher_gel = false
- dimension_locale = en-gb
- dimension_location_type = not_in_china
- dimension_machine_hardware_name = aarch64
- dimension_manufacturer = xiaomi
- dimension_mcc_mnc =
- dimension_memory_class = 256m
- dimension_memory_class_in_mb = 256
- dimension_model = 23078pnd5g
- dimension_native_bridge = 0
- dimension_num_cpus = 8
- dimension_os = android
- dimension_preview_sdk_version = 0
- dimension_product_board = corot
- dimension_product_device = corot
- dimension_release_version = 14
- dimension_release_version_major = 14
- dimension_revision = 0
- dimension_rooted = false
- dimension_screen_density = 480
- dimension_screen_size = 1220x2712
- dimension_screenshot_able = true
- dimension_sdk_version = 34
- dimension_serial = HA8POBAQLVFYQKKZ,ha8pobaqlvfyqkkz
- dimension_sign = release-keys
- dimension_sim_state = absent,absent
- dimension_soc_model = mt6985
- dimension_supports_adhoc = true
- dimension_supports_gmscore = true
- dimension_svelte_device = false
- dimension_total_memory = 11332 MB
- dimension_type = corot_global
- dimension_uuid = HA8POBAQLVFYQKKZ
- dimension_uuid_volatile = false
- dimension_veritymode = enforcing
- dimension_writable_external_storage = /storage/emulated/0
- enable_v4_build_api = true
- environment = PROD
- exec_mode = remote
- file_lazy_resolve_flashstation = true
- flash_result_HA8POBAQLVFYQKKZ = FLASH_FAILED
- flash_wait_millis_for_quota_HA8POBAQLVFYQKKZ = 0
- flashstation_client_email = mh-web-flashstation@mobile-harness-lab-server.google.com.iam.gserviceaccount.com
- flashstation_project_id = google.com:mobile-harness-lab-server
- hybrid_utp_detailed_incompatible_reason_AcidRemoteDriver = no_converter
- hybrid_utp_summary_incompatible_reason = no_converter(AcidRemoteDriver)
- lab_test_gen_file_dir = /var/www/mh_lab_gen_files/3a5b6e45-d7be-40c6-933f-0067198a67f5/test_71f02572-1262-4dce-9cab-20e7d3011c24
- master_central_suspended_duration_sec = 0
- master_scheduler_latency_sec = 6
- master_scheduler_latency_type = DEVICE_WAIT_FOR_TEST
- start_delay_ms = 0
- test_link_in_mhfe = http://mobileharness-fe/testdetailview/3a5b6e45-d7be-40c6-933f-0067198a67f5/71f02572-1262-4dce-9cab-20e7d3011c24
- use_mh_flashstation_key = true
2026-06-09 20:32:52:348 PDT I No test warnings on Lab Server side
2026-06-09 20:32:52:348 PDT I Downloading 4 test generated file(s) for :
- command_history.txt
- flashstation_decorator_protos/HA8POBAQLVFYQKKZ/allocation_wait_HA8POBAQLVFYQKKZ.pb.bin
- flashstation_decorator_protos/HA8POBAQLVFYQKKZ/flashing_log_proto_attempt_1.pb
- flashstation_decorator_protos/HA8POBAQLVFYQKKZ/flashing_attempt_HA8POBAQLVFYQKKZ_attempt_1.pb.bin
2026-06-09 20:32:52:349 PDT I TIMELINE:DOWNLOAD_FILES:START: Starting download test generated file directory
2026-06-09 20:32:56:267 PDT I TIMELINE:DOWNLOAD_FILES:END: Downloaded genfile directory /tmp/acid-realm-alkali-xcid/tmp/mh_gen_9d7c21ae-3da4-4800-85d9-ff7a3275377f/test_71f02572-1262-4dce-9cab-20e7d3011c24 (size: 3.95KB)
2026-06-09 20:32:56:270 PDT I
FileTransfer report:
send 1 files (with 1 files cached); total size: 50.74MB (with 50.74MB cached); total time: PT4.033079S; speed: 10571697.36 B/s (with cache), 0.00 B/s (without cache)
get 1 files; total size: 3.95KB; total time: PT3.912818S; speed: 822.95 B/s
2026-06-09 20:32:58:893 PDT I Post TestEndedEvent to test 71f02572-1262-4dce-9cab-20e7d3011c24@3a5b6e45-d7be-40c6-933f-0067198a67f5
2026-06-09 20:32:58:914 PDT I No UTP command history
2026-06-09 20:32:58:974 PDT I Start uploading test generated files
2026-06-09 20:32:58:975 PDT I Saved the test log to /tmp/acid-realm-alkali-xcid/tmp/mh_gen_9d7c21ae-3da4-4800-85d9-ff7a3275377f/test_71f02572-1262-4dce-9cab-20e7d3011c24/test_output.txt
2026-06-09 20:32:58:976 PDT I No external_storage_path specified, skip copying test generated files to CNS.
2026-06-09 20:32:58:977 PDT I Start uploading test data to GoogleAnalytics
2026-06-09 20:32:58:983 PDT I Finished uploading test data to GoogleAnalytics`,
  cloudLogLink: '#',
};
