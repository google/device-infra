/**
 * @fileoverview Pure utility functions for drilldown navigation URL generation.
 */

import {
  Fleet,
  FleetSearchTarget,
  SearchEntity,
} from '@deviceinfra/app/core/models/home';

/** Navigation parameters for Angular Router drilldown. */
export interface DrilldownTarget {
  path: string;
  queryParams: Record<string, string | string[] | null>;
}

/**
 * Builds the destination path and search query params for a FleetSearchTarget.
 * Maps SearchEntity to the appropriate route (/hosts or /devices), sets fleet scope,
 * and encodes filters matching the Search Page URL query format (e.g. !key~val1,val2).
 */
export function buildDrilldownUrl(target: FleetSearchTarget): DrilldownTarget {
  const isHost = target.entity === SearchEntity.SEARCH_ENTITY_HOST;
  const path = isHost ? '/hosts' : '/devices';

  const isAts = target.fleet === Fleet.FLEET_ATS;

  const fParams: string[] = [];
  for (const filter of target.filters ?? []) {
    if (filter.simple) {
      const prefix = filter.simple.negated ? '!' : '';
      const vals = (filter.simple.values ?? [])
        .map((v) => v.value)
        .filter(Boolean);
      if (vals.length > 0) {
        fParams.push(`${prefix}${filter.key}~${vals.join(',')}`);
      } else {
        fParams.push(`${prefix}${filter.key}`);
      }
    } else {
      fParams.push(filter.key);
    }
  }

  const queryParams: Record<string, string | string[] | null> = {
    'f': fParams.length > 0 ? fParams : null,
    'gb': null,
    'fleet': isAts ? 'ats' : null,
  };

  return {path, queryParams};
}
