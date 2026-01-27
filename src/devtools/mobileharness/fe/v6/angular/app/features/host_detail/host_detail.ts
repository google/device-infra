import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {Observable, of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

import {type HostOverview} from '../../core/models/host_overview';
import {HOST_SERVICE} from '../../core/services/host/host_service';
import {HostConfig} from './components/host_config/host_config';
import {HostEmpty} from './components/host_config/host_empty/host_empty';
import {HostSettings} from './components/host_config/host_settings/host_settings';
// deviceinfra:google3-replace-end
import {HostOverviewPage} from './components/host_overview/host_overview';

interface HostPageData {
  host: HostOverview | null;
  error?: string;
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
    MatMenuModule,
    RouterModule,
  ],
})
export class HostDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly hostService = inject(HOST_SERVICE);

  readonly hostPageData$: Observable<HostPageData> = this.route.paramMap.pipe(
    map((params) => params.get('hostName')),
    switchMap((hostName: string | null) => {
      if (!hostName) {
        return of<HostPageData>({
          host: null,
          error: 'No host name provided in the route.',
        });
      }

      return this.hostService.getHostOverview(hostName).pipe(
        map((hostOverview) => ({
          host: hostOverview,
        })),
        catchError((err) => {
          console.error(`Error fetching host ${hostName}:`, err);
          return of<HostPageData>({
            host: null,
            error: `Failed to load host data for host: ${hostName}. ${err.message || ''}`,
          });
        }),
      );
    }),
  );

  ngOnInit() {}

  resetConfiguration(hostName: string) {
    this.dialog
      .open(HostEmpty, {
        data: {
          hostName,
          title:
            'You are about to clear the existing configuration for this host. Your current settings will be discarded. Please choose how you want to proceed.',
        },
        autoFocus: false,
      })
      .afterClosed()
      .subscribe((result) => {
        if (!result) {
          return;
        }

        this.createorcopyConfiguration(
          result.action,
          result.hostName,
          result.config,
        );
      });
  }

  createorcopyConfiguration(
    action: string,
    hostName: string,
    config: HostConfig,
  ) {
    this.dialog.open(HostSettings, {
      data: {hostName, config},
      autoFocus: false,
    });
  }

  openConfiguration(hostName: string) {
    const dialogRef = this.dialog.open(HostConfig, {
      data: {hostName},
      autoFocus: false,
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (!result) {
        return;
      }

      if (result.action === 'reset') {
        this.resetConfiguration(result.hostName);
        return;
      }

      if (result.action === 'new' || result.action === 'copy') {
        this.createorcopyConfiguration(result.action, hostName, result.config);
      }
    });
  }

  releaseLabServer(hostName: string) {
    // TODO: implement release lab server logic.
  }

  stopLabServer(hostName: string) {
    // TODO: implement stop lab server logic.
  }

  debugHost(hostName: string) {
    // TODO: implement debug host logic.
  }
}
