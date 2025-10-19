import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  inject,
} from '@angular/core';
import {MatDialogModule} from '@angular/material/dialog';
import {Observable, of} from 'rxjs';
import {catchError, delay, map, startWith} from 'rxjs/operators';
import {CheckDeviceWritePermissionResult} from '../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

interface PermissionState {
  isChecking: boolean;
  hasPermission?: boolean;
}

/**
 * The footer component for common config dialog.
 */
@Component({
  selector: 'app-footer',
  standalone: true,
  templateUrl: './footer.ng.html',
  styleUrl: './footer.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatDialogModule],
})
export class Footer implements OnInit {
  @Input() deviceId = '';
  @Output() readonly onPermissionChange =
    new EventEmitter<CheckDeviceWritePermissionResult>();

  private readonly configService = inject(CONFIG_SERVICE);
  readonly permissionResult$: Observable<PermissionState | void> =
    this.configService.checkDeviceWritePermission(this.deviceId).pipe(
      delay(1000),
      map((result) => {
        this.onPermissionChange.emit(result);

        return {
          isChecking: false,
          hasPermission: result.hasPermission,
        };
      }),
      catchError(() =>
        of({
          isChecking: false,
          hasPermission: false,
        }),
      ),
      startWith({isChecking: true}),
    );

  ngOnInit() {}
}
