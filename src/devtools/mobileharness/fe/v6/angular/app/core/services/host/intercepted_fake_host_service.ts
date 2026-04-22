import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {HostHeaderInfo} from '../../models/host_action';
import {GetHostDeviceSummariesResponse, HostOverviewPageData} from '../../models/host_overview';
import {modifyHostDevices, modifyHostHeaderInfo, modifyHostOverview} from '../../utils/force_ready_utils';
import {FakeHostService} from './fake_host_service';

/**
 * A fake implementation of the HostService that supports forcing action buttons to be ready.
 */
@Injectable({
  providedIn: 'root',
})
export class InterceptedFakeHostService extends FakeHostService {
  private readonly forcedButtons: string[];

  constructor() {
    super();
    const urlParams = new URLSearchParams(window.location.search);
    const forceHostReady = urlParams.get('force_host_ready');
    this.forcedButtons = forceHostReady ? forceHostReady.split(',') : [];
  }

  override getHostHeaderInfo(hostName: string): Observable<HostHeaderInfo> {
    return super.getHostHeaderInfo(hostName).pipe(
      map((body) => {
        if (this.forcedButtons.length > 0) {
          return modifyHostHeaderInfo(body, this.forcedButtons);
        }
        return body;
      }),
    );
  }

  override getHostOverview(hostName: string): Observable<HostOverviewPageData> {
    return super.getHostOverview(hostName).pipe(
      map((body) => {
        if (this.forcedButtons.length > 0) {
          return modifyHostOverview(body, this.forcedButtons);
        }
        return body;
      }),
    );
  }

  override getHostDeviceSummaries(
    hostName: string,
  ): Observable<GetHostDeviceSummariesResponse> {
    return super.getHostDeviceSummaries(hostName).pipe(
      map((body) => {
        const urlParams = new URLSearchParams(window.location.search);
        const forceDeviceReady = urlParams.get('force_device_ready');
        const forcedDeviceButtons = forceDeviceReady ? forceDeviceReady.split(',') : [];

        if (forcedDeviceButtons.length > 0) {
          return modifyHostDevices(body, forcedDeviceButtons);
        }
        return body;
      }),
    );
  }
}
