import {MockHostScenario, MockHostScenarioWrapper} from '../models';
import {scenarioHostLocalMtt} from './local_mtt_host';
import {SCENARIO_HOST_NO_CONFIG} from './01_no_config';
import {SCENARIO_HOST_BASIC_EDITABLE} from './02_basic_editable';
import {SCENARIO_HOST_SHARED_MODE} from './03_shared_mode';
import {SCENARIO_HOST_PUSHER_PROPERTIES} from './04_pusher_properties_only';
import {SCENARIO_HOST_PUSHER_ITEM_OVERRIDE} from './05_pusher_properties_item_override';
import {SCENARIO_HOST_PUSHER_ALL} from './06_pusher_all';
import {SCENARIO_HOST_SSH_HIDDEN} from './07_ssh_access_hidden';
import {SCENARIO_HOST_DISCOVERY_HIDDEN} from './08_device_discovery_hidden';
import {SCENARIO_HOST_DEVICE_CONFIG_HIDDEN} from './09_device_config_hidden';
import {SCENARIO_HOST_DEVICE_CONFIG_WIFI_DIMENSIONS_ONLY} from './10_device_config_wifi_dimensions_only';
import {SCENARIO_HOST_COMING_SOON} from './11_coming_soon';
import {SCENARIO_HOST_NO_VALID_VERSIONS} from './12_no_valid_versions';
import {SCENARIO_HOST_PERMISSIONS_WIFI_STA} from './13_permissions_wifi_sta_only';
import {SCENARIO_HOST_X_PROD} from './host_x_prod';
import {SCENARIO_HOST_Z_PROD} from './host_z_prod';
import {
  SCENARIO_RC_ALL_VALID,
  SCENARIO_RC_MIXED_ALL,
  SCENARIO_RC_PROXY_MISMATCH,
} from './multi_remote_control';
import {OVERVIEW_01} from './overview_01';
import {OVERVIEW_02} from './overview_02';
import {OVERVIEW_03} from './overview_03';
import {OVERVIEW_04} from './overview_04';
import {OVERVIEW_05} from './overview_05';
import {OVERVIEW_06} from './overview_06';
import {OVERVIEW_07} from './overview_07';
import {OVERVIEW_08} from './overview_08';
import {OVERVIEW_09} from './overview_09';
import {OVERVIEW_10} from './overview_10';
import {OVERVIEW_11} from './overview_11';
import {OVERVIEW_12} from './overview_12';
import {OVERVIEW_13} from './overview_13';
import {OVERVIEW_14} from './overview_14';
import {overviewRefreshFactory} from './overview_refresh';
import {SCENARIO_RC_PERMISSIONS_ALL} from './remote_control_permissions';

function wrapHost(
  factory: (callCount?: number) => MockHostScenario,
): MockHostScenarioWrapper {
  const peek = factory(0);
  return {
    hostName: peek.hostName,
    scenarioName: peek.scenarioName,
    factory,
  };
}

function createClonedHostScenario(opts: {
  hostName: string;
  scenarioName: string;
  ip: string;
  devices: Array<{
    id: string;
    model: string;
    type?: string;
    status?: string;
    label?: string;
  }>;
}): () => MockHostScenario {
  return () => {
    const base = OVERVIEW_01();
    const baseSummary = base.deviceSummaries![0];
    return {
      ...base,
      hostName: opts.hostName,
      scenarioName: opts.scenarioName,
      overview: base.overview
        ? {
            ...base.overview,
            hostName: opts.hostName,
            ip: opts.ip,
          }
        : undefined,
      deviceSummaries: opts.devices.map((d) => ({
        ...baseSummary,
        id: d.id,
        model: d.model,
        label: d.label ?? 'golden-pool',
        deviceStatus: {
          ...baseSummary.deviceStatus,
          status: d.status ?? 'IDLE',
        },
        types: [
          {
            type: d.type ?? 'AndroidRealDevice',
            isAbnormal: false,
          },
        ],
      })),
    };
  };
}

