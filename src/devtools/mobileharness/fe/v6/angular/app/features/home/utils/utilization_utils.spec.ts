import {DeviceUtilization} from '@deviceinfra/app/core/models/home';
import {EMPTY_UTILIZATION_METRICS} from '@deviceinfra/app/features/home/models/home_models';
import {calcUtilizationMetrics, getIdleLeftStyle} from './utilization_utils';

describe('utilization_utils', () => {
  describe('calcUtilizationMetrics', () => {
    it('returns empty metrics when utilization is undefined or totalDevices <= 0', () => {
      expect(calcUtilizationMetrics(undefined, 100)).toEqual(
        EMPTY_UTILIZATION_METRICS,
      );
      expect(
        calcUtilizationMetrics(
          {busy: {count: 10}, idle: {count: 10}, others: {count: 10}},
          0,
        ),
      ).toEqual(EMPTY_UTILIZATION_METRICS);
      expect(
        calcUtilizationMetrics(
          {busy: {count: 10}, idle: {count: 10}, others: {count: 10}},
          -5,
        ),
      ).toEqual(EMPTY_UTILIZATION_METRICS);
    });

    it('correctly calculates percentages and tooltips for normal inputs', () => {
      const mockUtil: DeviceUtilization = {
        busy: {count: 210},
        idle: {count: 600},
        others: {count: 190},
      };
      const metrics = calcUtilizationMetrics(mockUtil, 1000);

      expect(metrics.busyPct).toBe(21);
      expect(metrics.idlePct).toBe(60);
      expect(metrics.othersPct).toBe(19);
      expect(metrics.idleLeftStyle).toBe('21%');
      expect(metrics.busyPct + metrics.idlePct + metrics.othersPct).toBe(100);

      expect(metrics.busyTooltip).toBe(
        '210 (21%) devices are used by customers.',
      );
      expect(metrics.idleTooltip).toBe('600 (60%) devices are available.');
      expect(metrics.othersTooltip).toBe(
        '190 (19%) devices are out of service (e.g., in maintenance).',
      );
    });

    it('ensures percentages cleanly sum to 100% even with repeating fractions', () => {
      const mockUtil: DeviceUtilization = {
        busy: {count: 1},
        idle: {count: 1},
        others: {count: 1},
      };
      const metrics = calcUtilizationMetrics(mockUtil, 3);

      expect(metrics.busyPct).toBe(33);
      expect(metrics.idlePct).toBe(33);
      expect(metrics.othersPct).toBe(34);
      expect(metrics.busyPct + metrics.idlePct + metrics.othersPct).toBe(100);
    });
  });

  describe('getIdleLeftStyle', () => {
    it('clamps lower bound to 15% when busyPct is below 15%', () => {
      expect(getIdleLeftStyle(0)).toBe('15%');
      expect(getIdleLeftStyle(5)).toBe('15%');
      expect(getIdleLeftStyle(14)).toBe('15%');
    });

    it('returns exact percentage when between 15% and 75%', () => {
      expect(getIdleLeftStyle(15)).toBe('15%');
      expect(getIdleLeftStyle(35)).toBe('35%');
      expect(getIdleLeftStyle(60)).toBe('60%');
      expect(getIdleLeftStyle(75)).toBe('75%');
    });

    it('clamps upper bound to 75% when busyPct exceeds 75%', () => {
      expect(getIdleLeftStyle(76)).toBe('75%');
      expect(getIdleLeftStyle(90)).toBe('75%');
      expect(getIdleLeftStyle(100)).toBe('75%');
    });
  });
});
