/**
 * @fileoverview All-site search (global ID resolution) models extracted from search_all_site.proto.
 */

/**
 * Identifier type scope enum matching ResolveAllSiteQueryRequest.IdType in search_all_site.proto.
 */
export enum AllSiteIdType {
  ID_TYPE_UNSPECIFIED = 'ID_TYPE_UNSPECIFIED',
  ID_TYPE_DEVICE_ID = 'ID_TYPE_DEVICE_ID',
  ID_TYPE_HOST_NAME = 'ID_TYPE_HOST_NAME',
  ID_TYPE_TEST_ID = 'ID_TYPE_TEST_ID',
  ID_TYPE_JOB_ID = 'ID_TYPE_JOB_ID',
  ID_TYPE_SESSION_ID = 'ID_TYPE_SESSION_ID',
}

/**
 * Marker message matching ResolveAllSiteQueryRequest.AnyId in search_all_site.proto.
 * Searching across all five identifier types simultaneously.
 */
export declare interface AllSiteAnyId {}

/**
 * Request message for SearchService.ResolveAllSiteQuery (`POST /v6/search/resolve`).
 *
 * Exactly one of `anyId` or `idType` may be set to represent the proto `oneof scope`.
 */
export declare interface ResolveAllSiteQueryRequest {
  /** Exact identifier string entered by the user in the header Jump Bar. */
  query: string;
  /** Search across all five identifier types simultaneously. */
  anyId?: AllSiteAnyId;
  /** Restrict resolution to a single identifier type. */
  idType?: AllSiteIdType;
}

/**
 * Device match candidate returned by ResolveAllSiteQuery.
 * Target route: `/devices/{deviceId}?host_name={hostName}&universe={universe}`.
 */
export declare interface AllSiteDeviceMatch {
  /** Matched device UUID / serial. */
  deviceId: string;
  /** Host name to which this device is attached. */
  hostName?: string;
  /** Device model display string (e.g., "pixel 10"). */
  model?: string;
  /** Universe identifier (e.g., "google_1p" or "ats_..."). */
  universe?: string;
}

/**
 * Host match candidate returned by ResolveAllSiteQuery.
 * Target route: `/hosts/{hostName}?universe={universe}`.
 */
export declare interface AllSiteHostMatch {
  /** Matched host name. */
  hostName: string;
  /** Number of devices attached to this host. */
  attachedDeviceCount?: number;
  /** IPv4/IPv6 address of the host. */
  hostIp?: string;
  /** Universe identifier (e.g., "google_1p" or "ats_..."). */
  universe?: string;
}

/**
 * Test match candidate returned by ResolveAllSiteQuery.
 * Target route: `/jobs/{jobId}/tests/{testId}`.
 */
export declare interface AllSiteTestMatch {
  /** Matched test UUID. */
  testId: string;
  /** Parent job UUID owning this test. */
  jobId: string;
}

/**
 * Job match candidate returned by ResolveAllSiteQuery.
 * Target route: `/jobs/{jobId}`.
 */
export declare interface AllSiteJobMatch {
  /** Matched job UUID. */
  jobId: string;
}

/**
 * Session match candidate returned by ResolveAllSiteQuery.
 * Target route: `/sessions/{sessionId}`.
 */
export declare interface AllSiteSessionMatch {
  /** Matched session UUID. */
  sessionId: string;
}

/**
 * A single resolved entity match (`oneof match` in `AllSiteMatch`).
 */
export declare interface AllSiteMatch {
  /** Populated when the match is a Device entity. */
  device?: AllSiteDeviceMatch;
  /** Populated when the match is a Host entity. */
  host?: AllSiteHostMatch;
  /** Populated when the match is a Test entity. */
  test?: AllSiteTestMatch;
  /** Populated when the match is a Job entity. */
  job?: AllSiteJobMatch;
  /** Populated when the match is a Session entity. */
  session?: AllSiteSessionMatch;
}

/**
 * Response message for SearchService.ResolveAllSiteQuery.
 *
 * - 0 items: No exact match found (404 state).
 * - 1 item: Unique match (immediately redirects to the corresponding detail page).
 * - >1 items: Ambiguous match (renders disambiguation candidate list).
 */
export declare interface ResolveAllSiteQueryResponse {
  /** List of matched entities across the requested scope. */
  matches?: AllSiteMatch[];
}
