import {MockDeviceScenario, MockDeviceScenarioWrapper} from '../models';
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
import {deviceRefreshFactory} from './27_refresh_scenario';

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

/** List of mock device scenarios. */
export const MOCK_DEVICE_SCENARIOS: MockDeviceScenarioWrapper[] = [
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
];
