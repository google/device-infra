import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  OnDestroy,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {LoadingService} from 'app/shared/services/loading_service';
import {combineLatest, Observable, of, ReplaySubject} from 'rxjs';
import {catchError, map, switchMap, takeUntil, tap} from 'rxjs/operators';

import {dateUtils} from 'app/shared/utils/date_utils';
import {APP_DATA, getLegacyFeUrl} from '../../core/models/app_data';
import {DeviceOverviewPageData} from '../../core/models/device_overview';
import {DEVICE_SERVICE} from '../../core/services/device/device_service';
import {NavLink} from '../../shared/components/nav_link/nav_link';
import {ClipboardService} from '../../shared/services/clipboard_service';
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
    NavLink,
  ],
  templateUrl: './device_detail_page.ng.html',
  styleUrl: './device_detail_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.has-background]': 'hasBackground',
  },
})
export class DeviceDetailPage implements OnInit, OnDestroy {
  hasBackground = true;

  @ViewChild(DeviceActionBar) actionBar!: DeviceActionBar;
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly route = inject(ActivatedRoute);
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly snackBar = inject(SnackBarService);
  private readonly titleService = inject(Title);
  private readonly destroyed = new ReplaySubject<void>(1);
  private readonly appData = inject(APP_DATA);
  private readonly clipboardService = inject(ClipboardService);
  private readonly loadingService = inject(LoadingService);

  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');

  activeTab = signal<'overview' | 'test-history' | 'health' | 'record'>(
    'overview',
  );
  isGoogle1p = signal<boolean>(false);

  ngOnInit() {
    combineLatest([
      this.route.paramMap.pipe(map((params) => params.get('id'))),
      this.route.queryParamMap.pipe(
        map((params) => ({
          isEmbedded: params.get('is_embedded_mode') === 'true',
          universe: params.get('universe'),
        })),
      ),
    ])
      .pipe(takeUntil(this.destroyed))
      .subscribe(([id, {isEmbedded, universe}]) => {
        const isStandalone = !isEmbedded;
        this.hasBackground = isStandalone;
        this.isGoogle1p.set(!universe || universe === 'google_1p');
        this.cdr.markForCheck();
        // for embedded mode, we don't need to set the title.
        if (isStandalone && id) {
          this.titleService.setTitle(`OmniLab Console - Device ${id}`);
        } else {
          this.titleService.setTitle(`OmniLab Console`);
        }
      });
  }

  ngOnDestroy() {
    this.destroyed.next();
    this.destroyed.complete();
    this.titleService.setTitle(`OmniLab Console`);
  }

  readonly devicePageData$: Observable<DevicePageData> =
    this.route.paramMap.pipe(
      map((params) => params.get('id')),
      tap(() => {
        this.loadingService.show();
      }),
      switchMap((id) => {
        if (!id) {
          this.loadingService.hide();
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
          tap(() => {
            this.loadingService.hide();
          }),
        );
      }),
    );

  setActiveTab(tab: 'overview' | 'test-history' | 'health' | 'record'): void {
    this.activeTab.set(tab);
  }

  unquarantineDevice() {
    if (this.actionBar.getAction('quarantine')?.isReady) {
      this.actionBar.quarantineDevice();
    } else {
      this.actionBar.showComingSoonPopup('quarantine');
    }
  }

  changeQuarantine() {
    if (this.actionBar.getAction('quarantine')?.isReady) {
      this.actionBar.changeQuarantine();
    } else {
      this.actionBar.showComingSoonPopup('quarantine');
    }
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
    const success = this.clipboardService.copyToClipboard(text);
    if (success) {
      this.snackBar.showSuccess('Copied to clipboard!');
    } else {
      this.snackBar.showError('Failed to copy text.');
    }
  }

  getFormattedQuarantineExpiry(expiry: string): string {
    return dateUtils.format(expiry);
  }
}
