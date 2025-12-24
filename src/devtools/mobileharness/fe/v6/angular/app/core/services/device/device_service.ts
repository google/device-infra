import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {GoogleDate} from '../../../shared/utils/date_utils';
import {
  DeviceHeaderInfo,
  GetLogcatResponse,
  QuarantineDeviceRequest,
  QuarantineDeviceResponse,
  RemoteControlRequest,
  RemoteControlResponse,
  TakeScreenshotResponse,
} from '../../models/device_action';
import {
  DeviceOverviewPageData,
  TestbedConfig,
} from '../../models/device_overview';
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
  abstract getDeviceOverview(id: string): Observable<DeviceOverviewPageData>;

  /**
   * Retrieves header info for a specific device by its ID.
   */
  abstract getDeviceHeaderInfo(id: string): Observable<DeviceHeaderInfo>;

  /**
   * Retrieves healthiness statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   */
  abstract getDeviceHealthinessStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<HealthinessStats>;

  /**
   * Retrieves test result statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   */
  abstract getDeviceTestResultStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<TestResultStats>;

  /**
   * Retrieves recovery task statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   */
  abstract getDeviceRecoveryTaskStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
  ): Observable<RecoveryTaskStats>;

  /**
   * Takes a screenshot of the device.
   */
  abstract takeScreenshot(id: string): Observable<TakeScreenshotResponse>;

  /**
   * Retrieves logcat from the device.
   */
  abstract getLogcat(id: string): Observable<GetLogcatResponse>;

  /**
   * Quarantines the device.
   */
  abstract quarantineDevice(
    id: string,
    req: QuarantineDeviceRequest,
  ): Observable<QuarantineDeviceResponse>;

  /**
   * Unquarantines the device.
   */
  abstract unquarantineDevice(id: string): Observable<void>;

  /**
   * Starts a remote control session for the device.
   */
  abstract remoteControl(
    id: string,
    req: RemoteControlRequest,
  ): Observable<RemoteControlResponse>;

  /**
   * Gets testbed config for the device.
   */
  abstract getTestbedConfig(id: string): Observable<TestbedConfig>;
}
