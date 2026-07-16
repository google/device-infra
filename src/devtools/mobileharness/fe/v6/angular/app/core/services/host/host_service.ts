import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';

import {
  DecommissionHostResponse,
  GetHostDebugInfoResponse,
  GetPopularFlagsResponse,
  HostHeaderInfo,
  LifecycleActionType,
  ListTroubleshootScriptsResponse,
  PreflightLabServerLifecycleResponse,
  PreflightLabServerReleaseResponse,
  ReleaseLabServerRequest,
  ReleaseLabServerResponse,
  RestartLabServerResponse,
  RunTroubleshootScriptResponse,
  StartLabServerResponse,
  StopLabServerResponse,
  UpdatePassThroughFlagsResponse,
} from '../../models/host_action';
import {
  CheckRemoteControlEligibilityResponse,
  DeviceTarget,
  GetHostDeviceSummariesResponse,
  HostOverviewPageData,
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
   * Retrieves basic information for host header and action bar.
   */
  abstract getHostHeaderInfo(hostName: string): Observable<HostHeaderInfo>;

  /**
   * Retrieves the detailed overview data for a specific host by its name.
   */
  abstract getHostOverview(hostName: string): Observable<HostOverviewPageData>;

  /**
   * Retrieves device summaries for a specific host.
   */
  abstract getHostDeviceSummaries(
    hostName: string,
  ): Observable<GetHostDeviceSummariesResponse>;

  /**
   * Retrieves diagnostic information from the host.
   */
  abstract getHostDebugInfo(
    hostName: string,
  ): Observable<GetHostDebugInfoResponse>;

  /**
   * Retrieves popular pass-through flags for a host.
   */
  abstract getPopularFlags(
    hostName: string,
  ): Observable<GetPopularFlagsResponse>;

  /**
   * Updates the pass through flags for a specific host.
   */
  abstract updatePassThroughFlags(
    hostName: string,
    flags: string,
  ): Observable<UpdatePassThroughFlagsResponse>;

  /**
   * Retrieves release configurations for a host.
   */
  abstract preflightLabServerRelease(
    hostName: string,
  ): Observable<PreflightLabServerReleaseResponse>;

  /**
   * Preflight check for lifecycle actions (Start/Restart/Stop).
   * Validates permission, action availability, and version before proceeding.
   */
  abstract preflightLabServerLifecycle(
    hostName: string,
    action: LifecycleActionType,
  ): Observable<PreflightLabServerLifecycleResponse>;

  /**
   * Decommissions missing devices on a specific host.
   * @param hostName The name of the host.
   * @param deviceIds The list of device IDs to decommission.
   * @return An Observable emitting when the operation is complete.
   */
  abstract decommissionMissingDevices(
    hostName: string,
    deviceIds: string[],
  ): Observable<void>;

  /**
   * Checks for remote control eligibility for the given devices on the host.
   * @param hostName The name of the host.
   * @param targets The list of device targets to check.
   * @return An Observable emitting the CheckRemoteControlEligibilityResponse.
   */
  abstract checkRemoteControlEligibility(
    hostName: string,
    targets: DeviceTarget[],
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

  /**
   * Decommissions a missing host and all its associated devices.
   * @param hostName The name of the host.
   * @return An Observable emitting the DecommissionHostResponse.
   */
  abstract decommissionHost(
    hostName: string,
  ): Observable<DecommissionHostResponse>;

  /**
   * Deploys a specific Lab Server release version to a host.
   */
  abstract releaseLabServer(
    hostName: string,
    req: ReleaseLabServerRequest,
  ): Observable<ReleaseLabServerResponse>;

  /**
   * Starts the lab server process on the host with same release version and
   * latest flags.
   */
  abstract startLabServer(hostName: string): Observable<StartLabServerResponse>;

  /**
   * Restarts the lab server process on the host with same release version and
   * latest flags.
   */
  abstract restartLabServer(
    hostName: string,
  ): Observable<RestartLabServerResponse>;

  /**
   * Stops the lab server process on the host.
   */
  abstract stopLabServer(hostName: string): Observable<StopLabServerResponse>;

  /**
   * Runs a troubleshooting script on the host.
   */
  abstract runTroubleshootScript(
    hostName: string,
    script: string,
    universe: string,
  ): Observable<RunTroubleshootScriptResponse>;

  /**
   * Lists all available troubleshooting scripts for the host with their safety metadata.
   */
  abstract listTroubleshootScripts(
    hostName: string,
    universe: string,
  ): Observable<ListTroubleshootScriptsResponse>;
}
