import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview, TestbedConfig} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'FusionMHTestBed';
const HOST_NAME = 'host-tb-1.example.google.com';

/** Mock device config for FusionMHTestBed. */
export const MOCK_CONFIG_TESTBED_DEVICE: DeviceConfig = {
  permissions: {
    owners: ['testbed-owner'],
    executors: ['testbed-runner'],
  },
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {
    maxConsecutiveFail: 5,
    maxConsecutiveTest: 10000,
  },
};

/** Mock device overview for FusionMHTestBed. */
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
    supportedDrivers: ['MoblyTest'],
    supportedDecorators: [],
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
      id: 'Q5AJ-5ZEW-BHN8',
      types: [
        {type: 'MiscDevice', isAbnormal: false},
        {type: 'MiscTestbedSubDevice', isAbnormal: false},
      ],
      dimensions: [
        {name: 'mh_device_type', value: 'MiscDevice'},
        {name: 'mh_device_type', value: 'MiscTestbedSubDevice'},
        {name: 'host_ip', value: '100.107.0.8'},
        {name: 'host_os', value: 'Linux'},
      ],
    },
    {
      id: 'Slowpoke_sp1',
      types: [
        {type: 'MiscDevice', isAbnormal: false},
        {type: 'MiscTestbedSubDevice', isAbnormal: false},
      ],
      dimensions: [
        {name: 'mh_device_type', value: 'MiscDevice'},
        {name: 'mh_device_type', value: 'MiscTestbedSubDevice'},
        {name: 'host_ip', value: '100.107.0.8'},
      ],
    },
    {
      id: '41081FDAS000YB',
      types: [
        {type: 'AbnormalAndroidFlashableDevice', isAbnormal: true},
        {type: 'AndroidFlashableDevice', isAbnormal: false},
        {type: 'AndroidOnlineDevice', isAbnormal: false},
        {type: 'AndroidDevice', isAbnormal: false},
      ],
      dimensions: [
        {name: 'num_cpus', value: '8'},
        {name: 'sign', value: 'dev-keys'},
        {name: 'type', value: 'komodo'},
      ],
    },
  ],
};

/** Mock testbed config for FusionMHTestBed. */
export const MOCK_TESTBED_CONFIG_TESTBED_DEVICE: TestbedConfig = {
  yamlContent: `- name: FusionMHTestBed
    devices:
      - id: A10A20-DVT-FF-id-sb90-ws_11
        type: MiscTestbedSubDevice
        properties:
          model: "allegro"
          case_serial: "ef0c_HOOKSH_ef0c"
          left_serial: "L_df82_SPI_L_df82"
          right_serial: "R_6a8d_SPI_R_6a8d"
          a10_hardware_type: "dvt"
          a20_hardware_type: "dvt"
          working_directory_type: "user"
          reset: true
        dimensions:
          mobly_type: PixelBudsDevice
          model: A10A20-DVT-FF
      - id: 17241FDF6000NZ
        type: AndroidRealDevice
        name: oriole
        dimensions:
          use_mobly_decorator_adapter: true
      - id: soundcard_1
        type: MiscTestbedSubDevice
        properties:
          usb_address: "usb-0000:00:14.0-1.4"
        dimensions:
          mobly_type: LinuxSoundcardDevice
          model: allegro_pixel
          label: a10a20_dvt`,
  codeSearchLink: `configs/devtools/deviceinfra/service/deviceconfig/testbed/staging/testing/pixel_buds_flash_decorator_testbed.yaml`,
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
