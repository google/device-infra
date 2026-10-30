import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {delay} from 'rxjs/operators';
import {
  BatchGetConfigurableDimensionKeysRequest,
  BatchGetConfigurableDimensionKeysResponse,
  BatchGetDeviceDimensionConfigsRequest,
  BatchGetDeviceDimensionConfigsResponse,
  BatchGetDeviceWifiConfigsRequest,
  BatchGetDeviceWifiConfigsResponse,
  BatchUpdateDeviceDimensionConfigsRequest,
  BatchUpdateDeviceDimensionConfigsResponse,
  BatchUpdateDeviceWifiConfigsRequest,
  BatchUpdateDeviceWifiConfigsResponse,
  CandidateDimensionKey,
  CandidateDimensionValue,
  CandidateWifi,
  CheckDeviceConfigPermissionResult,
  ConfigSection,
  ConfigurableDimension,
  DeviceConfig,
  DeviceConfigChangeStatus,
  DeviceConfigUiStatus,
  DeviceDimensionConfigEntry,
  DeviceTarget,
  DeviceWifi,
  DeviceWifiConfigEntry,
  DimensionList,
  GetConfigurableDimensionsRequest,
  GetConfigurableDimensionsResponse,
  GetDeviceConfigResult,
  GetDimensionValueSuggestionsRequest,
  GetDimensionValueSuggestionsResponse,
  GetWifiSuggestionsRequest,
  GetWifiSuggestionsResponse,
  RecommendedWifi,
  TargetDeviceWifi,
  TargetDimensionValues,
  UpdateDeviceConfigRequest,
  UpdateDeviceConfigResult,
  UpdateError,
  WifiConfig,
} from '../../models/device_config_models';
import {
  CheckHostConfigPermissionResult,
  GetHostConfigResult,
  UnlockHostPropertiesResponse,
  UpdateHostConfigRequest,
  UpdateHostConfigResult,
} from '../../models/host_config_models';
import {normalizeHostConfig} from '../../utils/host_config_utils';
import {
  MOCK_DEVICE_SCENARIOS,
  MOCK_HOST_SCENARIOS,
  MOCK_RECOMMENDED_WIFIS,
} from '../mock_data';
import {MockDeviceScenario, MockHostScenario} from '../mock_data/models';
import {ConfigService} from './config_service';

const CURRENT_USER = 'derekchen';

const DEFAULT_DEVICE_CONFIG_UI_STATUS: DeviceConfigUiStatus = {
  permissions: {visible: true, editability: {editable: true}},
  wifi: {visible: true, editability: {editable: true}},
  dimensions: {visible: true, editability: {editable: true}},
  settings: {visible: true, editability: {editable: true}},
};

// Helper function to deep copy an object
function deepCopy<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj)) as T;
}

/**
 * Helper that checks if a target configuration path is inside the update mask.
 * Matches both exact paths, sub-paths of the path, or parent paths.
 * Mirrors the logic in UpdateHostConfigHandler.java.
 */
function isPathInFieldMask(path: string, maskPaths: string[]): boolean {
  for (const maskPath of maskPaths) {
    if (
      maskPath === path ||
      maskPath.startsWith(path + '.') ||
      path.startsWith(maskPath + '.')
    ) {
      return true;
    }
  }
  return false;
}

/**
 * Generates a specific error message for invalid user ID scenarios in testing.
 * Mirrors the backend logic in UpdateHostConfigHandler to provide realistic test cases.
 */
function getInvalidUserErrorMessage(hostName: string, paths: string[]): string {
  let sections = '';
  // If no paths provided, it implies a full update.
  const effectivePaths =
    paths.length === 0 ? ['permissions', 'device_config'] : paths;

  const updatingHost = isPathInFieldMask('permissions', effectivePaths);
  const updatingDevice = isPathInFieldMask(
    'device_config.permissions',
    effectivePaths,
  );

  if (updatingHost && updatingDevice) {
    sections = 'Host Permissions or Device Executors';
  } else if (updatingHost) {
    sections = 'Host Permissions';
  } else if (updatingDevice) {
    sections = 'Device Executors';
  }

  if (!sections) {
    return `Failed to update config of host ${hostName}, please make sure the provided user IDs are correct, or try again later.`;
  }

  return `Failed to update config of host ${hostName}, please make sure the provided user IDs in ${sections} are correct, or try again later.`;
}

