import {inject, Injectable} from '@angular/core';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {DeviceConfigUiStatus} from '../../models/device_config_models';
import {normalizeDeviceConfigUiStatus} from '../../utils/device_config_utils';

/**
 * Service for managing the state of device configurations, specifically caching the
 * DeviceConfigUiStatus retrieved from the backend for the active device.
 */
@Injectable({
  providedIn: 'root',
})
export class DeviceConfigStateService {
  private deviceId?: string;
  private uiStatus?: DeviceConfigUiStatus;
  private readonly snackBar = inject(SnackBarService);

  /**
   * Caches the UI status for the given device.
   */
  setUiStatus(deviceId: string, status: Partial<DeviceConfigUiStatus>) {
    this.deviceId = deviceId;
    this.uiStatus = normalizeDeviceConfigUiStatus(status);
  }

  /**
   * Returns the cached UI status for the given device if it matches.
   */
  getUiStatus(deviceId: string): DeviceConfigUiStatus {
    if (this.deviceId === deviceId && this.uiStatus) {
      return this.uiStatus;
    }
    this.snackBar.showError(
      'Error: deviceConfig must be loaded from server first.',
    );
    throw new Error('deviceConfig must be loaded from server first.');
  }

  /**
   * Clears the cached UI status state.
   */
  clear() {
    this.deviceId = undefined;
    this.uiStatus = undefined;
  }
}
