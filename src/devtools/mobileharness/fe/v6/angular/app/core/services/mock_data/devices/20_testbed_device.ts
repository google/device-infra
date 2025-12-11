import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview, TestbedConfig} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'eos_connectivity_36081RTJWN0DVK';
const HOST_NAME = 'aw-zo3.sjc.corp.example.com';

/** Mock device config. */
export const MOCK_CONFIG_TESTBED_DEVICE: DeviceConfig = {
  permissions: {
    owners: ['testbed-owner'],
    executors: ['testbed-runner'],
  },
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {
    supported: [{name: 'monsoon_id', value: '10831'}],
    required: [],
  },
  settings: {
    maxConsecutiveFail: 5,
    maxConsecutiveTest: 10000,
  },
};

/** Mock device overview. */
export const MOCK_OVERVIEW_TESTBED_DEVICE: DeviceOverview = {
  id: DEVICE_ID,
  host: {
    name: HOST_NAME,
    ip: '192.168.100.1',
  },
  healthAndActivity: {
    title: 'In Service (Idle)',
    subtitle: 'The device is healthy and ready for new tasks.',
    state: 'IN_SERVICE_IDLE',
    deviceStatus: {
      status: 'IDLE',
      isCritical: false,
    },
    deviceTypes: [
      {
        type: 'TestbedDevice',
        isAbnormal: false,
      },
      {
        type: 'MoblyDevice',
        isAbnormal: false,
      },
      {
        type: 'AbnormalTestbedDevice',
        isAbnormal: true,
      },
    ],
    lastInServiceTime: '2025-09-09T03:33:47.715Z',
  },
  basicInfo: {
    model: 'Testbed',
    version: 'N/A',
    form: 'testbed',
    os: 'N/A',
    batteryLevel: null,
    network: {},
  },
  permissions: MOCK_CONFIG_TESTBED_DEVICE.permissions,
  capabilities: {
    supportedDrivers: [
      'AcidRemoteDriver',
      'AndroidCommsActsTest',
      'AndroidNuwa',
      'GoogleTradefedDriver',
      'MoblyAospTest',
      'MoblyTest',
      'NoOpDriver',
      'VinsonDriver',
    ],
    supportedDecorators: [
      'CompositeDeviceDecoratorAdapter',
      'MoblyDecoratorAdapter',
      'MoblyMonsoonDecorator',
      'NoOpDecorator',
      'TestbedDecoratorAdapter',
    ],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [{name: 'testbed_type', value: 'multi_device'}],
      },
    },
    required: {},
  },
  properties: {},
  subDevices: [
    {
      id: '36081RTJWN0DVK',
      types: [
        {type: 'AndroidRealDevice', isAbnormal: false},
        {type: 'AndroidFlashableDevice', isAbnormal: false},
        {type: 'AndroidOnlineDevice', isAbnormal: false},
        {type: 'AndroidDevice', isAbnormal: false},
      ],
      dimensions: [
        {name: 'num_cpus', value: '4'},
        {name: 'sign', value: 'dev-keys'},
        {name: 'language', value: 'en'},
        {name: 'incremental_build', value: '13624569'},
        {name: 'gmscore_signature', value: ''},
        {name: 'supports_adhoc', value: 'true'},
        {name: 'type', value: 'eos'},
        {name: 'product_device', value: 'eos'},
        {name: 'device_form', value: 'physical'},
        {name: 'veritymode', value: 'enforcing'},
        {name: 'memory_class', value: '128m'},
        {name: 'host_version', value: '4.342.0'},
        {name: 'model', value: 'google pixel watch 2'},
        {name: 'screen_size', value: '384x384'},
        {name: 'release_version', value: '16'},
        {name: 'brand', value: 'google'},
        {name: 'build_type', value: 'userdebug'},
        {name: 'hardware', value: 'eos'},
        {name: 'free_internal_storage', value: '18.3GB'},
        {name: 'writable_external_storage', value: '/storage/emulated/0'},
        {name: 'mcc_mnc', value: ''},
        {name: 'host_os', value: 'Linux'},
        {name: 'memory_class_in_mb', value: '128'},
        {name: 'revision', value: 'dvt1.0'},
        {name: 'battery_status', value: 'low'},
        {name: 'battery_temperature', value: '19'},
        {name: 'external_storage_status', value: 'ok'},
        {name: 'screen_density', value: '320'},
        {name: 'codename', value: 'baklava'},
        {name: 'free_internal_storage_percentage', value: '85.47%'},
        {name: 'device', value: 'eos'},
        {name: 'product_board', value: 'eos'},
        {name: 'internet', value: 'false'},
        {name: 'release_version_major', value: '16'},
        {name: 'communication_type', value: 'USB'},
        {name: 'communication_type', value: 'ADB'},
        {name: 'mh_device_type', value: 'AndroidRealDevice'},
        {name: 'mh_device_type', value: 'AndroidFlashableDevice'},
        {name: 'mh_device_type', value: 'AndroidOnlineDevice'},
        {name: 'mh_device_type', value: 'AndroidDevice'},
        {name: 'iccids', value: ''},
        {name: 'color', value: 'blk'},
        {name: 'free_external_storage', value: '18.3GB'},
        {name: 'wifi-password', value: 'androidtest123'},
        {name: 'launcher_1', value: 'false'},
        {name: 'lab_type', value: 'satellite'},
        {name: 'locale', value: 'en-us'},
        {name: 'lab_location', value: 'sjc'},
        {name: 'launcher_3', value: 'false'},
        {name: 'manufacturer', value: 'google'},
        {name: 'native_bridge', value: '0'},
        {name: 'internal_storage_status', value: 'ok'},
        {name: 'free_external_storage_percentage', value: '85.47%'},
        {name: 'dm_type', value: 'mh'},
        {name: 'total_memory', value: '1744 MB'},
        {name: 'feature', value: 'android.hardware.type.watch'},
        {
          name: 'feature',
          value: 'com.google.android.feature.PIXEL_EXPERIENCE_WATCH',
        },
        {name: 'feature', value: 'android.hardware.ram.low'},
        {name: 'launcher_gel', value: 'false'},
        {name: 'host_os_version', value: 'Ubuntu 20.04.6 LTS'},
        {name: 'mac_address', value: '3a:ed:9e:fd:d7:0b'},
        {name: 'sdk_version', value: '36'},
        {name: 'rooted', value: 'true'},
        {name: 'baseband_version', value: 'g5100-00273.2-250529-b-13569164'},
        {name: 'machine_hardware_name', value: 'armv8l'},
        {name: 'characteristics', value: 'nosdcard,watch'},
        {name: 'host_ip', value: '100.95.189.25'},
        {name: 'os', value: 'android'},
        {name: 'battery_level', value: '18'},
        {name: 'secure_boot', value: 'production'},
        {name: 'bluetooth_mac_address', value: '24:95:2f:93:38:03'},
        {name: 'screenshot_able', value: 'true'},
        {name: 'device_supports_container', value: 'true'},
        {name: 'abi', value: 'armeabi-v7a'},
        {name: 'gservices_android_id', value: ''},
        {name: 'gms_version', value: '25.20.37 (240300-766884605)'},
        {name: 'sim_state', value: 'not_ready'},
        {name: 'location_type', value: 'not_in_china'},
        {name: 'cpu_freq_in_ghz', value: '1.7'},
        {name: 'supports_gmscore', value: 'true'},
        {name: 'wifi-ssid', value: 'WL-aw-zo3'},
        {name: 'lab_supports_container', value: 'true'},
        {name: 'svelte_device', value: 'false'},
        {name: 'build', value: 'eos-userdebug Baklava MAIN 13624569 dev-keys'},
        {name: 'build', value: 'eos-userdebug baklava main 13624569 dev-keys'},
        {name: 'serial', value: '36081RTJWN0DVK'},
        {name: 'serial', value: '36081rtjwn0dvk'},
        {name: 'abilist', value: 'armeabi-v7a,armeabi'},
        {name: 'idiom', value: 'watch'},
        {name: 'build_alias', value: 'main'},
        {name: 'preview_sdk_version', value: '1'},
        {name: 'lab_supports_sandbox', value: 'true'},
        {name: 'host_name', value: 'aw-zo3.sjc.corp.example.com'},
      ],
    },
    {
      id: '31311JEHN09713',
      types: [
        {type: 'AndroidRealDevice', isAbnormal: false},
        {type: 'AndroidFlashableDevice', isAbnormal: false},
        {type: 'AndroidOnlineDevice', isAbnormal: false},
        {type: 'AndroidDevice', isAbnormal: false},
      ],
      dimensions: [
        {name: 'num_cpus', value: '8'},
        {name: 'sign', value: 'dev-keys'},
        {name: 'language', value: 'en'},
        {name: 'incremental_build', value: '10753802'},
        {name: 'gmscore_signature', value: 'f1014c4'},
        {name: 'supports_adhoc', value: 'true'},
        {name: 'type', value: 'lynx'},
        {name: 'product_device', value: 'lynx'},
        {name: 'device_form', value: 'physical'},
        {name: 'veritymode', value: 'enforcing'},
        {name: 'memory_class', value: '256m'},
        {name: 'host_version', value: '4.342.0'},
        {name: 'gsm_operator_alpha', value: ','},
        {name: 'model', value: 'pixel 7a'},
        {name: 'screen_size', value: '1080x2400'},
        {name: 'release_version', value: '13'},
        {name: 'brand', value: 'google'},
        {name: 'build_type', value: 'userdebug'},
        {name: 'soc_rev', value: '2'},
        {name: 'nonsec_ar', value: '2'},
        {name: 'hardware', value: 'lynx'},
        {name: 'free_internal_storage', value: '94.49GB'},
        {name: 'sec_ar', value: '2'},
        {name: 'writable_external_storage', value: '/storage/emulated/0'},
        {name: 'mcc_mnc', value: ','},
        {name: 'sim_operator_alpha', value: ','},
        {name: 'host_os', value: 'Linux'},
        {name: 'memory_class_in_mb', value: '256'},
        {name: 'revision', value: 'mp1.0'},
        {name: 'battery_status', value: 'ok'},
        {name: 'battery_temperature', value: '18'},
        {name: 'external_storage_status', value: 'ok'},
        {name: 'screen_density', value: '420'},
        {name: 'codename', value: 'rel'},
        {name: 'free_internal_storage_percentage', value: '85.97%'},
        {name: 'device', value: 'lynx'},
        {name: 'product_board', value: 'lynx'},
        {name: 'internet', value: 'false'},
        {name: 'release_version_major', value: '13'},
        {name: 'communication_type', value: 'USB'},
        {name: 'communication_type', value: 'ADB'},
        {name: 'mh_device_type', value: 'AndroidRealDevice'},
        {name: 'mh_device_type', value: 'AndroidFlashableDevice'},
        {name: 'mh_device_type', value: 'AndroidOnlineDevice'},
        {name: 'mh_device_type', value: 'AndroidDevice'},
        {name: 'iccids', value: ''},
        {name: 'color', value: 'wht'},
        {name: 'hardware_ufs', value: '128gb,micron'},
        {name: 'free_external_storage', value: '94.49GB'},
        {name: 'wifi-password', value: 'androidtest123'},
        {name: 'launcher_1', value: 'false'},
        {name: 'lab_type', value: 'satellite'},
        {name: 'locale', value: 'en-us'},
        {name: 'lab_location', value: 'sjc'},
        {name: 'launcher_3', value: 'false'},
        {name: 'manufacturer', value: 'google'},
        {name: 'native_bridge', value: '0'},
        {name: 'internal_storage_status', value: 'ok'},
        {name: 'free_external_storage_percentage', value: '85.97%'},
        {name: 'dm_type', value: 'mh'},
        {name: 'total_memory', value: '7423 MB'},
        {name: 'soc_id', value: '0b340e5e0088'},
        {name: 'feature', value: 'com.google.android.feature.PIXEL_EXPERIENCE'},
        {name: 'feature', value: 'android.hardware.nfc'},
        {name: 'launcher_gel', value: 'false'},
        {name: 'host_os_version', value: 'Ubuntu 20.04.6 LTS'},
        {name: 'mac_address', value: '24:95:2f:cc:66:3d'},
        {name: 'sdk_version', value: '33'},
        {name: 'rooted', value: 'true'},
        {
          name: 'baseband_version',
          value:
            'g5300g-230323-230525-b-10205083,g5300g-230323-230525-b-10205083',
        },
        {name: 'machine_hardware_name', value: 'aarch64'},
        {name: 'characteristics', value: 'nosdcard'},
        {name: 'host_ip', value: '100.95.189.25'},
        {name: 'os', value: 'android'},
        {name: 'battery_level', value: '74'},
        {name: 'secure_boot', value: 'production'},
        {name: 'bluetooth_mac_address', value: '24:95:2f:cc:d0:55'},
        {name: 'screenshot_able', value: 'true'},
        {name: 'device_supports_container', value: 'true'},
        {name: 'abi', value: 'arm64-v8a'},
        {name: 'gservices_android_id', value: ''},
        {name: 'gms_version', value: '22.46.19 (190408-515739919)'},
        {name: 'sim_state', value: 'absent,not_ready'},
        {name: 'location_type', value: 'not_in_china'},
        {name: 'cpu_freq_in_ghz', value: '1.8'},
        {name: 'supports_gmscore', value: 'true'},
        {name: 'wifi-ssid', value: 'WL-aw-zo3'},
        {name: 'lab_supports_container', value: 'true'},
        {name: 'svelte_device', value: 'false'},
        {
          name: 'build',
          value: 'lynx-userdebug 13 TQ3A.230901.001.C3 10753802 dev-keys',
        },
        {
          name: 'build',
          value: 'lynx-userdebug 13 tq3a.230901.001.c3 10753802 dev-keys',
        },
        {name: 'serial', value: '31311JEHN09713'},
        {name: 'serial', value: '31311jehn09713'},
        {name: 'abilist', value: 'arm64-v8a'},
        {name: 'idiom', value: 'phone'},
        {name: 'build_alias', value: 'tq3a.230901.001.c3'},
        {name: 'preview_sdk_version', value: '0'},
        {name: 'lab_supports_sandbox', value: 'true'},
        {name: 'host_name', value: 'aw-zo3.sjc.corp.example.com'},
      ],
    },
    {
      id: 'attenuator_1',
      types: [
        {type: 'AbnormalAndroidFlashableDevice', isAbnormal: true},
        {type: 'MiscDevice', isAbnormal: false},
        {type: 'MiscTestbedSubDevice', isAbnormal: false},
      ],
      dimensions: [
        {name: 'mh_device_type', value: 'MiscDevice'},
        {name: 'mh_device_type', value: 'MiscTestbedSubDevice'},
        {name: 'host_ip', value: '100.95.189.25'},
        {name: 'host_os', value: 'Linux'},
        {name: 'lab_type', value: 'satellite'},
        {name: 'supports_adhoc', value: 'true'},
        {name: 'lab_location', value: 'sjc'},
        {name: 'location_type', value: 'not_in_china'},
        {name: 'dm_type', value: 'mh'},
        {name: 'lab_supports_container', value: 'true'},
        {name: 'host_os_version', value: 'Ubuntu 20.04.6 LTS'},
        {name: 'host_version', value: '4.342.0'},
        {name: 'mobly_type', value: 'Attenuator'},
        {name: 'lab_supports_sandbox', value: 'true'},
        {name: 'host_name', value: 'aw-zo3.sjc.corp.example.com'},
      ],
    },
  ],
};