/**
 * A fake implementation of the ConfigService for development and testing.
 * It uses the mock data defined in the central mock_data registry and simulates
 * backend logic like permission checks and config updates.
 */
@Injectable({
  providedIn: 'root',
})
export class FakeConfigService extends ConfigService {
  private readonly mockDeviceScenarios: MockDeviceScenario[];
  private readonly mockHostScenarios: MockHostScenario[];

  constructor() {
    super();
    this.mockDeviceScenarios = MOCK_DEVICE_SCENARIOS.map((w) =>
      deepCopy(w.factory()),
    );
    this.mockHostScenarios = MOCK_HOST_SCENARIOS.map((w) =>
      deepCopy(w.factory()),
    );
  }

  // ===== Device Config Methods =====

  /**
   * Retrieves the configuration for a specific device from fake data.
   *
   * @param deviceId The unique identifier of the device.
   * @param hostName The host name to simulate host-managed config.
   * @return An Observable emitting the fake device configuration result.
   */
  override getDeviceConfig(
    deviceId: string,
    hostName: string, // hostName is required to disambiguate devices.
  ): Observable<GetDeviceConfigResult> {
    const scenario = this.mockDeviceScenarios.find((s) => s.id === deviceId);
    if (!scenario) {
      return throwError(
        () => new Error(`Device with ID '${deviceId}' not found in mock data.`),
      ).pipe(delay(1000));
    }

    return of({
      deviceConfig: deepCopy(scenario.config),
      isHostManaged: Boolean(scenario.isHostManaged),
      hostName: scenario.overview.host.name,
      uiStatus: deepCopy(
        scenario.configUiStatus ?? DEFAULT_DEVICE_CONFIG_UI_STATUS,
      ),
    }).pipe(delay(1000));
  }

  override checkDeviceConfigPermission(
    deviceId: string,
    universe?: string,
  ): Observable<CheckDeviceConfigPermissionResult> {
    const scenario = this.mockDeviceScenarios.find((s) => s.id === deviceId);

    if (!scenario || !scenario.config) {
      // If no config exists, permission is granted to create one.
      return of({hasPermission: true, userName: CURRENT_USER}).pipe(
        delay(1000),
      );
    }

    const hasPermission = (scenario.config.permissions?.owners || []).includes(
      CURRENT_USER,
    );
    return of({hasPermission, userName: CURRENT_USER}).pipe(delay(1000));
  }

