/**
 * @fileoverview UI view models for the Home feature.
 */

/** Precomputed percentage distribution and tooltip strings for device utilization. */
export interface UtilizationMetrics {
  busyPct: number;
  idlePct: number;
  othersPct: number;
  idleLeftStyle: string;
  busyTooltip: string;
  idleTooltip: string;
  othersTooltip: string;
}

/** Fallback empty utilization metrics. */
export const EMPTY_UTILIZATION_METRICS: UtilizationMetrics = {
  busyPct: 0,
  idlePct: 0,
  othersPct: 0,
  idleLeftStyle: '15%',
  busyTooltip: '0 (0%) devices are used by customers.',
  idleTooltip: '0 (0%) devices are available.',
  othersTooltip: '0 (0%) devices are out of service (e.g., in maintenance).',
};
