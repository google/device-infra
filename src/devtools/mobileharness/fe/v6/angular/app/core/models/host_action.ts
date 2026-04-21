import {ActionButtonState} from './action_common';


/**
 * Represents the state of action buttons for a host.
 */
export declare interface HostActions {
  readonly configuration: ActionButtonState;
  readonly debug: ActionButtonState;
  readonly decommission: ActionButtonState;
}

/**
 * Represents the state of action buttons for a device.
 */
export declare interface LabServerActions {
  readonly release: ActionButtonState;
  readonly start: ActionButtonState;
  readonly restart: ActionButtonState;
  readonly stop: ActionButtonState;
}

/**
 * Data required to render the host detail page header and action bar.
 */
export declare interface HostHeaderInfo {
  readonly hostName: string;
  readonly actions: HostActions;
}

/**
 * Result of diagnostic command collection.
 */
export declare interface GetHostDebugInfoResponse {
  readonly results: CommandResult[];
  readonly timestamp: string;
}

/**
 * Result of a single diagnostic command.
 */
export declare interface CommandResult {
  readonly command: string;
  readonly stdout: string;
  readonly stderr: string;
}

/**
 * Represents a popular or recommended pass-through flag.
 */
export declare interface PopularFlag {
  readonly name: string;
  readonly description: string;
  readonly cmd: string;
}

/**
 * A preset configuration for pass-through flags.
 */
export declare interface FlagPreset {
  readonly label: string;
  readonly value: string;
  readonly description: string;
}

/**
 * Response for PreflightLabServerRelease API.
 */
export declare interface PreflightLabServerReleaseResponse {
  readonly permissionDenied?: PermissionDenied;
  readonly ready?: ReleaseReady;
}

/**
 * No deploy permission.
 */
export declare interface PermissionDenied {}

/**
 * All checks passed. Here are the available versions to deploy.
 */
export declare interface ReleaseReady {
  readonly versions: DeployableVersion[];
}

/**
 * Represents the status of a Lab Server release.
 */
export type ReleaseStatus = 'Latest' | 'Current' | 'Deprecated' | '';

/**
 * Configuration for a specific Lab Server release.
 */
export declare interface DeployableVersion {
  readonly name: string;
  readonly version: string;
  readonly status: ReleaseStatus;
  readonly buildTime: string;
  readonly ports?: ReleasePort[];
  readonly releaseDetails?: ReleaseDetails;
}

/**
 * Port configuration for a release.
 */
export declare interface ReleasePort {
  readonly protocol: string;
  readonly portNumber: number;
}

/**
 * Details about a specific release.
 */
export declare interface ReleaseDetails {
  readonly changeLogs?: ChangeLogGroup[];
  readonly files?: FileRecord[];
  readonly syncCommands?: CommandRecord[];
  readonly asyncCommands?: CommandRecord[];
}

/**
 * Represents a group of changes or bugs related to a release.
 */
export declare interface ChangeLogGroup {
  readonly name: string;
  readonly items: ChangeLogItem[];
}

/**
 * Represents a single change record or bug record for a release.
 */
export declare interface ChangeLogItem {
  readonly change?: ChangeRecord;
  readonly bug?: BugRecord;
}

/**
 * Represents a single change record for a release.
 */
export declare interface ChangeRecord {
  readonly cl: number;
  readonly author: string;
  readonly text: string;
  readonly bugs: number[];
}

/**
 * Represents a single bug record for a release.
 */
export declare interface BugRecord {
  readonly bug: number;
  readonly text: string;
}

/**
 * Represents a single file record for a release.
 */
export declare interface FileRecord {
  readonly name: string;
  readonly path: string;
}

/**
 * Represents a single command record for a release.
 */
export declare interface CommandRecord {
  readonly name: string;
  readonly command: string;
}

/**
 * Response for DecommissionHost API.
 */
export declare interface DecommissionHostResponse {}

/**
 * Response for those rollout action
 */
export declare interface RolloutResponse {
  readonly trackingUrl: string;
}

/**
 * Response for ReleaseLabServer API.
 */
export declare interface ReleaseLabServerResponse extends RolloutResponse {}

/**
 * Response for StartLabServer API.
 */
export declare interface StartLabServerResponse extends RolloutResponse {}

/**
 * Response for RestartLabServer API.
 */
export declare interface RestartLabServerResponse extends RolloutResponse {}

/**
 * Response for StopLabServer API.
 */
export declare interface StopLabServerResponse extends RolloutResponse {}

/**
 * Request for ReleaseLabServer API.
 */
export declare interface ReleaseLabServerRequest {
  readonly version: string;
  readonly flags?: string;
}

