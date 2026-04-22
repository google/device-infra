import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {DeviceHeaderInfo} from '../../models/device_action';
import {DeviceOverviewPageData} from '../../models/device_overview';
import {modifyDeviceHeaderInfo, modifyDeviceOverview} from '../../utils/force_ready_utils';
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

  override getDeviceHeaderInfo(id: string): Observable<DeviceHeaderInfo> {
    return super.getDeviceHeaderInfo(id).pipe(
      map((body) => {
        if (this.forcedButtons.length > 0) {
          return modifyDeviceHeaderInfo(body, this.forcedButtons);
        }
        return body;
      }),
    );
  }

  override getDeviceOverview(id: string): Observable<DeviceOverviewPageData> {
    return super.getDeviceOverview(id).pipe(
      map((body) => {
        if (this.forcedButtons.length > 0) {
          return modifyDeviceOverview(body, this.forcedButtons);
        }
        return body;
      }),
    );
  }
}
