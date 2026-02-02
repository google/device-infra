import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {
  CheckDeviceWritePermissionResult,
  ConfigSection,
  DeviceConfig,
  DeviceConfigUiStatus,
  GetDeviceConfigResult,
  RecommendedWifi,
  UpdateDeviceConfigRequest,
  UpdateDeviceConfigResult,
} from '../../models/device_config_models';
import {
  CheckHostWritePermissionResult,
  GetHostConfigResult,
  HostConfigSection,
  UpdateHostConfigRequest,
  UpdateHostConfigResult,
} from '../../models/host_config_models';
import {MOCK_DEVICE_SCENARIOS, MOCK_HOST_SCENARIOS} from '../mock_data';
import {MockDeviceScenario, MockHostScenario} from '../mock_data/models';
import {ConfigService} from './config_service';

const CURRENT_USER = 'derekchen';

// Helper function to deep copy an object
function deepCopy<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj)) as T;
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
    this.mockDeviceScenarios = deepCopy(MOCK_DEVICE_SCENARIOS);
    this.mockHostScenarios = deepCopy(MOCK_HOST_SCENARIOS);
  }

  // ===== Device Config Methods =====

  override getDeviceConfig(
    deviceId: string,
  ): Observable<GetDeviceConfigResult> {
    const scenario = this.mockDeviceScenarios.find((s) => s.id === deviceId);
    if (!scenario) {
      return throwError(
        () => new Error(`Device with ID '${deviceId}' not found in mock data.`),
      );
    }

    // Simulate host-managed scenario
    const isHostManaged = scenario.overview.host.name === 'host-x.example.com';
    const hostName = scenario.overview.host.name;

    let uiStatus: Partial<DeviceConfigUiStatus> | undefined;
    if (deviceId === 'WIFI_DIMENSIONS_ONLY_DEVICE') {
      uiStatus = {
        permissions: {visible: false},
        wifi: {visible: true, editability: {editable: true}},
        dimensions: {visible: true, editability: {editable: true}},
        settings: {visible: false},
      };
    }

    return of({
      deviceConfig: scenario.config,
      isHostManaged,
      hostName,
      uiStatus,
    });
  }

  override checkDeviceWritePermission(
    deviceId: string,
  ): Observable<CheckDeviceWritePermissionResult> {
    const scenario = this.mockDeviceScenarios.find((s) => s.id === deviceId);
    if (!scenario || !scenario.config) {
      // If no config exists, permission is granted to create one.
      return of({hasPermission: true, userName: CURRENT_USER});
    }

    const hasPermission =
      scenario.config.permissions.owners.includes(CURRENT_USER);
    return of({hasPermission, userName: CURRENT_USER});
  }

  override updateDeviceConfig(
    request: UpdateDeviceConfigRequest,
  ): Observable<UpdateDeviceConfigResult> {
    const scenarioIndex = this.mockDeviceScenarios.findIndex(
      (s) => s.id === request.deviceId,
    );
    if (scenarioIndex === -1) {
      return of({
        success: false,
        error: {code: 'UNKNOWN', message: 'Device not found'},
      });
    }

    let currentConfig = this.mockDeviceScenarios[scenarioIndex].config;
    if (!currentConfig) {
      // If config is null, create a new one
      currentConfig = deepCopy(request.config);
    } else {
      currentConfig = deepCopy(currentConfig);
    }

    if (request.section === ConfigSection.ALL) {
      this.mockDeviceScenarios[scenarioIndex].config = deepCopy(request.config);
      return of({success: true});
    }

    switch (request.section) {
      case ConfigSection.PERMISSIONS:
        if (
          !request.config.permissions.owners.includes(CURRENT_USER) &&
          !request.options?.overrideSelfLockout
        ) {
          return of({success: false, error: {code: 'SELF_LOCKOUT_DETECTED'}});
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
        return of({
          success: false,
          error: {code: 'VALIDATION_ERROR', message: 'Unknown config section'},
        });
    }

    this.mockDeviceScenarios[scenarioIndex].config = currentConfig;
    return of({success: true});
  }

  override getRecommendedWifi(): Observable<RecommendedWifi[]> {
    return of([
      {ssid: 'GoogleGuest', psk: ''},
      {ssid: 'GoogleGuestPSK', psk: 'google_guest_password_123'},
      {ssid: 'WL-MobileHarness', psk: 'mh_lab_secret_key'},
      {ssid: 'Android-Lab-Secure', psk: 'secure_android_99'},
      {ssid: 'Guest-Open', psk: ''},
      {ssid: 'Lab-Testing-A', psk: 'test_pass_alpha'},
      {ssid: 'Lab-Testing-B', psk: 'test_pass_beta'},
      {ssid: 'Lab-Testing-C', psk: 'test_pass_gamma'},
      {ssid: 'Pixel-Factory', psk: 'factory_reset_key'},
      {ssid: 'WearOS-Lab', psk: 'wear_os_dedicated_key'},
      {ssid: 'Nest-Devices', psk: 'nest_devices_iot_secret'},
      {ssid: 'Auto-Testing-Network', psk: 'automation_bot_pass'},
      {ssid: 'Voice-Isolation-Lab', psk: 'audio_lab_key_456'},
    ]);
  }

  // ===== Host Config Methods =====

  override getHostDefaultDeviceConfig(
    hostName: string,
  ): Observable<DeviceConfig | null> {
    const scenario = this.mockHostScenarios.find(
      (s) => s.hostName === hostName,
    );
    return of(scenario ? deepCopy(scenario.defaultDeviceConfig) : null);
  }

  override getHostConfig(hostName: string): Observable<GetHostConfigResult> {
    const scenario = this.mockHostScenarios.find(
      (s) => s.hostName === hostName,
    );
    if (!scenario) {
      return throwError(
        () => new Error(`Host with name '${hostName}' not found in mock data.`),
      );
    }
    return of(deepCopy(scenario.hostConfigResult));
  }

  override checkHostWritePermission(
    hostName: string,
  ): Observable<CheckHostWritePermissionResult> {
    // For fake service, always grant permission to the current user.
    return of({hasPermission: true, userName: CURRENT_USER});
  }

  override updateHostConfig(
    request: UpdateHostConfigRequest,
  ): Observable<UpdateHostConfigResult> {
    const scenarioIndex = this.mockHostScenarios.findIndex(
      (s) => s.hostName === request.hostName,
    );
    if (scenarioIndex === -1) {
      return of({
        success: false,
        error: {code: 'UNKNOWN', message: 'Host not found'},
      });
    }

    const currentScenario = this.mockHostScenarios[scenarioIndex];
    let updatedConfig = currentScenario.hostConfigResult.hostConfig;

    if (!updatedConfig) {
      updatedConfig = deepCopy(request.config);
    } else {
      updatedConfig = deepCopy(updatedConfig);
    }

    if (!request.scope) {
      // Full update
      updatedConfig = deepCopy(request.config);
    } else {
      switch (request.scope.section) {
        case HostConfigSection.HOST_PERMISSIONS:
          if (
            !request.config.permissions.hostAdmins.includes(CURRENT_USER) &&
            !request.options?.overrideSelfLockout
          ) {
            return of({success: false, error: {code: 'SELF_LOCKOUT_DETECTED'}});
          }
          updatedConfig.permissions = deepCopy(request.config.permissions);
          break;
        case HostConfigSection.DEVICE_CONFIG_MODE:
          updatedConfig.deviceConfigMode = request.config.deviceConfigMode;
          break;
        case HostConfigSection.DEVICE_CONFIG:
          // Further handling for deviceConfigSection would be needed here
          updatedConfig.deviceConfig = deepCopy(request.config.deviceConfig);
          break;
        case HostConfigSection.HOST_PROPERTIES:
          updatedConfig.hostProperties = deepCopy(
            request.config.hostProperties,
          );
          break;
        case HostConfigSection.DEVICE_DISCOVERY:
          updatedConfig.deviceDiscovery = deepCopy(
            request.config.deviceDiscovery,
          );
          break;
        default:
          return of({
            success: false,
            error: {
              code: 'VALIDATION_ERROR',
              message: 'Unknown host config section',
            },
          });
      }
    }

    this.mockHostScenarios[scenarioIndex].hostConfigResult.hostConfig =
      updatedConfig;
    return of({success: true});
  }
}
