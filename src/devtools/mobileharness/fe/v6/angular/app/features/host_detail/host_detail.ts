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
import {MatSnackBarRef} from '@angular/material/snack-bar';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';
import {combineLatest, merge, Observable, of, Subject} from 'rxjs';
import {
  catchError,
  map,
  scan,
  shareReplay,
  switchMap,
  takeUntil,
  tap,
  throttleTime,
} from 'rxjs/operators';

import {HostOverviewPageData} from '../../core/models/host_overview';
import {HOST_SERVICE} from '../../core/services/host/host_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {getErrorMessage} from '../../shared/utils/error_utils';
import {HostActionBar} from './components/host_action_bar/host_action_bar';
import {HostOverviewPage} from './components/host_overview/host_overview';

interface HostPageData {
  hostOverviewPageData: HostOverviewPageData | null;
  error?: string;
  hostName: string | null;
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
  protected readonly loadingService = inject(LoadingService);
  private readonly destroyed = new Subject<void>();
  private readonly clipboardService = inject(ClipboardService);
  private readonly snackBar = inject(SnackBarService);
  /**
   * Subject to trigger a data refresh.
   */
  private readonly refreshSubject$ = new Subject<boolean>();
  private refreshSnackBarRef?: MatSnackBarRef<unknown>;

  /**
   * Triggers a refresh of the host data.
   */
  triggerRefresh(): void {
    this.refreshSubject$.next(true);
  }

  copyToClipboard(text: string): void {
    const success = this.clipboardService.copyToClipboard(text);
    if (success) {
      this.snackBar.showSuccess('Copied to clipboard!');
    } else {
      this.snackBar.showError('Failed to copy text.');
    }
  }

  readonly hostPageData$: Observable<HostPageData> = this.route.paramMap
    .pipe(
      map((params) => params.get('hostName')),
      switchMap((hostName) =>
        merge(
          of({hostName, isRefresh: false}),
          this.refreshSubject$.pipe(map(() => ({hostName, isRefresh: true}))),
        ),
      ),
    )
    .pipe(
      throttleTime(1000, undefined, {leading: true, trailing: true}),
      tap(({isRefresh}) => {
        if (isRefresh) {
          this.refreshSnackBarRef?.dismiss();
          this.refreshSnackBarRef = this.snackBar.showInProgress(
            'Refreshing host data...',
          );
        }
      }),
      tap(() => {
        this.loadingService.show();
      }),
      switchMap(({hostName, isRefresh}) => {
        if (!hostName) {
          this.loadingService.hide();
          return of<HostPageData>({
            hostOverviewPageData: null,
            error: 'No host name provided in the route.',
            hostName: null,
          });
        }

        return this.hostService.getHostOverview(hostName).pipe(
          map((hostOverviewPageData) => {
            this.loadingService.hide();
            if (isRefresh && this.refreshSnackBarRef) {
              this.refreshSnackBarRef.dismiss();
              this.refreshSnackBarRef = undefined;
              this.snackBar.showSuccess('Host data refreshed.');
            }
            return {
              hostOverviewPageData,
              error: undefined,
              hostName,
            };
          }),
          catchError((err) => {
            console.error(`Error fetching host ${hostName}:`, err);
            this.loadingService.hide();
            if (this.refreshSnackBarRef) {
              this.refreshSnackBarRef.dismiss();
              this.refreshSnackBarRef = undefined;
              this.snackBar.showError(
                `Failed to refresh host data for host: ${hostName}.`,
              );
            }
            return of<HostPageData>({
              hostOverviewPageData: null,
              error: `Failed to load host data for host: ${hostName}. ${getErrorMessage(err)}`,
              hostName,
            });
          }),
        );
      }),
      scan<HostPageData, HostPageData>(
        (acc, curr) => {
          if (curr.hostOverviewPageData) {
            return curr;
          }
          // If failure, check if ID matches previous success
          if (acc.hostOverviewPageData && acc.hostName === curr.hostName) {
            return {
              ...acc,
              error: undefined, // Clear error for silent failure
            };
          }
          return curr;
        },
        {hostOverviewPageData: null, hostName: null},
      ),
      shareReplay(1),
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
    this.refreshSnackBarRef?.dismiss();
    this.refreshSnackBarRef = undefined;
    this.destroyed.next();
    this.destroyed.complete();
    this.titleService.setTitle(`OmniLab Console`);
  }
}
