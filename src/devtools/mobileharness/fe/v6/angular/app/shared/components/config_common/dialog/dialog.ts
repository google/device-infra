import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, ContentChild, EventEmitter, Input, OnInit, Output, TemplateRef} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';

import {CheckDeviceWritePermissionResult} from '../../../../core/models/device_config_models';
import {SafeHtmlPipe} from '../../../pipes/safe_html_pipe';
import {Footer} from '../footer/footer';

/**
 * The dialog component for common config.
 */
@Component({
  selector: 'app-dialog',
  standalone: true,
  templateUrl: './dialog.ng.html',
  styleUrl: './dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    Footer,
    SafeHtmlPipe,
  ],
})
export class Dialog implements OnInit {
  @Input() width = '48rem';
  @Input() height = '';

  @Input() title = '';
  @Input() subtitle = '';

  // used for footer type permission-check
  @Input() type: 'device'|'host' = 'device';
  @Input()
  param = {
    deviceId: '',
    hostName: '',
  };

  @Input() footerType: 'normal'|'permission-check'|'none' = 'permission-check';

  @Output()
  readonly onPermissionChange =
      new EventEmitter<CheckDeviceWritePermissionResult>();

  @ContentChild('actionsTemplate') actionsTemplate!: TemplateRef<{}>;

  ngOnInit() {}
}
