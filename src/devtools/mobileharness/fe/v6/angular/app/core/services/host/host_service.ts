import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';

import {
  CheckRemoteControlEligibilityResponse,
  DeviceSummary,
  HostOverview,
  RemoteControlDevicesRequest,
  RemoteControlDevicesResponse,
} from '../../models/host_overview';

/**
 * Injection token for the HostService.
 */
export const HOST_SERVICE = new InjectionToken<HostService>('HostService');

/**
 * Abstract class defining the contract for host data operations.
 */
export abstract class HostService {
  /**
   * Retrieves the detailed overview data for a specific host by its name.
   */
  abstract getHostOverview(hostName: string): Observable<HostOverview>;

  /**
   * Retrieves device summaries for a specific host.
   */
  abstract getHostDeviceSummaries(
    hostName: string,
  ): Observable<DeviceSummary[]>;

  /**
   * Updates the pass through flags for a specific host.
   */
  abstract updatePassThroughFlags(
    hostName: string,
    flags: string,
  ): Observable<void>;

  /**
   * Decommissions missing devices on a specific host.
   * @param hostName The name of the host.
   * @param deviceControlIds The list of device control IDs to decommission.
   * @return An Observable emitting when the operation is complete.
   */
  abstract decommissionMissingDevices(
    hostName: string,
    deviceControlIds: string[],
  ): Observable<void>;

  /**
   * Checks for remote control eligibility for the given devices on the host.
   * @param hostName The name of the host.
   * @param deviceControlIds The list of device control IDs to check.
   * @return An Observable emitting the CheckRemoteControlEligibilityResponse.
   */
  abstract checkRemoteControlEligibility(
    hostName: string,
    deviceControlIds: string[],
  ): Observable<CheckRemoteControlEligibilityResponse>;

  /**
   * Starts remote control sessions for multiple devices.
   * @param req The request containing device IDs and configuration for the sessions.
   * @return An observable emitting the session results.
   */
  abstract remoteControlDevices(
    hostName: string,
    req: RemoteControlDevicesRequest,
  ): Observable<RemoteControlDevicesResponse>;
}
