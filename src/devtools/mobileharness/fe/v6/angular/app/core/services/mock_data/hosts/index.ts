import {MockHostScenario} from '../models';
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
import {OVERVIEW_REFRESH} from './overview_refresh';
import {SCENARIO_RC_PERMISSIONS_ALL} from './remote_control_permissions';

/** Central registry of all mock host scenarios. */
export const MOCK_HOST_SCENARIOS: MockHostScenario[] = [
  OVERVIEW_REFRESH,
  SCENARIO_HOST_NO_CONFIG,
  SCENARIO_HOST_BASIC_EDITABLE,
  SCENARIO_HOST_SHARED_MODE,
  SCENARIO_HOST_PUSHER_PROPERTIES,
  SCENARIO_HOST_PUSHER_ITEM_OVERRIDE,
  SCENARIO_HOST_PUSHER_ALL,
  SCENARIO_HOST_SSH_HIDDEN,
  SCENARIO_HOST_DISCOVERY_HIDDEN,
  SCENARIO_HOST_DEVICE_CONFIG_HIDDEN,
  SCENARIO_HOST_DEVICE_CONFIG_WIFI_DIMENSIONS_ONLY,
  SCENARIO_HOST_X_PROD,
  SCENARIO_HOST_Z_PROD,
  SCENARIO_HOST_COMING_SOON,
  SCENARIO_HOST_NO_VALID_VERSIONS,
  SCENARIO_HOST_PERMISSIONS_WIFI_STA,
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
  OVERVIEW_14,
];
