import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';
import {SCENARIO_IN_SERVICE_IDLE} from './01_in_service_idle';

/** Factory for dynamic device refresh scenario. */
export function deviceRefreshFactory(callCount = 0): MockDeviceScenario {
  // Simulate failure on the 5th call
  if (callCount % 5 === 0 && callCount > 0) {
    throw new Error(
      `Simulated failure on 5th refresh call for device: refresh-device-id.`,
    );
  }

  const isEven = callCount % 2 === 0;

  // Deep clone overview to avoid mutating global mock data
  const baseOverview = SCENARIO_IN_SERVICE_IDLE().overview;
  const overview = structuredClone(baseOverview) as DeviceOverview;

  // 🎭 Simulate dynamic changes on refresh across all sections to verify UI reactivity.

  // 1. Health & Activity Section
  overview.healthAndActivity.state = isEven
    ? 'IN_SERVICE_IDLE'
    : 'OUT_OF_SERVICE_NEEDS_FIXING';
  overview.healthAndActivity.title = isEven
    ? 'In Service (Idle)'
    : 'Out of Service (Needs Fixing)';
  overview.healthAndActivity.subtitle = isEven
    ? 'The device is healthy and ready for new tasks.'
    : 'The device is experiencing issues and needs attention.';

  if (overview.healthAndActivity.deviceStatus) {
    overview.healthAndActivity.deviceStatus.status = isEven
      ? 'IDLE'
      : 'MISSING';
    overview.healthAndActivity.deviceStatus.isCritical = !isEven;
  }

  // 2. Basic Information Section
  overview.basicInfo.batteryLevel = isEven ? 95 : 42;
  overview.basicInfo.version = isEven ? '14 (Stable)' : '14 (Beta)';

  if (overview.basicInfo.network) {
    overview.basicInfo.network.wifiRssi = isEven ? -50 : -85;
    overview.basicInfo.network.hasInternet = isEven;
  }

  // 3. Properties Section
  overview.properties = {
    ...baseOverview.properties,
    'Refresh Count': String(callCount),
    'Last Refreshed At': new Date().toLocaleTimeString(),
    'Simulated State': isEven ? 'Even Refresh' : 'Odd Refresh',
  };

  // 4. Capabilities Section
  if (overview.capabilities) {
    overview.capabilities.supportedDrivers = isEven
      ? [
          ...(baseOverview.capabilities?.supportedDrivers || []),
          'DynamicFakeDriver',
        ]
      : baseOverview.capabilities?.supportedDrivers;
  }

  // 5. Dimensions Section
  if (overview.dimensions && overview.dimensions.supported) {
    // Inject dynamic dimension into the first available source group
    const sources = Object.keys(overview.dimensions.supported);
    if (sources.length > 0) {
      const source = sources[0];
      overview.dimensions.supported[source] = {
        ...overview.dimensions.supported[source],
        dimensions: [
          ...(baseOverview.dimensions?.supported?.[source]?.dimensions || []),
          {
            name: 'Dynamic Simulation Source',
            value: isEven ? 'Toggle A' : 'Toggle B',
          },
        ],
      };
    }
  }

  // 6. Sub-Devices Section (if applicable)
  if (overview.subDevices && overview.subDevices.length > 0) {
    overview.subDevices[0].batteryLevel = isEven ? 80 : 20;
    if (overview.subDevices[0].network) {
      overview.subDevices[0].network.wifiRssi = isEven ? -55 : -90;
    }
  }

  // 7. Permissions Section
  if (overview.permissions) {
    overview.permissions.owners = isEven
      ? [...(baseOverview.permissions?.owners || []), 'DynamicFakeOwner']
      : baseOverview.permissions?.owners;
    overview.permissions.executors = isEven
      ? [...(baseOverview.permissions?.executors || []), 'DynamicFakeExecutor']
      : baseOverview.permissions?.executors;
  }

  return {
    ...SCENARIO_IN_SERVICE_IDLE(),
    scenarioName: 'Device Refresh Test Scenario',
    id: 'refresh-device-id',
    overview,
  };
}