/** Central registry of all mock host scenarios. */
export const MOCK_HOST_SCENARIOS: MockHostScenarioWrapper[] = [
  wrapHost(scenarioHostLocalMtt),
  wrapHost(overviewRefreshFactory),
  wrapHost(SCENARIO_HOST_NO_CONFIG),
  wrapHost(SCENARIO_HOST_BASIC_EDITABLE),
  wrapHost(SCENARIO_HOST_SHARED_MODE),
  wrapHost(SCENARIO_HOST_PUSHER_PROPERTIES),
  wrapHost(SCENARIO_HOST_PUSHER_ITEM_OVERRIDE),
  wrapHost(SCENARIO_HOST_PUSHER_ALL),
  wrapHost(SCENARIO_HOST_SSH_HIDDEN),
  wrapHost(SCENARIO_HOST_DISCOVERY_HIDDEN),
  wrapHost(SCENARIO_HOST_DEVICE_CONFIG_HIDDEN),
  wrapHost(SCENARIO_HOST_DEVICE_CONFIG_WIFI_DIMENSIONS_ONLY),
  wrapHost(SCENARIO_HOST_X_PROD),
  wrapHost(SCENARIO_HOST_Z_PROD),
  wrapHost(SCENARIO_HOST_COMING_SOON),
  wrapHost(SCENARIO_HOST_NO_VALID_VERSIONS),
  wrapHost(SCENARIO_HOST_PERMISSIONS_WIFI_STA),
  wrapHost(SCENARIO_RC_ALL_VALID),
  wrapHost(SCENARIO_RC_MIXED_ALL),
  wrapHost(SCENARIO_RC_PROXY_MISMATCH),
  wrapHost(SCENARIO_RC_PERMISSIONS_ALL),
  wrapHost(OVERVIEW_01),
  wrapHost(OVERVIEW_02),
  wrapHost(OVERVIEW_03),
  wrapHost(OVERVIEW_04),
  wrapHost(OVERVIEW_05),
  wrapHost(OVERVIEW_06),
  wrapHost(OVERVIEW_07),
  wrapHost(OVERVIEW_08),
  wrapHost(OVERVIEW_09),
  wrapHost(OVERVIEW_10),
  wrapHost(OVERVIEW_11),
  wrapHost(OVERVIEW_12),
  wrapHost(OVERVIEW_13),
  wrapHost(OVERVIEW_14),
  wrapHost(
    createClonedHostScenario({
      hostName: 'host-for-1-hit-1.example.com',
      scenarioName: 'All-Site 1-Hit Host #1: Core Lab',
      ip: '172.16.42.18',
      devices: [
        {
          id: 'device-id-for-1-hit-1',
          model: 'Pixel 9 Pro',
          type: 'AndroidRealDevice',
        },
        {
          id: 'device-id-for-1-hit-2',
          model: 'Pixel 8 Pro',
          type: 'AndroidRealDevice',
          status: 'BUSY',
        },
        {id: '18261FDF6003KC', model: 'Pixel 8 Pro', type: 'AndroidRealDevice'},
        {id: '98888FDF6005AB', model: 'Pixel 7a', type: 'AndroidRealDevice'},
      ],
    }),
  ),
  wrapHost(
    createClonedHostScenario({
      hostName: 'host-for-1-hit-2.example.com',
      scenarioName: 'All-Site 1-Hit Host #2: Satellite Lab',
      ip: '172.16.42.19',
      devices: [
        {
          id: 'device-id-for-1-hit-1',
          model: 'Pixel 9 Pro',
          type: 'AndroidRealDevice',
        },
      ],
    }),
  ),
  wrapHost(
    createClonedHostScenario({
      hostName: 'host-name-for-ambiguous-1.example.com',
      scenarioName: 'All-Site Ambiguous Host: Device & Host Collision',
      ip: '172.16.42.18',
      devices: [
        {
          id: 'host-name-for-ambiguous-1',
          model: 'LinuxTestbedDevice',
          type: 'LinuxDevice',
        },
        {
          id: 'device-id-for-1-hit-1',
          model: 'Pixel 9 Pro',
          type: 'AndroidRealDevice',
        },
      ],
    }),
  ),
  wrapHost(
    createClonedHostScenario({
      hostName: 'host-for-ambiguous-device-1.example.com',
      scenarioName: 'All-Site Multi-Host #1: Cuttlefish Lab 1',
      ip: '172.16.14.10',
      devices: [
        {
          id: 'device-id-for-ambiguous-1',
          model: 'Android Cuttlefish',
          type: 'AndroidRealDevice',
        },
      ],
    }),
  ),
  wrapHost(
    createClonedHostScenario({
      hostName: 'host-for-ambiguous-device-2.example.com',
      scenarioName: 'All-Site Multi-Host #2: Cuttlefish Lab 2',
      ip: '172.20.88.12',
      devices: [
        {
          id: 'device-id-for-ambiguous-1',
          model: 'Android Cuttlefish',
          type: 'AndroidRealDevice',
        },
      ],
    }),
  ),
  wrapHost(
    createClonedHostScenario({
      hostName: 'host-for-ambiguous-device-3.example.com',
      scenarioName: 'All-Site Multi-Host #3: Cuttlefish Lab 3',
      ip: '172.24.21.15',
      devices: [
        {
          id: 'device-id-for-ambiguous-1',
          model: 'Android Cuttlefish',
          type: 'AndroidRealDevice',
        },
      ],
    }),
  ),
  wrapHost(
    createClonedHostScenario({
      hostName: 'mt31-dm01-a-x04.moma.example.com',
      scenarioName: 'Test/Job Referenced Host: mt31-dm01-a-x04',
      ip: '100.107.201.12',
      devices: [
        {id: '43021FDAQ000UM', model: 'Pixel 7 Pro', type: 'AndroidRealDevice'},
        {id: '99061FFAZ004AA', model: 'Pixel 8 Pro', type: 'AndroidRealDevice'},
        {
          id: '4D5A1FDAB001BB',
          model: 'Pixel 8',
          type: 'AndroidRealDevice',
          status: 'BUSY',
        },
      ],
    }),
  ),
];

export {scenarioHostLocalMtt} from './local_mtt_host';
