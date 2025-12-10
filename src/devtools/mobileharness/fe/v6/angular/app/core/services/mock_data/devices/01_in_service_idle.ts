/**
 * @fileoverview Mock data for the "In Service, IDLE, Android" device scenario.
 * This scenario represents a healthy, fully configured Android device that is
 * ready to accept new tasks.
 */

import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = '43021FDAQ000UM';

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-a-1.prod.example.com', ip: '192.168.1.101'},
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {status: 'IDLE', isCritical: false},
    deviceTypes: [
      'AndroidDevice',
      'AndroidFlashableDevice',
      'AndroidOnlineDevice',
      'AndroidRealDevice',
    ].map((type) => ({type, isAbnormal: false})),
    lastInServiceTime: new Date().toISOString(),
  },
  basicInfo: {
    model: 'Pixel 8 Pro',
    version: '14',
    form: 'physical',
    os: 'Android',
    batteryLevel: 95,
    network: {wifiRssi: -58, hasInternet: true},
    hardware: 'g/2345a',
    build: 'AP1A.240405.002',
  },
  permissions: {
    owners: ['user-a', 'group-infra-team', 'derekchen'],
    executors: ['test-runner-service-account', 'auto-recovery-service'],
  },
  capabilities: {
    supportedDrivers: [
      'AndroidGUnit',
      'AndroidInstrumentation',
      'AndroidMonkey',
      'AndroidRoboTest',
      'AndroidTradefedTest',
      'FlutterDriver',
      'MoblyAospTest',
      'MoblyTest',
    ],
    supportedDecorators: [
      'AndroidBugreportDecorator',
      'AndroidCrashMonitorDecorator',
      'AndroidFilePullerDecorator',
      'AndroidLogCatDecorator',
      'AndroidScreenshotDecorator',
    ],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [
          {name: 'pool', value: 'pixel-prod'},
          {name: 'os', value: '14'},
        ],
      },
      'Detected by OmniLab': {
        dimensions: [
          {name: 'abi', value: 'arm64-v8a'},
          {name: 'abilist', value: 'arm64-v8a,armeabi-v7a,armeabi'},
          {
            name: 'baseband_version',
            value: 'qb5491625_s5318ap_erd_sgc,qb5491625_s5318ap_erd_sgc',
          },
          {name: 'battery_level', value: '97'},
          {name: 'battery_status', value: 'ok'},
          {name: 'battery_temperature', value: '25'},
          {name: 'bluetooth_mac_address', value: '9c:71:e3:10:54:ac'},
          {name: 'brand', value: 'exynos'},
          {
            name: 'build',
            value: 'essi-userdebug 16 ZP1A.251203.001 14536131 test-keys',
          },
          {
            name: 'build',
            value: 'essi-userdebug 16 zp1a.251203.001 14536131 test-keys',
          },
          {name: 'build_alias', value: 'zp1a.251203.001'},
          {name: 'build_type', value: 'userdebug'},
          {name: 'characteristics', value: 'phone'},
          {name: 'cluster', value: 'seolab-pool-01'},
          {name: 'cluster', value: 'seo-exp'},
          {name: 'codename', value: 'rel'},
          {name: 'communication_type', value: 'USB'},
          {name: 'communication_type', value: 'ADB'},
          {name: 'control_id', value: '00000054ac9c71e3'},
          {name: 'cpu_freq_in_ghz', value: '2.0'},
          {name: 'device', value: 'erd8835'},
          {name: 'device_class_name', value: 'AndroidRealDevice'},
          {name: 'device_form', value: 'physical'},
          {name: 'device_supports_container', value: 'true'},
          {name: 'dm_type', value: 'mh'},
          {name: 'external_storage_status', value: 'ok'},
          {name: 'free_external_storage', value: '107GB'},
          {name: 'free_external_storage_percentage', value: '98.35%'},
          {name: 'free_internal_storage', value: '107GB'},
          {name: 'free_internal_storage_percentage', value: '98.35%'},
          {name: 'gms_version', value: ''},
          {name: 'gmscore_signature', value: ''},
          {name: 'gsm_operator_alpha', value: 'sktelecom,'},
          {name: 'hardware', value: 's5e8835'},
          {name: 'host_ip', value: '100.114.242.21'},
          {name: 'host_name', value: 'dockerized-tf-seo-ks21'},
          {name: 'host_os', value: 'Linux'},
          {name: 'host_os_version', value: 'Ubuntu 22.04.4 LTS'},
          {name: 'host_version', value: '4.344.0'},
          {name: 'iccids', value: ''},
          {name: 'id', value: '00000054ac9c71e3'},
          {name: 'incremental_build', value: '14536131'},
          {name: 'internal_storage_status', value: 'ok'},
          {name: 'internet', value: 'false'},
          {name: 'is_gki_kernel', value: 'true'},
          {
            name: 'kernel_release_number',
            value: '5.15.196-android13-8-00619-gffebbfb0540d-ab14538510',
          },
          {name: 'lab_supports_container', value: 'true'},
          {name: 'lab_type', value: 'satellite'},
          {name: 'language', value: 'en'},
          {name: 'launcher_1', value: 'false'},
          {name: 'launcher_3', value: 'true'},
          {name: 'launcher_gel', value: 'false'},
          {name: 'locale', value: 'en-us'},
          {name: 'location_type', value: 'not_in_china'},
          {name: 'mac_address', value: '00:00:00:00:00:00'},
          {name: 'machine_hardware_name', value: 'aarch64'},
          {name: 'manufacturer', value: 'samsung electronics co., ltd.'},
          {name: 'mcc_mnc', value: ''},
          {name: 'memory_class', value: '256m'},
          {name: 'memory_class_in_mb', value: '256'},
          {name: 'model', value: 'full android on s5e8835 erd'},
          {name: 'native_bridge', value: '0'},
          {name: 'num_cpus', value: '8'},
          {name: 'os', value: 'android'},
          {name: 'preview_sdk_version', value: '0'},
          {name: 'product_board', value: 's5e8835'},
          {name: 'product_device', value: 'erd8835'},
          {name: 'release_version', value: '16'},
          {name: 'release_version_major', value: '16'},
          {name: 'revision', value: '0'},
          {name: 'rooted', value: 'true'},
          {name: 'run_target', value: 's5e8835'},
          {name: 'screen_density', value: '480'},
          {name: 'screen_size', value: '1080x2400'},
          {name: 'screenshot_able', value: 'true'},
          {name: 'sdk_version', value: '36'},
          {name: 'serial', value: '00000054ac9c71e3'},
          {name: 'sign', value: 'test-keys'},
          {name: 'sim_state', value: 'absent,absent'},
          {name: 'supports_adhoc', value: 'true'},
          {name: 'supports_gmscore', value: 'true'},
          {name: 'svelte_device', value: 'false'},
          {name: 'total_memory', value: '4356 MB'},
          {name: 'type', value: 'full_erd8835_t'},
          {name: 'uuid', value: '00000054ac9c71e3'},
          {name: 'uuid_volatile', value: 'false'},
          {name: 'veritymode', value: 'enforcing'},
          {name: 'writable_external_storage', value: '/storage/emulated/0'},
        ],
      },
    },
    required: {
      'From Device Config': {
        dimensions: [{name: 'min-sdk', value: '33'}],
      },
    },
  },
  properties: {
    'test-type': 'instrumentation',
    'max-run-time': '3600',
    'network-requirement': 'full',
    'encryption-state': 'encrypted',
  },
};

const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['user-a', 'group-infra-team', 'derekchen'],
    executors: ['test-runner-service-account', 'auto-recovery-service'],
  },
  wifi: {
    type: 'pre-configured',
    ssid: 'GoogleGuest',
    psk: 'some-secure-password',
    scanSsid: true,
  },
  dimensions: {
    supported: [
      {name: 'pool', value: 'pixel-prod'},
      {name: 'os', value: '14'},
    ],
    required: [{name: 'min-sdk', value: '33'}],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/**
 * Represents a mock device scenario where the device is in service and idle.
 * This scenario is used for testing and development purposes to simulate a
 * device in a specific state.
 */
export const SCENARIO_IN_SERVICE_IDLE: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'In Service - Idle',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  actionVisibility: {
    screenshot: true,
    logcat: true,
    flash: true,
    remoteControl: true,
    quarantine: true,
  },
};
