import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {GoogleDate} from '../../../shared/utils/date_utils';
import {
  DeviceHeaderInfo,
  GetLogcatResponse,
  QuarantineDeviceRequest,
  QuarantineDeviceResponse,
  TakeScreenshotResponse,
} from '../../models/device_action';
import {
  DeviceOverviewPageData,
  GetDeviceOverviewRequest,
  TestbedConfig,
} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';
import {DeviceTestHistoryResponse} from '../../models/device_test_history';

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
  abstract getDeviceOverview(
    request: GetDeviceOverviewRequest,
  ): Observable<DeviceOverviewPageData>;

  /**
   * Retrieves header info for a specific device by its ID.
   * @param id The device ID.
   * @param hostName The host name.
   * @return An Observable emitting the device header info.
   */
  abstract getDeviceHeaderInfo(
    id: string,
    hostName: string,
  ): Observable<DeviceHeaderInfo>;

  /**
   * Retrieves healthiness statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   * @param hostName The host name.
   * @return An Observable emitting the healthiness statistics.
   */
  abstract getDeviceHealthinessStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
    hostName: string,
  ): Observable<HealthinessStats>;

  /**
   * Retrieves test result statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   * @param hostName The host name.
   * @return An Observable emitting the test result statistics.
   */
  abstract getDeviceTestResultStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
    hostName: string,
  ): Observable<TestResultStats>;

  /**
   * Retrieves recovery task statistics for a device within a given time range.
   * @param id The device ID.
   * @param startDate The start date of the range.
   * @param endDate The end date of the range.
   * @param hostName The host name.
   * @return An Observable emitting the recovery task statistics.
   */
  abstract getDeviceRecoveryTaskStats(
    id: string,
    startDate: GoogleDate,
    endDate: GoogleDate,
    hostName: string,
  ): Observable<RecoveryTaskStats>;

  /**
   * Takes a screenshot of the device.
   * @param id The device ID.
   * @param hostName The host name.
   * @return An Observable emitting the screenshot response.
   */
  abstract takeScreenshot(
    id: string,
    hostName: string,
  ): Observable<TakeScreenshotResponse>;

  /**
   * Retrieves logcat from the device.
   * @param id The device ID.
   * @param hostName The host name.
   * @return An Observable emitting the logcat response.
   */
  abstract getLogcat(
    id: string,
    hostName: string,
  ): Observable<GetLogcatResponse>;

  /**
   * Quarantines the device.
   */
  abstract quarantineDevice(
    id: string,
    req: QuarantineDeviceRequest,
  ): Observable<QuarantineDeviceResponse>;

  /**
   * Unquarantines the device.
   * @param id The device ID.
   * @param hostName The host name.
   * @return An Observable that completes when the operation finishes.
   */
  abstract unquarantineDevice(id: string, hostName: string): Observable<void>;

  /**
   * Prepares the device.
   *
   * @param id The ID of the device to prepare.
   * @param hostName The host name.
   * @return An Observable that completes when the prepare operation finishes.
   */
  abstract prepareDevice(id: string, hostName: string): Observable<void>;

  // /**
  //  * Starts a remote control session for the device.
  //  */
  // abstract remoteControl(
  //   id: string,
  //   req: RemoteControlRequest,
  // ): Observable<RemoteControlResponse>;

  /**
   * Gets testbed config for the device.
   * @param id The device ID.
   * @param hostName The host name.
   * @return An Observable emitting the testbed config.
   */
  abstract getTestbedConfig(
    id: string,
    hostName: string,
  ): Observable<TestbedConfig>;

  /**
   * Retrieves one page of the device's historical tests, newest first.
   *
   * @param id The device ID.
   * @param hostName The host name.
   * @param pageToken Cursor from a previous response; empty for the first page.
   * @return An Observable emitting the device test history response.
   */
  abstract getDeviceTestHistory(
    id: string,
    hostName: string,
    pageToken?: string,
  ): Observable<DeviceTestHistoryResponse>;
}
