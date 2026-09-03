/**
 * @fileoverview Pure utility functions for device utilization calculation and styling.
 */

import type {DeviceUtilization} from '@deviceinfra/app/core/models/home';
import {
  EMPTY_UTILIZATION_METRICS,
  UtilizationMetrics,
} from '@deviceinfra/app/features/home/models/home_models';

/**
 * Calculates busy, idle, and others percentages & tooltip strings from a DeviceUtilization object.
 * Precalculating tooltips avoids expensive string concatenation and formatting during change detection.
 */
export function calcUtilizationMetrics(
  utilization?: DeviceUtilization,
  totalDevices?: number,
): UtilizationMetrics {
  if (!utilization) {
    return EMPTY_UTILIZATION_METRICS;
  }
  const busy = utilization.busy?.count ?? 0;
  const idle = utilization.idle?.count ?? 0;
  const others = utilization.others?.count ?? 0;
  const effectiveTotal = totalDevices ?? busy + idle + others;
  if (effectiveTotal <= 0) {
    return EMPTY_UTILIZATION_METRICS;
  }
  const busyPct = Math.round((busy / effectiveTotal) * 100);
  const idlePct = Math.round((idle / effectiveTotal) * 100);
  const othersPct = Math.max(0, 100 - busyPct - idlePct);
  return {
    busyPct,
    idlePct,
    othersPct,
    idleLeftStyle: getIdleLeftStyle(busyPct),
    busyTooltip: `${busy.toLocaleString()} (${busyPct}%) devices are used by customers.`,
    idleTooltip: `${idle.toLocaleString()} (${idlePct}%) devices are available.`,
    othersTooltip: `${others.toLocaleString()} (${othersPct}%) devices are out of service (e.g., in maintenance).`,
  };
}

/**
 * Computes the clamped left offset style for the IDLE legend item
 * (clamped between 15% and 75% to stay near the idle segment without colliding).
 */
export function getIdleLeftStyle(busyPct: number): string {
  return `${Math.min(Math.max(busyPct, 15), 75)}%`;
}
