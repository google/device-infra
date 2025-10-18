import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {DeviceOverview} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';

/**
 * Injection token for the DeviceService.
 */
export const DEVICE_SERVICE = new InjectionToken<DeviceService>(
  'DeviceService',
);

/**
 * Abstract class defining the contract for device data operations.
 */
export abstract class DeviceService {
  /**
   * Retrieves the detailed overview data for a specific device by its ID.
   */
  abstract getDeviceOverview(id: string): Observable<DeviceOverview>;

  /**
   * Retrieves healthiness statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  abstract getDeviceHealthinessStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<HealthinessStats>;

  /**
   * Retrieves test result statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  abstract getDeviceTestResultStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<TestResultStats>;

  /**
   * Retrieves recovery task statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  abstract getDeviceRecoveryTaskStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<RecoveryTaskStats>;
}
