import {MockDeviceScenario, MockDeviceScenarioWrapper} from '../models';
import {SCENARIOS_LOCAL_MTT_DEVICES} from './local_mtt_devices';
import {SCENARIO_IN_SERVICE_IDLE} from './01_in_service_idle';
import {SCENARIO_IN_SERVICE_BUSY} from './02_in_service_busy';
import {SCENARIO_OUT_OF_SERVICE_INIT} from './03_out_of_service_init';
import {SCENARIO_OUT_OF_SERVICE_RECOVERING} from './04_out_of_service_recovering';
import {SCENARIO_OUT_OF_SERVICE_DIRTY} from './05_out_of_service_dirty';
import {SCENARIO_OUT_OF_SERVICE_MISSING} from './06_out_of_service_missing';
import {SCENARIO_OUT_OF_SERVICE_FAILED} from './07_out_of_service_failed';
import {SCENARIO_OUT_OF_SERVICE_ABNORMAL_TYPE} from './08_out_of_service_abnormal_type';
import {SCENARIO_OUT_OF_SERVICE_NO_TYPE} from './09_out_of_service_no_type';
import {SCENARIO_UI_TEST_LONG_ID} from './10_ui_test_long_id';
import {SCENARIO_OUT_OF_SERVICE_UNKNOWN_TIME} from './11_out_of_service_unknown_time';
import {SCENARIO_HOST_MANAGED_DEVICE} from './12_host_managed_device';
import {SCENARIO_EMPTY_CONFIG} from './13_empty_config';
import {SCENARIO_EMPTY_CONFIG_WITH_HOST} from './14_empty_config_with_host';
import {SCENARIO_IDLE_BUT_QUARANTINED} from './15_idle_but_quarantined';
import {SCENARIO_LINUX_DEVICE} from './16_linux_device';
import {SCENARIO_ANDROID_MISSING} from './17_android_missing';
import {SCENARIO_ANDROID_BUSY_NO_FLASH} from './18_android_busy_no_flash';
import {SCENARIO_ANDROID_NO_SCREENSHOT} from './19_android_no_screenshot';
import {SCENARIO_TESTBED_DEVICE} from './20_testbed_device';
import {SCENARIO_TESTBED_EVEN_SUBDEVICES} from './21_testbed_even_subdevices';
import {SCENARIO_TEST_RESULTS} from './22_test_results';
import {SCENARIO_WIFI_DIMENSIONS_ONLY} from './23_wifi_dimensions_only';
import {SCENARIO_TESTBED_SINGLE_ELIGIBLE} from './24_testbed_single_eligible';
import {SCENARIO_TESTBED_MIXED_ELIGIBILITY} from './25_testbed_mixed_eligibility';
import {SCENARIO_COMING_SOON} from './26_coming_soon';
import {
  scenarioErrorLogical,
  scenarioErrorPermissionDenied,
  scenarioErrorRpc,
} from './27_error_scenarios';
import {deviceRefreshFactory} from './27_refresh_scenario';
import {scenarioFusionDevice} from './28_fusion_device';

function wrapDevice(
  factory: (callCount?: number) => MockDeviceScenario,
): MockDeviceScenarioWrapper {
  const peek = factory(0);
  return {
    id: peek.id,
    scenarioName: peek.scenarioName,
    factory,
  };
}

function createClonedDeviceScenario(
  baseFactory: () => MockDeviceScenario,
  opts: {
    id: string;
    scenarioName: string;
    model: string;
    hostName: string;
    hostIp: string;
  },
): () => MockDeviceScenario {
  return () => {
    const base = baseFactory();
    return {
      ...base,
      id: opts.id,
      scenarioName: opts.scenarioName,
      overview: {
        ...base.overview,
        id: opts.id,
        host: {
          name: opts.hostName,
          ip: opts.hostIp,
        },
        basicInfo: {
          ...base.overview.basicInfo,
          model: opts.model,
        },
      },
    };
  };
}