/** Mock testbed config for FusionMHTestBed. */
export const MOCK_TESTBED_CONFIG_TESTBED_DEVICE: TestbedConfig = {
  yamlContent: `- name: eos_connectivity_36081RTJWN0DVK
  dimensions:
    pool: wear_connectivity_lab
    label: wear_connectivity_lab
    board_type: eos-nff3
    watch_name: eos
    has_iphone: false
    monsoon: '10831'
    monsoon_only: 'true'
    environment: shieldbox
  devices:
  - id: '36081RTJWN0DVK'
    name: eos
    type: AndroidRealDevice
    dimensions:
      idiom: watch
      wifi-ssid: 'WL-aw-zo3'
      wifi-password: 'androidtest123'
    aliases:
    - AndroidRealOnlineDevice
    properties:
      default_wifi_ssid: 'WL-aw-zo3'
      default_wifi_psk: 'androidtest123'
  - id: '31311JEHN09713'
    name: lynx
    type: AndroidRealDevice
    dimensions:
      idiom: phone
      wifi-ssid: 'WL-aw-zo3'
      wifi-password: 'androidtest123'
    aliases:
    - AndroidRealOnlineDevice
    properties:
      default_wifi_ssid: 'WL-aw-zo3'
      default_wifi_psk: 'androidtest123'
  - id: attenuator_1
    type: MiscTestbedSubDevice
    dimensions:
      mobly_type: Attenuator
    properties:
      part_no: "RDCAT-6000-90"
      address: "192.168.2.20"
      port: 23
      paths: ["AP-DUT"]
      model: "minicircuits"`,
  codeSearchLink: `configs/devtools/deviceinfra/service/deviceconfig/testbed/prod/wearos/aw-zo3.yaml`,
};

/** Mock device scenario for TESTBED-001. */
export const SCENARIO_TESTBED_DEVICE: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '20. Testbed Device with Sub-Devices',
  overview: MOCK_OVERVIEW_TESTBED_DEVICE,
  config: MOCK_CONFIG_TESTBED_DEVICE,
  isQuarantined: false,
  testbedConfig: MOCK_TESTBED_CONFIG_TESTBED_DEVICE,
};
