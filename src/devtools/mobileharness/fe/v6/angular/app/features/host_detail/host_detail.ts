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
import {ActivatedRoute} from '@angular/router';
import {Observable, of} from 'rxjs';
import {map, switchMap} from 'rxjs/operators';

import {HostConfig} from './components/host_config/host_config';
import {HostEmpty} from './components/host_config/host_empty/host_empty';
import {HostWizard} from './components/host_config/host_wizard/host_wizard';
import {HostOverview} from './components/host_overview/host_overview';

interface HostPageData {
  hostName: string;
  hostIP?: string;
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
  imports: [CommonModule, MatIconModule, MatButtonModule, HostOverview],
})
export class HostDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  readonly hostPageData$: Observable<HostPageData> = this.route.paramMap.pipe(
    map((params) => params.get('hostName')),
    switchMap((hostName: string | null) => {
      if (!hostName) {
        return of<HostPageData>({
          hostName: '',
          error: 'No host name provided in the route.',
        });
      }
      return of<HostPageData>({
        hostName,
        hostIP: '192.168.1.101',
      });
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
    this.dialog.open(HostWizard, {
      data: {hostName, source: action, config},
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
