import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';
import {combineLatest, Observable, of, ReplaySubject} from 'rxjs';
import {catchError, map, switchMap, takeUntil, tap} from 'rxjs/operators';

import {HostOverviewPageData} from '../../core/models/host_overview';
import {HOST_SERVICE} from '../../core/services/host/host_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {HostActionBar} from './components/host_action_bar/host_action_bar';
import {HostOverviewPage} from './components/host_overview/host_overview';

import {openInNewTab} from '../../shared/utils/safe_dom';

interface HostPageData {
  hostOverviewPageData: HostOverviewPageData | null;
  error?: string;
  errorDetails?: string;
}

/**
 * Component for displaying the detail of a host.
 */
@Component({
  selector: 'app-host-detail',
  standalone: true,
  templateUrl: './host_detail.ng.html',
  styleUrl: './host_detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    HostOverviewPage,
    HostActionBar,
    MatMenuModule,
    RouterModule,
  ],
  host: {
    '[class.has-background]': 'hasBackground',
  },
})
export class HostDetail implements OnInit, OnDestroy {
  hasBackground = true;

  private readonly cdr = inject(ChangeDetectorRef);
  private readonly route = inject(ActivatedRoute);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly titleService = inject(Title);
  private readonly loadingService = inject(LoadingService);
  private readonly destroyed = new ReplaySubject<void>(1);
  private readonly clipboardService = inject(ClipboardService);
  private readonly snackBar = inject(SnackBarService);

  showDetails = false;

  toggleDetails() {
    this.showDetails = !this.showDetails;
    this.cdr.markForCheck();
  }

  reportBug(errorMessage: string, errorDetails: string) {
    const maxDetailsLength = 1000;
    let detailsForBug = errorDetails || '';
    if (detailsForBug.length > maxDetailsLength) {
      detailsForBug =
        detailsForBug.substring(0, maxDetailsLength) +
        '\n\n... (details truncated)';
    }

    const title = encodeURIComponent(
      `[MHFE] Operation failed: ${errorMessage}`,
    );
    const body = encodeURIComponent(
      `Operation failed.\n\nError: ${errorMessage}\n\nDetails:\n${detailsForBug}`,
    );
    const url = `https://issuetracker.google.com/issues/new?component=94628&title=${title}&description=${body}`;
    openInNewTab(url);
  }

  copyToClipboard(text: string): void {
    const success = this.clipboardService.copyToClipboard(text);
    if (success) {
      this.snackBar.showSuccess('Copied to clipboard!');
    } else {
      this.snackBar.showError('Failed to copy text.');
    }
  }

  readonly hostPageData$: Observable<HostPageData> = this.route.paramMap.pipe(
    map((params) => params.get('hostName')),
    tap(() => {
      this.loadingService.show();
    }),
    switchMap((hostName: string | null) => {
      if (!hostName) {
        this.loadingService.hide();
        return of<HostPageData>({
          hostOverviewPageData: null,
          error: 'No host name provided in the route.',
        });
      }

      return this.hostService.getHostOverview(hostName).pipe(
        map((hostOverviewPageData) => ({
          hostOverviewPageData,
        })),
        catchError((err) => {
          console.error(`Error fetching host ${hostName}:`, err);
          let errorMsg = `Failed to load host data for host: ${hostName}.`;
          if (err.message && err.message.includes('HOST_NOT_FOUND')) {
            errorMsg = `Host not found: ${hostName}.`;
          } else if (err.message) {
            errorMsg += ` ${err.message}`;
          }
          return of<HostPageData>({
            hostOverviewPageData: null,
            error: errorMsg,
            errorDetails: formatErrorDetails(err),
          });
        }),
        tap(() => {
          this.loadingService.hide();
        }),
      );
    }),
  );

  ngOnInit() {
    combineLatest([
      this.route.paramMap.pipe(map((params) => params.get('hostName'))),
      this.route.queryParamMap.pipe(
        map((params) => params.get('is_embedded_mode') === 'true'),
      ),
    ])
      .pipe(takeUntil(this.destroyed))
      .subscribe(([hostName, isEmbedded]) => {
        const isStandalone = !isEmbedded;
        this.hasBackground = isStandalone;
        this.cdr.markForCheck();
        // for embedded mode, we don't need to set the title.
        if (isStandalone && hostName) {
          this.titleService.setTitle(`OmniLab Console - Host ${hostName}`);
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
}

function formatErrorDetails(err: unknown): string {
  if (!err) return '';
  if (err instanceof Error && err.stack) return err.stack;
  if (typeof err === 'object') return JSON.stringify(err, null, 2);
  return String(err);
}
