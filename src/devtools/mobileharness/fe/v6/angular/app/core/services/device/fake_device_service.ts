import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {DeviceOverview} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';
import {MOCK_DEVICE_SCENARIOS} from '../mock_data';
import {DeviceService} from './device_service';

/**
 * A fake implementation of the DeviceService for development and testing.
 * It uses the mock data defined in the central mock_data registry.
 */
@Injectable({
  providedIn: 'root',
})
export class FakeDeviceService extends DeviceService {
  constructor() {
    super();
  }

  /**
   * Retrieves the detailed overview data for a specific device by its ID
   * from the mock dataset.
   * @param id The unique identifier of the device.
   * @returns An Observable emitting the DeviceOverview data if found,
   *          or an error Observable if not found.
   */
  override getDeviceOverview(id: string): Observable<DeviceOverview> {
    const scenario = MOCK_DEVICE_SCENARIOS.find((s) => s.id === id);
    if (scenario) {
      return of(scenario.overview);
    } else {
      return throwError(
        () => new Error(`Device with ID '${id}' not found in mock data.`),
      );
    }
  }

  /**
   * Retrieves healthiness statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  override getDeviceHealthinessStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<HealthinessStats> {
    console.log(
      `FakeService: Fetching Healthiness for ${id} from ${startTime} to ${endTime}`,
    );
    // In a real scenario, we would check if the device exists.
    // Here, we just return mock data based on the date range.
    return of();
  }

  /**
   * Retrieves test result statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  override getDeviceTestResultStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<TestResultStats> {
    console.log(
      `FakeService: Fetching Test Results for ${id} from ${startTime} to ${endTime}`,
    );
    return of();
  }

  /**
   * Retrieves recovery task statistics for a device within a given time range.
   * @param id The device ID.
   * @param startTime ISO 8601 string representing the start of the range.
   * @param endTime ISO 8601 string representing the end of the range.
   */
  override getDeviceRecoveryTaskStats(
    id: string,
    startTime: string,
    endTime: string,
  ): Observable<RecoveryTaskStats> {
    console.log(
      `FakeService: Fetching Recovery Tasks for ${id} from ${startTime} to ${endTime}`,
    );
    return of();
  }

  // Future methods like listDevices, updateDeviceConfig, etc., would be added here.
}

// Optional: Provider for easy swapping in AppModule
// export const FAKE_DEVICE_SERVICE_PROVIDER = {
//   provide: DEVICE_SERVICE,
//   useClass: FakeDeviceService
// };
