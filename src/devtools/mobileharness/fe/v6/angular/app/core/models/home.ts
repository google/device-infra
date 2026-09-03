/**
 * @fileoverview Summary data models extracted from search_summary.proto.
 * Directly reuses existing models from search_fleet without duplicate definitions.
 */

import type {Filter} from './search_fleet';
import {Fleet, SearchEntity} from './search_fleet';

// Re-export shared fleet models used by home
export {Fleet, SearchEntity};
export type {Filter};

/**
 * A drill-down into Fleet Search: which entity to search, which fleet, and the
 * filter conditions to pre-apply.
 */
export declare interface FleetSearchTarget {
  entity: SearchEntity;
  fleet?: Fleet;
  filters?: Filter[];
}

/**
 * A filter-defined portion of the fleet together with its size.
 */
export declare interface FleetSubset {
  search?: FleetSearchTarget;
  count: number;
}

/**
 * Device status distribution for one fleet scope, rendered as the utilization bar.
 */
export declare interface DeviceUtilization {
  busy?: FleetSubset;
  idle?: FleetSubset;
  others?: FleetSubset;
}

/**
 * First-Party (Google-internal) OmniLab summary.
 */
export declare interface FirstPartySummary {
  hosts?: FleetSubset;
  devices?: FleetSubset;
  utilization?: DeviceUtilization;
}

/**
 * Summary for one partner ATS lab (a single ATS controller).
 */
export declare interface PartnerAtsLab {
  controllerId: string;
  displayName: string;
  hosts?: FleetSubset;
  devices?: FleetSubset;
  utilization?: DeviceUtilization;
}

/**
 * Summary for the partner ATS labs: aggregate plus per-controller breakdown.
 */
export declare interface PartnerAtsLabsSummary {
  labCount: number;
  hosts?: FleetSubset;
  devices?: FleetSubset;
  utilization?: DeviceUtilization;
  labs?: PartnerAtsLab[];
}

/** Request for the Home page global summary. */
export declare interface GetGlobalSummaryRequest {}

/** The Home page global summary response. */
export declare interface GlobalSummary {
  firstParty?: FirstPartySummary;
  partnerAtsLabs?: PartnerAtsLabsSummary;
}
