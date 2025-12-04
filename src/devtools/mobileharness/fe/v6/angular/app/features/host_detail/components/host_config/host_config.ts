import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {map} from 'rxjs/operators';
import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {HostEmpty} from './host_empty/host_empty';
import {HostSettings} from './host_settings/host_settings';

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

  readonly configResult$ = this.configService
    .getHostConfig(this.data.hostName)
    .pipe(
      map((result) => {
        if (
          result.hostConfig?.deviceConfigMode ===
          'NODEVICE_CONFIG_MODE_UNSPECIFIEDNE'
        ) {
          result.hostConfig.deviceConfigMode = 'PER_DEVICE';
        }
        return result;
      }),
    );

  ngOnInit() {}
}
