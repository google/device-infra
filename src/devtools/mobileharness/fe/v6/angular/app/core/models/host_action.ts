import {ActionButtonState} from './action_common';


/**
 * Represents the state of action buttons for a host.
 */
export declare interface HostActions {
  readonly configuration: ActionButtonState;
  readonly debug: ActionButtonState;
  readonly deploy: ActionButtonState;
  readonly start: ActionButtonState;
  readonly restart: ActionButtonState;
  readonly stop: ActionButtonState;
  readonly decommission: ActionButtonState;
  readonly updatePassThroughFlags: ActionButtonState;
  readonly release: ActionButtonState;
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
 * Configuration for a specific Lab Server release.
 */
export declare interface HostReleaseConfig {
  readonly name: string;
  readonly version: string;
  readonly port: ReleasePort;
  readonly syncCMD: string[];
  readonly asyncCMD: string[];
}

/**
 * Port configuration for a release.
 */
export declare interface ReleasePort {
  readonly protocol: string;
  readonly portNumber: number;
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

