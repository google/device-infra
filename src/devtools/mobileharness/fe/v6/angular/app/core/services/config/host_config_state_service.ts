import {inject, Injectable} from '@angular/core';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {HostConfigUiStatus} from '../../models/host_config_models';

/**
 * Service for managing the state of host configurations, specifically caching the
 * HostConfigUiStatus retrieved from the backend for the active host.
 */
@Injectable({
  providedIn: 'root',
})
export class HostConfigStateService {
  private hostName?: string;
  private uiStatus?: HostConfigUiStatus;
  private readonly snackBar = inject(SnackBarService);

  /**
   * Caches the UI status for the given host.
   */
  setUiStatus(host: string, status: HostConfigUiStatus) {
    this.hostName = host;
    this.uiStatus = status;
  }

  /**
   * Returns the cached UI status for the given host if it matches.
   */
  getUiStatus(host: string): HostConfigUiStatus {
    if (this.hostName === host && this.uiStatus) {
      return this.uiStatus;
    }
    this.snackBar.showError(
      'Error: hostConfig must be loaded from server first.',
    );
    throw new Error('hostConfig must be loaded from server first.');
  }

  /**
   * Clears the cached UI status state.
   */
  clear() {
    this.hostName = undefined;
    this.uiStatus = undefined;
  }
}