  override updateDeviceConfig(
    request: UpdateDeviceConfigRequest,
  ): Observable<UpdateDeviceConfigResult> {
    const scenarioIndex = this.mockDeviceScenarios.findIndex(
      (s) => s.id === request.id,
    );
    if (scenarioIndex === -1) {
      return of<UpdateDeviceConfigResult>({
        success: false,
        error: {code: 'UNKNOWN', message: 'Device not found'},
      }).pipe(delay(1000));
    }

    const scenario = this.mockDeviceScenarios[scenarioIndex];
    if (scenario.configUpdateError) {
      return of<UpdateDeviceConfigResult>({
        success: false,
        error: deepCopy(scenario.configUpdateError),
      }).pipe(delay(1000));
    }

    let currentConfig = scenario.config;
    if (!currentConfig) {
      // If config is null, create a new one
      currentConfig = deepCopy(request.config);
    } else {
      currentConfig = deepCopy(currentConfig);
    }

    const uiStatus = deepCopy(
      scenario.configUiStatus ?? DEFAULT_DEVICE_CONFIG_UI_STATUS,
    );

    if (request.section === ConfigSection.ALL) {
      // Magic string 'iam-error' to simulate invalid user ID backend error during full update.
      if ((request.config.permissions?.owners || []).includes('iam-error')) {
        return throwError(() => ({
          error: {
            message:
              'Failed to update config of device ' +
              request.id +
              ', please make sure the provided user IDs in owner/executor fields are correct, or try again later.',
          },
        })).pipe(delay(1000));
      }
      const newConfig = deepCopy(request.config);
      this.mockDeviceScenarios[scenarioIndex].config = newConfig;
      return of({
        success: true,
        deviceConfig: newConfig,
        uiStatus,
      }).pipe(delay(1000));
    }

    switch (request.section) {
      case ConfigSection.PERMISSIONS:
        // input `iam-error` to test error handling for the invalid user ID.
        if ((request.config.permissions?.owners || []).includes('iam-error')) {
          return throwError(() => ({
            error: {
              message:
                'Failed to update config of device 2A131FDH200MS1, please make sure the provided user IDs in owner/executor fields are correct, or try again later.',
            },
          })).pipe(delay(1000));
        }
        if (
          !(request.config.permissions?.owners || []).includes(CURRENT_USER) &&
          !request.options?.overrideSelfLockout
        ) {
          return of<UpdateDeviceConfigResult>({
            success: false,
            error: {code: 'SELF_LOCKOUT_DETECTED'},
          }).pipe(delay(1000));
        }
        currentConfig.permissions = deepCopy(request.config.permissions);
        break;
      case ConfigSection.WIFI:
        currentConfig.wifi = deepCopy(request.config.wifi);
        break;
      case ConfigSection.DIMENSIONS:
        currentConfig.dimensions = deepCopy(request.config.dimensions);
        break;
      case ConfigSection.STABILITY:
        currentConfig.settings = deepCopy(request.config.settings);
        break;
      default:
        return of<UpdateDeviceConfigResult>({
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Unknown config section',
          },
        }).pipe(delay(1000));
    }

