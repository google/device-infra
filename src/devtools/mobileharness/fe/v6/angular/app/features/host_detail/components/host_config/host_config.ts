import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {tap} from 'rxjs/operators';
import {HostConfigUiStatus} from '../../../../core/models/host_config_models';
import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {HostConfigStateService} from '../../../../core/services/config/host_config_state_service';
import {Environment} from '../../../../core/services/environment';
import {HostEmpty} from './host_empty/host_empty';
import {HostSettings} from './host_settings/host_settings';

const ATS_HOST_CONFIG_UI_STATUS: HostConfigUiStatus = {
  hostAdmins: {visible: false, editability: {editable: false}},
  deviceConfigMode: {visible: false, editability: {editable: false}},
  deviceConfig: {
    sectionStatus: {visible: true, editability: {editable: true}},
    subSections: {
      permissions: {visible: false, editability: {editable: false}},
      wifi: {visible: true, editability: {editable: true}},
      dimensions: {visible: false, editability: {editable: false}},
      settings: {visible: false, editability: {editable: false}},
    },
  },
  hostProperties: {
    sectionStatus: {visible: false, editability: {editable: false}},
  },
  deviceDiscovery: {visible: false, editability: {editable: false}},
};

/**
 * Component for displaying the host configuration dialog.
 *
 * It is used to configure the host's dimensions, owners, and other properties.
 */
@Component({
  selector: 'app-host-config',
  standalone: true,
  templateUrl: './host_config.ng.html',
  styleUrl: './host_config.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, HostEmpty, HostSettings],
})
export class HostConfig implements OnInit {
  readonly data = inject(MAT_DIALOG_DATA);

  private readonly configService = inject(CONFIG_SERVICE);
  private readonly hostConfigStateService = inject(HostConfigStateService);
  private readonly environment = inject(Environment);

  get isAts(): boolean {
    return !this.environment.isGoogleInternal();
  }

  readonly configResult$ = this.configService
    .getHostConfig(this.data.hostName)
    .pipe(
      tap((result) => {
        if (result && result.uiStatus) {
          this.hostConfigStateService.setUiStatus(
            this.data.hostName,
            result.uiStatus,
          );
        } else if (result && this.isAts) {
          this.hostConfigStateService.setUiStatus(
            this.data.hostName,
            ATS_HOST_CONFIG_UI_STATUS,
          );
        }
      }),
    );

  ngOnInit() {}
}
