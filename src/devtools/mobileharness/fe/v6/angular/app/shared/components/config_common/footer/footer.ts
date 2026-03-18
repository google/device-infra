import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  inject,
} from '@angular/core';
import {MatDialogModule} from '@angular/material/dialog';
import {Observable, ReplaySubject, of} from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  map,
  startWith,
  switchMap,
} from 'rxjs/operators';
import {CheckDeviceWritePermissionResult} from '../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

interface PermissionState {
  isChecking: boolean;
  hasPermission?: boolean;
  message?: string;
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
export class Footer implements OnInit, OnChanges {
  @Input() type: 'device' | 'host' = 'device';
  @Input() param: {deviceId: string; hostName: string; universe?: string} = {
    deviceId: '',
    hostName: '',
    universe: '',
  };

  @Output() readonly onPermissionChange =
    new EventEmitter<CheckDeviceWritePermissionResult>();

  private readonly configService = inject(CONFIG_SERVICE);
  private readonly inputChange$ = new ReplaySubject<void>(1);

  readonly permissionResult$: Observable<PermissionState | void> =
    this.inputChange$.pipe(
      map(() =>
        this.type === 'device' ? this.param.deviceId : this.param.hostName,
      ),
      distinctUntilChanged(),
      switchMap((id) => {
        if (!id) {
          return of({isChecking: false, hasPermission: false, message: ''});
        }
        const checkPermission =
          this.type === 'device'
            ? this.configService.checkDeviceWritePermission(
                id,
                this.param.universe,
              )
            : this.configService.checkHostWritePermission(
                id,
                this.param.universe,
              );

        return checkPermission.pipe(
          map((result) => {
            this.onPermissionChange.emit(result);

            return {
              isChecking: false,
              hasPermission: result.hasPermission,
              message: this.getPermissionMessage(
                this.type,
                result.hasPermission,
              ),
            };
          }),
          catchError((error) => {
            console.error('Failed to check permission', error);
            return of({isChecking: false});
          }),
          startWith({isChecking: true}),
        );
      }),
    );

  ngOnInit() {
    this.inputChange$.next();
  }

  ngOnChanges() {
    this.inputChange$.next();
  }

  private getPermissionMessage(type: string, hasPermission: boolean): string {
    const messages = {
      device: {
        granted: 'Current user has permission to configure this device.',
        denied:
          'Current user does not have permission to configure this device.',
      },
      host: {
        granted: 'Host Admin permissions verified.',
        denied: 'Host Admin permissions denied.',
      },
    };

    if (type === 'device') {
      return hasPermission ? messages.device.granted : messages.device.denied;
    } else {
      return hasPermission ? messages.host.granted : messages.host.denied;
    }
  }
}
