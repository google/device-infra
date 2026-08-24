import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {DeviceHeaderInfo} from '../../models/device_action';
import {
  DeviceOverviewPageData,
  GetDeviceOverviewRequest,
} from '../../models/device_overview';
import {
  modifyDeviceHeaderInfo,
  modifyDeviceOverview,
} from '../../utils/force_ready_utils';
import {FakeDeviceService} from './fake_device_service';

/**
 * A fake implementation of the DeviceService that supports forcing action buttons to be ready.
 */
@Injectable({
  providedIn: 'root',
})
export class InterceptedFakeDeviceService extends FakeDeviceService {
  private readonly forcedButtons: string[];

  constructor() {
    super();
    const urlParams = new URLSearchParams(window.location.search);
    const forceDeviceReady = urlParams.get('force_device_ready');
    this.forcedButtons = forceDeviceReady ? forceDeviceReady.split(',') : [];
  }

  /**
   * Retrieves the header information for a specific device, supporting forced ready states.
   * @param id The ID of the device.
   * @param hostName The name of the host.
   * @return An Observable emitting the DeviceHeaderInfo.
   */
  override getDeviceHeaderInfo(
    id: string,
    hostName: string,
  ): Observable<DeviceHeaderInfo> {
    return super.getDeviceHeaderInfo(id, hostName).pipe(
      map((body) => {
        if (this.forcedButtons.length > 0) {
          return modifyDeviceHeaderInfo(body, this.forcedButtons);
        }
        return body;
      }),
    );
  }

  override getDeviceOverview(
    request: GetDeviceOverviewRequest,
  ): Observable<DeviceOverviewPageData> {
    return super.getDeviceOverview(request).pipe(
      map((body) => {
        if (this.forcedButtons.length > 0) {
          return modifyDeviceOverview(body, this.forcedButtons);
        }
        return body;
      }),
    );
  }
}
