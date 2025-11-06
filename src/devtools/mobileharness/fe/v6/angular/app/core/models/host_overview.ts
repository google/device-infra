/**
 * @fileoverview Defines interfaces for Host Overview data, structured for
 * presentation in the UI, corresponding to messages in host_resources.proto.
 * These interfaces are designed according to the BFF principle, where the
 * backend provides semantic state and pre-formatted text, and the frontend
 * uses this data to render the UI, including icons, colors, and layouts.
 */

/**
 * Semantic state of host connectivity with the OmniLab master server.
 * Frontend uses this to determine icon and color.
 */
export type HostConnectivityState = 'RUNNING'|'MISSING'|'UNKNOWN';

/**
 * Represents the data needed to render the Host Connectivity card.
 * Corresponds to HostConnectivityStatus in host_resources.proto.
 */
export interface HostConnectivityStatus {
  /** The semantic state used by FE to determine styling (icon/color). */
  readonly state: HostConnectivityState;
  /** The display text for the state, e.g., "Running", "Missing". */
  readonly title: string;
  /**
   * Timestamp of when host was first detected as missing, in
   * RFC 3339 format (e.g., "2025-11-04T23:38:00Z").
   * Only provided by backend if state is 'MISSING'.
   * Frontend uses this to calculate and display offline duration.
   */
  readonly missingStartTime?: string;
  /** Tooltip text explaining the current connectivity status. */
  readonly tooltip: string;
}

/**
 * Semantic state of Lab Server activity.
 * Frontend uses this to determine icon, color, and spinning animation.
 */
export type LabServerActivityState =|'STARTED'|'STARTED_BUT_DISCONNECTED'|
    'STARTING'|'ERROR'|'DRAINING'|'DRAINED'|'STOPPING'|'STOPPED'|'UNKNOWN';

/**
 * Represents the data needed to render Lab Server activity.
 * Corresponds to LabServerInfo.Activity in host_resources.proto.
 */
export interface LabServerActivity {
  /** The semantic state used by FE to determine styling (icon/color/spin). */
  readonly state: LabServerActivityState;
  /** The display text for the state, e.g., "Started", "Stopping". */
  readonly title: string;
  /** Tooltip text explaining the current activity state. */
  readonly tooltip: string;
}

/**
 * Represents the data needed to render the Lab Server card.
 * Corresponds to LabServerInfo in host_resources.proto.
 */
export interface LabServerInfo {
  /** Host connectivity status details. */
  readonly connectivity: HostConnectivityStatus;
  /**
   * The activity of the lab server.
   * This is optional because it is not applicable to all host types (e.g., Core
   * Lab).
   */
  readonly activity?: LabServerActivity;
  /** The version of the lab server software. */
  readonly version: string;
  /** Pass-through flags configured for the lab server. */
  readonly passThroughFlags: string;
}

/**
 * Semantic state of the Daemon server process.
 * Frontend uses this to determine icon and color.
 */
export type DaemonServerState = 'RUNNING'|'MISSING'|'UNKNOWN';

/**
 * Represents the data needed to render Daemon server status.
 * Corresponds to DaemonServerInfo.Status in host_resources.proto.
 */
export interface DaemonServerStatus {
  /** The semantic state used by FE to determine styling (icon/color). */
  readonly state: DaemonServerState;
  /** The display text for the state, e.g., "Running", "Missing". */
  readonly title: string;
  /**
   * Timestamp of when daemon was first detected as missing, in RFC 3339
   * format (e.g., "2025-11-04T23:38:00Z").
   * Only provided by backend if state is 'MISSING'.
   * Frontend uses this to calculate and display missing duration.
   */
  readonly missingStartTime?: string;
  /** Tooltip text explaining the current daemon status. */
  readonly tooltip: string;
}

/**
 * Represents the data needed to render the Daemon Server card.
 * Corresponds to DaemonServerInfo in host_resources.proto.
 */
export interface DaemonServerInfo {
  /** The status of the daemon server. */
  readonly status: DaemonServerStatus;
  /** The version of the daemon server software. */
  readonly version: string;
}

/**
 * A summary of a device connected to the host, for display in the device list.
 * Corresponds to DeviceSummary in host_resources.proto.
 */
export interface DeviceSummary {
  // TODO: Define device summary fields when device table design is
  // finalized.
}

/**
 * Represents the comprehensive data required to render the Host Detail Page's
 * overview section. This is the top-level interface for host overview data.
 * Corresponds to HostOverview in host_resources.proto.
 */
export interface HostOverview {
  /** The unique name (ID) of the host. */
  readonly hostName: string;
  /** The IP address of the host. */
  readonly ip: string;
  /**
   * The user-friendly lab type name, derived by the backend,
   * e.g., "Core Lab", "Satellite Lab (SLaaS)".
   */
  readonly labTypeDisplayName: string;
  /** Lab server information. */
  readonly labServer: LabServerInfo;
  /** Daemon server information. */
  readonly daemonServer: DaemonServerInfo;
  /** A list of summaries for devices connected to this host. */
  readonly devices: readonly DeviceSummary[];
  /** A map of host-level properties for display. */
  readonly properties: {[key: string]: string};
  /** OS of the host machine, e.g., "gLinux", "macOS". */
  readonly os: string;
}
