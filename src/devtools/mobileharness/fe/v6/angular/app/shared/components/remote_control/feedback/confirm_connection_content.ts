import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input, signal} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {type RemoteControlDevicesRequest} from 'app/core/models/host_overview';
import {PROXY_TYPE_LABELS} from '../remote_control.types';

/**
 * Component for displaying confirmation content before starting remote control.
 */
@Component({
  selector: 'app-confirm-connection-content',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTooltipModule],
  templateUrl: './confirm_connection_content.ng.html',
  styleUrls: ['./confirm_connection_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmConnectionContent {
  @Input() request: RemoteControlDevicesRequest | null = null;
  @Input() skippedDevices: Array<{id: string; reason: string}> = [];

  showSkippedInConfirm = signal(true);

  getDurationLabel(): string {
    if (!this.request) return '';
    const minutes = Math.floor(this.request.durationSeconds / 60);
    return `${minutes}m`;
  }

  getResolutionLabel(): string {
    if (!this.request) return '';
    const res = this.request.videoResolution;
    const limit = this.request.maxVideoSize === '1024' ? ' (1024px)' : '';

    if (!res) return 'Default Quality' + limit;
    return res === 'HIGH' ? 'High Quality' + limit : 'Low Quality' + limit;
  }

  getProxyLabel(): string {
    if (!this.request) return '';
    return PROXY_TYPE_LABELS[this.request.proxyType] || 'Unknown';
  }

  getFlashLabel(): string {
    if (!this.request) return '';
    return this.request.flashOptions ? 'Flash On' : 'Flash Off';
  }
}
