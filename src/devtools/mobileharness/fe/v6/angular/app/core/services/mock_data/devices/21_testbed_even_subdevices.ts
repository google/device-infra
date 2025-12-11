import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview, TestbedConfig} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'TESTBED-EVEN-002';
const HOST_NAME = 'host-tb-2.example.com';

/** Mock device config for TESTBED-EVEN-002. */
const CONFIG: DeviceConfig = {
  permissions: {
    owners: ['testbed-owner-2'],
    executors: ['testbed-runner-2'],
  },
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {supported: [], required: []},
  settings: {
    maxConsecutiveFail: 5,
    maxConsecutiveTest: 10000,
  },
};

/** Mock device overview for TESTBED-EVEN-002. */
const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {
    name: HOST_NAME,
    ip: '192.168.100.2',
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
  permissions: CONFIG.permissions,
  capabilities: {
    supportedDrivers: ['MoblyTest'],
    supportedDecorators: [],
  },
  dimensions: {
    supported: {
      'From Device Config': {
        dimensions: [{name: 'testbed_type', value: 'multi_device_even'}],
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
        {type: 'AndroidRealDevice', isAbnormal: false},
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
    {
      id: '41081FDAS000YC',
      types: [
        {type: 'AndroidRealDevice', isAbnormal: false},
        {type: 'AndroidOnlineDevice', isAbnormal: false},
        {type: 'AndroidDevice', isAbnormal: false},
      ],
      dimensions: [
        {name: 'num_cpus', value: '8'},
        {name: 'sign', value: 'dev-keys'},
        {name: 'type', value: 'caiman'},
      ],
    },
  ],
};

/** Mock testbed config for TESTBED-EVEN-002. */
export const MOCK_TESTBED_CONFIG_TESTBED_DEVICE: TestbedConfig = {
  yamlContent: `- name: FiOndemandVCNTestbed-1
  devices:
    - id: 'R4880120031'
      type: MiscTestbedSubDevice
      properties:
          device_id: "R4880120031"
          baudrate: 9600
          channel_count: 8
      dimensions:
          mobly_type: AdauraController
    - id: 'Q5AJ-5ZEW-BHN8'
      type: MiscTestbedSubDevice
      properties:
          serial: 'Q5AJ-5ZEW-BHN8'
          network_id: 'N_636133447366094612'
          api_key: '49a103315741d3da7296a9a80fbbcd2175184b19'
      dimensions:
          mobly_type: MerakiApController
    - id: 'Slowpoke_sp1'
      type: MiscTestbedSubDevice
      properties:
          version: 'v2'
          hostname: '100.107.0.12'
          username: 'orion'
          password: 'orionw1f1'
      dimensions:
          mobly_type: SlowpokeController
    - id: 'R3CW808Q66R'
      type: AndroidRealDevice
      dimensions:
          pool: ondemand-vpn-mobly-attenuator`,
  codeSearchLink: `configs/devtools/deviceinfra/service/deviceconfig/testbed/prod/pixel-bluetooth/pqm-bt-rangne-test-2-lab/sunfish_conductive_bds.yaml`,
};

/** Mock device scenario for TESTBED-EVEN-002. */
export const SCENARIO_TESTBED_EVEN_SUBDEVICES: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: '21. Testbed Device with Even Sub-Devices',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  testbedConfig: MOCK_TESTBED_CONFIG_TESTBED_DEVICE,
};
