import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {CheckDeviceWritePermissionResult} from '../../../../core/models/device_config_models';
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
    Footer,
    MatDialogModule,
  ],
})
export class Dialog implements OnInit {
  @Input() width = '48rem';
  @Input() height = '';

  @Input() deviceId = '';
  @Input() title = '';
  @Input() subtitle = '';

  @Input() hasFooter = true;
  @Output() readonly onPermissionChange =
    new EventEmitter<CheckDeviceWritePermissionResult>();

  ngOnInit() {}
}