/** List of mock device scenarios. */
export const MOCK_DEVICE_SCENARIOS: MockDeviceScenarioWrapper[] = [
  ...SCENARIOS_LOCAL_MTT_DEVICES.map(wrapDevice),
  wrapDevice(deviceRefreshFactory),
  wrapDevice(SCENARIO_IN_SERVICE_IDLE),
  wrapDevice(SCENARIO_IN_SERVICE_BUSY),
  wrapDevice(SCENARIO_OUT_OF_SERVICE_INIT),
  wrapDevice(SCENARIO_OUT_OF_SERVICE_RECOVERING),
  wrapDevice(SCENARIO_OUT_OF_SERVICE_DIRTY),
  wrapDevice(SCENARIO_OUT_OF_SERVICE_MISSING),
  wrapDevice(SCENARIO_OUT_OF_SERVICE_FAILED),
  wrapDevice(SCENARIO_OUT_OF_SERVICE_ABNORMAL_TYPE),
  wrapDevice(SCENARIO_OUT_OF_SERVICE_NO_TYPE),
  wrapDevice(SCENARIO_UI_TEST_LONG_ID),
  wrapDevice(SCENARIO_OUT_OF_SERVICE_UNKNOWN_TIME),
  wrapDevice(SCENARIO_HOST_MANAGED_DEVICE),
  wrapDevice(SCENARIO_EMPTY_CONFIG),
  wrapDevice(SCENARIO_EMPTY_CONFIG_WITH_HOST),
  wrapDevice(SCENARIO_IDLE_BUT_QUARANTINED),
  wrapDevice(SCENARIO_LINUX_DEVICE),
  wrapDevice(SCENARIO_ANDROID_MISSING),
  wrapDevice(SCENARIO_ANDROID_BUSY_NO_FLASH),
  wrapDevice(SCENARIO_ANDROID_NO_SCREENSHOT),
  wrapDevice(SCENARIO_TESTBED_DEVICE),
  wrapDevice(SCENARIO_TESTBED_EVEN_SUBDEVICES),
  wrapDevice(SCENARIO_TEST_RESULTS),
  wrapDevice(SCENARIO_WIFI_DIMENSIONS_ONLY),
  wrapDevice(SCENARIO_TESTBED_SINGLE_ELIGIBLE),
  wrapDevice(SCENARIO_TESTBED_MIXED_ELIGIBILITY),
  wrapDevice(SCENARIO_COMING_SOON),
  wrapDevice(scenarioFusionDevice),
  wrapDevice(scenarioErrorPermissionDenied),
  wrapDevice(scenarioErrorLogical),
  wrapDevice(scenarioErrorRpc),
  wrapDevice(
    createClonedDeviceScenario(SCENARIO_IN_SERVICE_IDLE, {
      id: 'device-id-for-1-hit-1',
      scenarioName: 'All-Site 1-Hit #1: Pixel 9 Pro (IDLE)',
      model: 'Pixel 9 Pro',
      hostName: 'host-for-1-hit-1.example.com',
      hostIp: '172.16.42.18',
    }),
  ),
  wrapDevice(
    createClonedDeviceScenario(SCENARIO_IN_SERVICE_BUSY, {
      id: 'device-id-for-1-hit-2',
      scenarioName: 'All-Site 1-Hit #2: Pixel 8 Pro (BUSY)',
      model: 'Pixel 8 Pro',
      hostName: 'host-for-1-hit-1.example.com',
      hostIp: '172.16.42.18',
    }),
  ),
  wrapDevice(
    createClonedDeviceScenario(SCENARIO_IN_SERVICE_IDLE, {
      id: 'device-id-for-ambiguous-1',
      scenarioName: 'All-Site Ambiguous: Android Cuttlefish (Multi-Host)',
      model: 'Android Cuttlefish',
      hostName: 'host-for-ambiguous-device-1.example.com',
      hostIp: '172.16.14.10',
    }),
  ),
  wrapDevice(
    createClonedDeviceScenario(SCENARIO_LINUX_DEVICE, {
      id: 'host-name-for-ambiguous-1',
      scenarioName: 'All-Site Ambiguous: LinuxTestbedDevice (Device & Host)',
      model: 'LinuxTestbedDevice',
      hostName: 'host-name-for-ambiguous-1.example.com',
      hostIp: '172.16.42.18',
    }),
  ),
  wrapDevice(
    createClonedDeviceScenario(SCENARIO_IN_SERVICE_IDLE, {
      id: '99061FFAZ004AA',
      scenarioName: 'Passed Test Device (IDLE)',
      model: 'Pixel 8 Pro',
      hostName: 'mt31-dm01-a-x04.moma.example.com',
      hostIp: '100.107.201.12',
    }),
  ),
  wrapDevice(
    createClonedDeviceScenario(SCENARIO_IN_SERVICE_BUSY, {
      id: '4D5A1FDAB001BB',
      scenarioName: 'In-Progress Job Device (BUSY)',
      model: 'Pixel 8',
      hostName: 'mt31-dm01-a-x04.moma.example.com',
      hostIp: '100.107.201.12',
    }),
  ),
  wrapDevice(
    createClonedDeviceScenario(SCENARIO_IN_SERVICE_IDLE, {
      id: '18261FDF6003KC',
      scenarioName: 'TJS Search Device 1 (IDLE)',
      model: 'Pixel 8 Pro',
      hostName: 'host-for-1-hit-1.example.com',
      hostIp: '172.16.42.18',
    }),
  ),
  wrapDevice(
    createClonedDeviceScenario(SCENARIO_IN_SERVICE_IDLE, {
      id: '98888FDF6005AB',
      scenarioName: 'TJS Search Device 2 (IDLE)',
      model: 'Pixel 7a',
      hostName: 'host-for-1-hit-1.example.com',
      hostIp: '172.16.42.18',
    }),
  ),
];

export {SCENARIOS_LOCAL_MTT_DEVICES} from './local_mtt_devices';
