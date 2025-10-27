import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {Observable, of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

import {DeviceOverview} from '../../core/models/device_overview';
import {DEVICE_SERVICE} from '../../core/services/device/device_service';
import {DeviceConfig} from './components/device_config/device_config';
import {DeviceOverviewTab} from './components/device_overview_tab/device_overview_tab';

interface DevicePageData {
  device: DeviceOverview | null;
  error: string | null;
}

/**
 * Component for displaying the detailed information of a single device.
 * It fetches device data based on the ID from the route parameters and
 * presents different tabs for overview, test history, health, and records.
 */
@Component({
  selector: 'app-device-detail-page',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, DeviceOverviewTab],
  templateUrl: './device_detail_page.ng.html',
  styleUrl: './device_detail_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly dialog = inject(MatDialog);

  activeTab = signal<'overview' | 'test-history' | 'health' | 'record'>(
    'overview',
  );

  readonly devicePageData$: Observable<DevicePageData> =
    this.route.paramMap.pipe(
      map((params) => params.get('id')),
      switchMap((id) => {
        if (!id) {
          return of<DevicePageData>({
            device: null,
            error: 'No device ID provided in the route.',
          });
        }
        return this.deviceService.getDeviceOverview(id).pipe(
          map((device) => ({
            device,
            error: null,
          })),
          catchError((err) => {
            console.error(`Error fetching device ${id}:`, err);
            return of<DevicePageData>({
              device: null,
              error: `Failed to load device data for ID: ${id}. ${err.message || ''}`,
            });
          }),
        );
      }),
    );

  setActiveTab(tab: 'overview' | 'test-history' | 'health' | 'record'): void {
    this.activeTab.set(tab);
  }

  // Mock action buttons from prototype
  openConfiguration(deviceId: string, hostName: string): void {
    const dialogRef = this.dialog.open(DeviceConfig, {
      data: {deviceId, hostName},
      autoFocus: false,
    });

    dialogRef.afterClosed().subscribe((permission: string | null) => {});
  }

  takeScreenshot(): void {
    alert('Screenshot action');
  }

  remoteControl(): void {
    alert('Remote Control action');
  }

  flashDevice(): void {
    alert('Flash action');
  }

  getLogcat(): void {
    alert('Get Logcat action');
  }

  quarantineDevice(): void {
    alert('Quarantine action');
  }

  copyToClipboard(text: string): void {
    navigator.clipboard
      .writeText(text)
      .then(() => {
        // Ideally, show a temporary success message
        console.log('Copied to clipboard:', text);
      })
      .catch((err) => {
        console.error('Failed to copy text: ', err);
      });
  }
}