    this.mockDeviceScenarios[scenarioIndex].config = currentConfig;
    return of({
      success: true,
      deviceConfig: currentConfig,
      uiStatus,
    }).pipe(delay(1000));
  }

  override getRecommendedWifi(): Observable<RecommendedWifi[]> {
    return of(deepCopy(MOCK_RECOMMENDED_WIFIS)).pipe(delay(1000));
  }

  override getWifiSuggestions(
    request?: GetWifiSuggestionsRequest,
  ): Observable<GetWifiSuggestionsResponse> {
    const aggregated = new Map<
      string,
      {ssid: string; psk: string; scanSsid: boolean; count: number}
    >();
    for (const scenario of this.mockDeviceScenarios) {
      const wifi = scenario.config?.wifi;
      const ssid = wifi?.ssid?.trim();
      if (!ssid) continue;
      const scanSsid = Boolean(wifi?.scanSsid);
      const key = `${ssid}:${scanSsid}`;
      const existing = aggregated.get(key);
      if (existing) {
        existing.count += 1;
      } else {
        aggregated.set(key, {
          ssid,
          psk: wifi?.psk || '',
          scanSsid,
          count: 1,
        });
      }
    }

    const fleetWifis: CandidateWifi[] = Array.from(aggregated.values())
      .sort((a, b) => b.count - a.count)
      .map((item) => ({
        wifi: {
          ssid: item.ssid,
          psk: item.psk,
          scanSsid: item.scanSsid,
        },
        fleetInUse: {deviceCount: item.count},
      }));

    const curatedWifis: CandidateWifi[] = MOCK_RECOMMENDED_WIFIS.filter(
      (w) => !aggregated.has(`${w.ssid}:false`),
    ).map((w) => ({
      wifi: {
        ssid: w.ssid,
        psk: w.psk,
        scanSsid: false,
      },
      curatedPreset: {},
    }));

    return of({candidateWifis: [...fleetWifis, ...curatedWifis]}).pipe(
      delay(500),
    );
  }

  override batchGetDeviceWifiConfigs(
    request: BatchGetDeviceWifiConfigsRequest,
  ): Observable<BatchGetDeviceWifiConfigsResponse> {
    const entries: DeviceWifiConfigEntry[] = request.devices.map((target) => {
      const deviceId = target.deviceId;
      const scenario = this.findDeviceScenario(target);
      const {writable, unwritableReason} =
        this.getDeviceWifiWritability(scenario);
      const currentWifi = scenario?.config?.wifi?.ssid
        ? {
            ssid: scenario.config.wifi.ssid,
            psk: scenario.config.wifi.psk || '',
            scanSsid: Boolean(scenario.config.wifi.scanSsid),
          }
        : undefined;

      let status =
        DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_UNSPECIFIED;
      if (!writable) {
        status = DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY;
      } else if (!request.target) {
        status = DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE;
      } else {
        const matches = this.isWifiMatchingTarget(currentWifi, request.target);
        status = matches
          ? DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES
          : DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE;
      }

      return {
        deviceId,
        ...(target.hostName ? {hostName: target.hostName} : {}),
        ...(currentWifi ? {currentWifi} : {}),
        status,
        ...(unwritableReason ? {readOnlyReason: unwritableReason} : {}),
      };
    });

    return of({entries}).pipe(delay(500));
  }

  override getConfigurableDimensions(
    request?: GetConfigurableDimensionsRequest,
  ): Observable<GetConfigurableDimensionsResponse> {
    const dimensions: ConfigurableDimension[] = [
      {
        dimensionName: 'recovery',
        displayName: 'recovery',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      },
      {
        dimensionName: 'skip_suw_app',
        displayName: 'skip_suw_app',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      },
      {
        dimensionName: 'pool',
        displayName: 'pool',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      },
      {
        dimensionName: 'persist_test_harness',
        displayName: 'persist_test_harness',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      },
      {
        dimensionName: 'sim_operator',
        displayName: 'sim_operator',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      },
    ];

    return of({
      enabled: true,
      dimensions,
      allowCustomDimensions: true,
    }).pipe(delay(200));
  }

  override batchGetConfigurableDimensionKeys(
    request: BatchGetConfigurableDimensionKeysRequest,
  ): Observable<BatchGetConfigurableDimensionKeysResponse> {
    const candidateKeys: CandidateDimensionKey[] = [
      {
        dimensionName: 'recovery',
        selectedDeviceCount: request.devices.length,
        fleetDeviceCount: 8420,
      },
      {
        dimensionName: 'skip_suw_app',
        selectedDeviceCount: Math.floor(request.devices.length / 2),
        fleetDeviceCount: 6150,
      },
      {
        dimensionName: 'pool',
        selectedDeviceCount: Math.floor(request.devices.length / 3),
        fleetDeviceCount: 5280,
      },
      {
        dimensionName: 'persist_test_harness',
        selectedDeviceCount: 0,
        fleetDeviceCount: 3110,
      },
      {
        dimensionName: 'sim_operator',
        selectedDeviceCount: 0,
        fleetDeviceCount: 1940,
      },
    ];

    const systemDimensionNames = [
      'model',
      'sdk_version',
      'battery_level',
      'device_form',
      'cpu_architecture',
      'hardware',
      'wifi',
      'sim_state',
      'oem_unlock',
      'host_name',
      'status',
      'run_target',
      'uuid',
      'id',
      'ip',
      'port',
      'driver',
      'type',
    ];

    return of({
      candidateKeys,
      systemDimensionNames,
    }).pipe(delay(200));
  }

  override getDimensionValueSuggestions(
    request: GetDimensionValueSuggestionsRequest,
  ): Observable<GetDimensionValueSuggestionsResponse> {
    let candidateValues: CandidateDimensionValue[] = [];
    if (request.dimensionName === 'recovery') {
      candidateValues = [
        {values: ['true'], deviceCount: 5420},
        {values: ['false'], deviceCount: 2100},
        {values: [], deviceCount: 900},
      ];
    } else if (request.dimensionName === 'pool') {
      candidateValues = [
        {values: ['shared_pool'], deviceCount: 3800},
        {values: ['dedicated_pool'], deviceCount: 1200},
        {values: ['staging_pool'], deviceCount: 280},
        {values: [], deviceCount: 150},
      ];
    } else {
      candidateValues = [
        {values: ['true'], deviceCount: 1500},
        {values: ['false'], deviceCount: 800},
        {values: [], deviceCount: 200},
      ];
    }

    return of({candidateValues}).pipe(delay(200));
  }

  override batchGetDeviceDimensionConfigs(
    request: BatchGetDeviceDimensionConfigsRequest,
  ): Observable<BatchGetDeviceDimensionConfigsResponse> {
    const entries: DeviceDimensionConfigEntry[] = request.devices.map(
      (target, i) => {
        const did = target.deviceId;
        let values: string[] = [];
        if (request.dimensionName === 'recovery') {
          values = i % 2 === 0 ? ['true'] : ['false'];
        } else if (request.dimensionName === 'pool') {
          values =
            i % 3 === 0
              ? ['shared_pool']
              : i % 3 === 1
                ? ['dedicated_pool']
                : [];
        } else {
          values = i % 2 === 0 ? ['val-1'] : [];
        }

        let writable = true;
        let unwritableReason = '';
        if (
          /^0+$/.test(did) ||
          did.startsWith('Failed') ||
          did.startsWith('Disconnected') ||
          did.includes('unrecognizable') ||
          did.includes('invalid')
        ) {
          writable = false;
          unwritableReason = 'Unrecognizable or invalid device ID';
        } else if (i % 7 === 1) {
          writable = false;
          unwritableReason =
            'Managed by host shared config (update host config instead)';
        } else if (i % 7 === 2) {
          writable = false;
          unwritableReason = 'Managed by Config Pusher';
        } else if (i % 7 === 3) {
          writable = false;
          unwritableReason = 'No permission (not an authorized owner)';
        }

        let status =
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_UNSPECIFIED;
        if (!writable) {
          status =
            DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY;
        } else if (!request.target) {
          status =
            DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE;
        } else {
          const matches = this.isDimensionMatchingTarget(
            values,
            request.target,
          );
          status = matches
            ? DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES
            : DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE;
        }

        return {
          deviceId: did,
          ...(target.hostName ? {hostName: target.hostName} : {}),
          values,
          status,
          ...(unwritableReason ? {readOnlyReason: unwritableReason} : {}),
        };
      },
    );

    return of({entries}).pipe(delay(300));
  }

  private isDimensionMatchingTarget(
    currentValues: string[],
    target: TargetDimensionValues,
  ): boolean {
    if (target.clearValues) {
      return currentValues.length === 0;
    }
    const targetVals = target.valueList?.values || [];
    if (currentValues.length !== targetVals.length) {
      return false;
    }
    const sortedCurrent = Array.from(currentValues, (v) =>
      v.toLowerCase(),
    ).sort();
    const sortedTarget = Array.from(targetVals, (v) => v.toLowerCase()).sort();
    return sortedCurrent.every((v, i) => v === sortedTarget[i]);
  }

  override batchUpdateDeviceDimensionConfigs(
    request: BatchUpdateDeviceDimensionConfigsRequest,
  ): Observable<BatchUpdateDeviceDimensionConfigsResponse> {
    const errors: Record<string, UpdateError> = {};
    for (const target of request.devices) {
      const did = target.deviceId;
      if (did.includes('fail') || did.includes('error')) {
        errors[did] = {
          code: 'VALIDATION_ERROR',
          message: 'Device offline or unreachable',
        };
      }
    }
    return of({errors}).pipe(delay(800));
  }

  override batchUpdateDeviceWifiConfigs(
    request: BatchUpdateDeviceWifiConfigsRequest,
  ): Observable<BatchUpdateDeviceWifiConfigsResponse> {
    const errors: Record<string, UpdateError> = {};
    for (const target of request.devices) {
      const deviceId = target.deviceId;
      const scenario = this.findDeviceScenario(target);
      if (!scenario) {
        errors[deviceId] = {
          code: 'UNKNOWN',
          message: 'Device not found',
        };
        continue;
      }
      if (scenario.configUpdateError) {
        errors[deviceId] = deepCopy(scenario.configUpdateError);
        continue;
      }
      const {writable, unwritableReason} =
        this.getDeviceWifiWritability(scenario);
      if (!writable) {
        errors[deviceId] = {
          code: 'PERMISSION_DENIED',
          message: unwritableReason || 'Device Wi-Fi config is not writable',
        };
        continue;
      }
      if (request.target.clearWifi) {
        if (scenario.config) {
          scenario.config = {
            ...scenario.config,
            wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
          };
        }
      } else if (request.target.wifi) {
        const nextWifi: WifiConfig = {
          type: 'custom',
          ssid: request.target.wifi.ssid ?? '',
          psk: request.target.wifi.psk ?? '',
          scanSsid: Boolean(request.target.wifi.scanSsid),
        };
        if (scenario.config) {
          scenario.config = {
            ...scenario.config,
            wifi: nextWifi,
          };
        } else {
          scenario.config = {
            permissions: {owners: [CURRENT_USER], executors: []},
            wifi: nextWifi,
            dimensions: {supported: [], required: []},
            settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
          };
        }
      }
    }
    return of({errors}).pipe(delay(800));
  }

  private findDeviceScenario(
    target: DeviceTarget,
  ): MockDeviceScenario | undefined {
    if (target.hostName) {
      const exactMatch = this.mockDeviceScenarios.find(
        (s) =>
          s.id === target.deviceId &&
          s.overview?.host?.name === target.hostName,
      );
      if (exactMatch) {
        return exactMatch;
      }
    }
    return this.mockDeviceScenarios.find((s) => s.id === target.deviceId);
  }

  private isWifiMatchingTarget(
    currentWifi?: DeviceWifi,
    target?: TargetDeviceWifi,
  ): boolean {
    if (!target) return false;
    if (target.clearWifi) {
      return !currentWifi || !currentWifi.ssid;
    }
    if (target.wifi) {
      if (!currentWifi || !currentWifi.ssid) return false;
      return (
        currentWifi.ssid === target.wifi.ssid &&
        (currentWifi.psk || '') === (target.wifi.psk || '') &&
        Boolean(currentWifi.scanSsid) === Boolean(target.wifi.scanSsid)
      );
    }
    return false;
  }

  private getDeviceWifiWritability(scenario?: MockDeviceScenario): {
    writable: boolean;
    unwritableReason?: string;
  } {
    if (!scenario) {
      return {
        writable: false,
        unwritableReason: 'Device not found',
      };
    }
    const did = scenario.id || '';
    if (
      /^0+$/.test(did) ||
      did.startsWith('Failed') ||
      did.startsWith('Disconnected') ||
      did.includes('unrecognizable') ||
      did.includes('invalid')
    ) {
      return {
        writable: false,
        unwritableReason: 'Unrecognizable or invalid device ID',
      };
    }
    if (scenario.isHostManaged) {
      return {
        writable: false,
        unwritableReason:
          'Managed by host shared config (update host config instead)',
      };
    }
    const wifiStatus = scenario.configUiStatus?.wifi;
    if (wifiStatus) {
      if (wifiStatus.visible === false) {
        return {
          writable: false,
          unwritableReason:
            'Wi-Fi configuration is not supported on this device',
        };
      }
      if (wifiStatus.editability && !wifiStatus.editability.editable) {
        return {
          writable: false,
          unwritableReason:
            wifiStatus.editability.reason || 'Wi-Fi configuration is read-only',
        };
      }
    }
    if (scenario.config) {
      const owners = scenario.config.permissions?.owners || [];
      if (!owners.includes(CURRENT_USER)) {
        return {
          writable: false,
          unwritableReason: 'No permission (not an authorized owner)',
        };
      }
    }
    return {writable: true};
  }

  // ===== Host Config Methods =====

  override getHostDefaultDeviceConfig(
    hostName: string,
  ): Observable<DeviceConfig | null> {
    const scenario = this.mockHostScenarios.find(
      (s) => s.hostName === hostName,
    );
    return of(scenario ? deepCopy(scenario.defaultDeviceConfig) : null).pipe(
      delay(1000),
    );
  }

  override getHostConfig(hostName: string): Observable<GetHostConfigResult> {
    const scenario = this.mockHostScenarios.find(
      (s) => s.hostName === hostName,
    );
    if (!scenario) {
      return throwError(
        () => new Error(`Host with name '${hostName}' not found in mock data.`),
      ).pipe(delay(1000));
    }
    const result = deepCopy(scenario.hostConfigResult);
    if (result.hostConfig) {
      result.hostConfig = normalizeHostConfig(result.hostConfig);
    }
    return of(result).pipe(delay(1000));
  }

  override checkHostConfigPermission(
    hostName: string,
    universe?: string,
  ): Observable<CheckHostConfigPermissionResult> {
    const scenario = this.mockHostScenarios.find(
      (s) => s.hostName === hostName,
    );

    if (scenario && hostName.includes('host-basic-editable')) {
      return of({hasPermission: false}).pipe(delay(1000));
    }
    // For fake service, always grant permission to the current user.
    return of({hasPermission: true, userName: CURRENT_USER}).pipe(delay(1000));
  }

  override updateHostConfig(
    request: UpdateHostConfigRequest,
  ): Observable<UpdateHostConfigResult> {
    const scenarioIndex = this.mockHostScenarios.findIndex(
      (s) => s.hostName === request.hostName,
    );
    if (scenarioIndex === -1) {
      return of<UpdateHostConfigResult>({
        success: false,
        error: {code: 'UNKNOWN', message: 'Host not found'},
      }).pipe(delay(1000));
    }

    const currentScenario = this.mockHostScenarios[scenarioIndex];
    let updatedConfig = currentScenario.hostConfigResult.hostConfig;

    if (!updatedConfig) {
      updatedConfig = deepCopy(request.config);
    } else {
      updatedConfig = deepCopy(updatedConfig);
    }

    const paths = request.scope?.updateMask?.paths || [];

    if (paths.length === 0) {
      // Full update
      // Magic string 'iam-error' in either Host Admins or Device Owners simulates backend error.
      if (
        (request.config.permissions?.hostAdmins || []).includes('iam-error') ||
        (request.config.deviceConfig?.permissions?.owners || []).includes(
          'iam-error',
        )
      ) {
        return throwError(() => ({
          error: {
            message: getInvalidUserErrorMessage(request.hostName, paths),
          },
        })).pipe(delay(1000));
      }
      updatedConfig = deepCopy(request.config);
    } else {
      if (isPathInFieldMask('permissions', paths)) {
        if (
          (request.config.permissions?.hostAdmins || []).includes('iam-error')
        ) {
          return throwError(() => ({
            error: {
              message: getInvalidUserErrorMessage(request.hostName, paths),
            },
          })).pipe(delay(1000));
        }
        if (
          !request.config.permissions.hostAdmins.includes(CURRENT_USER) &&
          !request.options?.overrideSelfLockout
        ) {
          return of<UpdateHostConfigResult>({
            success: false,
            error: {code: 'SELF_LOCKOUT_DETECTED'},
          }).pipe(delay(1000));
        }
        updatedConfig.permissions = deepCopy(request.config.permissions);
      }
      if (isPathInFieldMask('device_config_mode', paths)) {
        updatedConfig.deviceConfigMode = request.config.deviceConfigMode;
      }
      if (isPathInFieldMask('device_config', paths)) {
        if (isPathInFieldMask('device_config.permissions', paths)) {
          if (
            (request.config.deviceConfig?.permissions?.owners || []).includes(
              'iam-error',
            )
          ) {
            return throwError(() => ({
              error: {
                message: getInvalidUserErrorMessage(request.hostName, paths),
              },
            })).pipe(delay(1000));
          }
        }
        updatedConfig.deviceConfig = deepCopy(request.config.deviceConfig);
      }
      if (isPathInFieldMask('host_properties', paths)) {
        updatedConfig.hostProperties = deepCopy(request.config.hostProperties);
      }
      if (isPathInFieldMask('device_discovery', paths)) {
        updatedConfig.deviceDiscovery = deepCopy(
          request.config.deviceDiscovery,
        );
      }
    }

    this.mockHostScenarios[scenarioIndex].hostConfigResult.hostConfig =
      updatedConfig;
    return of({
      success: true,
      hostConfig: updatedConfig,
      uiStatus: currentScenario.hostConfigResult.uiStatus,
    }).pipe(delay(1000));
  }

  override unlockHostProperties(
    hostName: string,
  ): Observable<UnlockHostPropertiesResponse> {
    return of({success: true}).pipe(delay(1000));
  }
}
