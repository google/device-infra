/**
 * @fileoverview Main registry for all mock data scenarios.
 * This file defines the interfaces for mock scenarios and serves as the single
 * source of truth for importing and exporting mock data arrays used by fake
 * services throughout the application.
 */

import {MockDeviceScenario, MockHostScenario} from './models';

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
];
