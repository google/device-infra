import {MockDeviceScenario} from '../models';
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
import {SCENARIO_DEVICE_REFRESH} from './27_refresh_scenario';

/** Central registry of all mock device scenarios. */
export const MOCK_DEVICE_SCENARIOS: MockDeviceScenario[] = [
  SCENARIO_DEVICE_REFRESH,
  SCENARIO_IN_SERVICE_IDLE,
  SCENARIO_IN_SERVICE_BUSY,
  SCENARIO_OUT_OF_SERVICE_INIT,
  SCENARIO_OUT_OF_SERVICE_RECOVERING,
  SCENARIO_OUT_OF_SERVICE_DIRTY,
  SCENARIO_OUT_OF_SERVICE_MISSING,
  SCENARIO_OUT_OF_SERVICE_FAILED,
  SCENARIO_OUT_OF_SERVICE_ABNORMAL_TYPE,
  SCENARIO_OUT_OF_SERVICE_NO_TYPE,
  SCENARIO_UI_TEST_LONG_ID,
  SCENARIO_OUT_OF_SERVICE_UNKNOWN_TIME,
  SCENARIO_HOST_MANAGED_DEVICE,
  SCENARIO_EMPTY_CONFIG,
  SCENARIO_EMPTY_CONFIG_WITH_HOST,
  SCENARIO_IDLE_BUT_QUARANTINED,
  SCENARIO_LINUX_DEVICE,
  SCENARIO_ANDROID_MISSING,
  SCENARIO_ANDROID_BUSY_NO_FLASH,
  SCENARIO_ANDROID_NO_SCREENSHOT,
  SCENARIO_TESTBED_DEVICE,
  SCENARIO_TESTBED_EVEN_SUBDEVICES,
  SCENARIO_TEST_RESULTS,
  SCENARIO_WIFI_DIMENSIONS_ONLY,
  SCENARIO_TESTBED_SINGLE_ELIGIBLE,
  SCENARIO_TESTBED_MIXED_ELIGIBILITY,
  SCENARIO_COMING_SOON,
];
