import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
  ViewChild,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {Observable, of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

import {dateUtils} from 'app/shared/utils/date_utils';
import {DeviceOverviewPageData} from '../../core/models/device_overview';
import {DEVICE_SERVICE} from '../../core/services/device/device_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {DeviceActionBar} from './components/device_action_bar/device_action_bar';
import {DeviceOverviewTab} from './components/device_overview_tab/device_overview_tab';
import {HealthStatisticTab} from './components/health_statistic_tab/health_statistic_tab';

declare interface DevicePageData {
  pageData: DeviceOverviewPageData | null;
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
  imports: [
    CommonModule,
    RouterModule,
    MatIconModule,
    MatMenuModule,
    DeviceOverviewTab,
    DeviceActionBar,
    HealthStatisticTab,
    MatTooltipModule,
  ],
  templateUrl: './device_detail_page.ng.html',
  styleUrl: './device_detail_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceDetailPage {
  @ViewChild(DeviceActionBar) actionBar!: DeviceActionBar;
  private readonly route = inject(ActivatedRoute);
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly snackBar = inject(SnackBarService);

  activeTab = signal<'overview' | 'test-history' | 'health' | 'record'>(
    'overview',
  );

  readonly devicePageData$: Observable<DevicePageData> =
    this.route.paramMap.pipe(
      map((params) => params.get('id')),
      switchMap((id) => {
        if (!id) {
          return of<DevicePageData>({
            pageData: null,
            error: 'No device ID provided in the route.',
          });
        }
        return this.deviceService.getDeviceOverview(id).pipe(
          map((pageData) => ({
            pageData,
            error: null,
          })),
          catchError((err) => {
            console.error(`Error fetching device ${id}:`, err);
            return of<DevicePageData>({
              pageData: null,
              error: `Failed to load device data for ID: ${id}. ${
                err.message || ''
              }`,
            });
          }),
        );
      }),
    );

  setActiveTab(tab: 'overview' | 'test-history' | 'health' | 'record'): void {
    this.activeTab.set(tab);
  }

  unquarantineDevice() {
    this.actionBar.quarantineDevice();
  }

  changeQuarantine() {
    this.actionBar.changeQuarantine();
  }

  formatRemainingTime(expiry: string | undefined): string {
    if (!expiry) return '';
    const diffMs = new Date(expiry).getTime() - new Date().getTime();
    if (diffMs <= 0) return ' (Expired)';

    const diffSeconds = Math.floor(diffMs / 1000);
    const diffMinutes = Math.floor(diffSeconds / 60);
    const diffHours = Math.floor(diffMinutes / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffDays > 0) {
      return ` (${diffDays} day${diffDays > 1 ? 's' : ''} left)`;
    } else if (diffHours > 0) {
      return ` (${diffHours} hour${diffHours > 1 ? 's' : ''} left)`;
    } else if (diffMinutes > 0) {
      return ` (${diffMinutes} minute${diffMinutes > 1 ? 's' : ''} left)`;
    } else {
      return ` (less than 1 minute left)`;
    }
  }

  copyToClipboard(text: string): void {
    navigator.clipboard
      .writeText(text)
      .then(() => {
        this.snackBar.showSuccess('Copied to clipboard!');
      })
      .catch((err) => {
        this.snackBar.showError('Failed to copy text.');
        console.error('Failed to copy text: ', err);
      });
  }

  getFormattedQuarantineExpiry(expiry: string): string {
    return dateUtils.format(expiry);
  }
}
