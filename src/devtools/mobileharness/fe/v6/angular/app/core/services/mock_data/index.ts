/**
 * @fileoverview Main registry for all mock data scenarios.
 * This file defines the interfaces for mock scenarios and serves as the single
 * source of truth for importing and exporting mock data arrays used by fake
 * services throughout the application.
 */

import {SCENARIO_IN_SERVICE_IDLE} from './devices/01_in_service_idle';
import {SCENARIO_IN_SERVICE_BUSY} from './devices/02_in_service_busy';
import {SCENARIO_OUT_OF_SERVICE_INIT} from './devices/03_out_of_service_init';
import {SCENARIO_OUT_OF_SERVICE_RECOVERING} from './devices/04_out_of_service_recovering';
import {SCENARIO_OUT_OF_SERVICE_DIRTY} from './devices/05_out_of_service_dirty';
import {SCENARIO_OUT_OF_SERVICE_MISSING} from './devices/06_out_of_service_missing';
import {SCENARIO_OUT_OF_SERVICE_FAILED} from './devices/07_out_of_service_failed';
import {SCENARIO_OUT_OF_SERVICE_ABNORMAL_TYPE} from './devices/08_out_of_service_abnormal_type';
import {SCENARIO_OUT_OF_SERVICE_NO_TYPE} from './devices/09_out_of_service_no_type';
import {SCENARIO_UI_TEST_LONG_ID} from './devices/10_ui_test_long_id';
import {SCENARIO_OUT_OF_SERVICE_UNKNOWN_TIME} from './devices/11_out_of_service_unknown_time';
import {SCENARIO_HOST_MANAGED_DEVICE} from './devices/12_host_managed_device';
import {SCENARIO_EMPTY_CONFIG} from './devices/13_empty_config';
import {SCENARIO_EMPTY_CONFIG_WITH_HOST} from './devices/14_empty_config_with_host';
import {SCENARIO_IDLE_BUT_QUARANTINED} from './devices/15_idle_but_quarantined';
import {SCENARIO_LINUX_DEVICE} from './devices/16_linux_device';
import {SCENARIO_ANDROID_MISSING} from './devices/17_android_missing';
import {SCENARIO_ANDROID_BUSY_NO_FLASH} from './devices/18_android_busy_no_flash';
import {SCENARIO_ANDROID_NO_SCREENSHOT} from './devices/19_android_no_screenshot';
import {SCENARIO_TESTBED_DEVICE} from './devices/20_testbed_device';
import {SCENARIO_TESTBED_EVEN_SUBDEVICES} from './devices/21_testbed_even_subdevices';
import {SCENARIO_TEST_RESULTS} from './devices/22_test_results';
import {SCENARIO_HOST_NO_CONFIG} from './hosts/01_no_config';
import {SCENARIO_HOST_BASIC_EDITABLE} from './hosts/02_basic_editable';
import {SCENARIO_HOST_SHARED_MODE} from './hosts/03_shared_mode';
import {SCENARIO_HOST_PUSHER_PROPERTIES} from './hosts/04_pusher_properties_only';
import {SCENARIO_HOST_PUSHER_ITEM_OVERRIDE} from './hosts/05_pusher_properties_item_override';
import {SCENARIO_HOST_PUSHER_ALL} from './hosts/06_pusher_all';
import {SCENARIO_HOST_SSH_HIDDEN} from './hosts/07_ssh_access_hidden';
import {SCENARIO_HOST_DISCOVERY_HIDDEN} from './hosts/08_device_discovery_hidden';
import {SCENARIO_HOST_DEVICE_CONFIG_HIDDEN} from './hosts/09_device_config_hidden';
import {SCENARIO_HOST_X_PROD} from './hosts/host_x_prod';
import {SCENARIO_HOST_Z_PROD} from './hosts/host_z_prod';
import {
  SCENARIO_RC_ALL_VALID,
  SCENARIO_RC_MIXED_ALL,
  SCENARIO_RC_PROXY_MISMATCH,
} from './hosts/multi_remote_control';
import {OVERVIEW_01} from './hosts/overview_01';
import {OVERVIEW_02} from './hosts/overview_02';
import {OVERVIEW_03} from './hosts/overview_03';
import {OVERVIEW_04} from './hosts/overview_04';
import {OVERVIEW_05} from './hosts/overview_05';
import {OVERVIEW_06} from './hosts/overview_06';
import {OVERVIEW_07} from './hosts/overview_07';
import {OVERVIEW_08} from './hosts/overview_08';
import {OVERVIEW_09} from './hosts/overview_09';
import {OVERVIEW_10} from './hosts/overview_10';
import {OVERVIEW_11} from './hosts/overview_11';
import {OVERVIEW_12} from './hosts/overview_12';
import {OVERVIEW_13} from './hosts/overview_13';
import {SCENARIO_RC_PERMISSIONS_ALL} from './hosts/remote_control_permissions';
import {MockDeviceScenario, MockHostScenario} from './models';

/**
 * A comprehensive collection of mock device scenarios, each representing a
 * unique state or configuration of a device.
 */
export const MOCK_DEVICE_SCENARIOS: MockDeviceScenario[] = [
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
];

/**
 * A collection of mock host scenarios, each representing a unique state or
 * configuration of a host.
 */
export const MOCK_HOST_SCENARIOS: MockHostScenario[] = [
  SCENARIO_HOST_NO_CONFIG,
  SCENARIO_HOST_BASIC_EDITABLE,
  SCENARIO_HOST_SHARED_MODE,
  SCENARIO_HOST_PUSHER_PROPERTIES,
  SCENARIO_HOST_PUSHER_ITEM_OVERRIDE,
  SCENARIO_HOST_PUSHER_ALL,
  SCENARIO_HOST_SSH_HIDDEN,
  SCENARIO_HOST_DISCOVERY_HIDDEN,
  SCENARIO_HOST_DEVICE_CONFIG_HIDDEN,
  SCENARIO_HOST_X_PROD,
  SCENARIO_HOST_Z_PROD,
  SCENARIO_RC_ALL_VALID,
  SCENARIO_RC_MIXED_ALL,
  SCENARIO_RC_PROXY_MISMATCH,
  SCENARIO_RC_PERMISSIONS_ALL,
  OVERVIEW_01,
  OVERVIEW_02,
  OVERVIEW_03,
  OVERVIEW_04,
  OVERVIEW_05,
  OVERVIEW_06,
  OVERVIEW_07,
  OVERVIEW_08,
  OVERVIEW_09,
  OVERVIEW_10,
  OVERVIEW_11,
  OVERVIEW_12,
  OVERVIEW_13,
];
