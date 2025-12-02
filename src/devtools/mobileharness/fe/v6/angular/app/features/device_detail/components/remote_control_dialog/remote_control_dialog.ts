import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatSelectModule} from '@angular/material/select';
import {MatTooltipModule} from '@angular/material/tooltip';
import {openInNewTab} from 'app/shared/utils/safe_dom';
import {finalize} from 'rxjs/operators';
import {
  type RemoteControlRequest,
  RemoteControlDialogData,
} from '../../../../core/models/device_action';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {Dialog} from '../../../../shared/components/config_common/dialog/dialog';
import {ToggleSwitch} from '../../../../shared/components/toggle_switch/toggle_switch';

/**
 * Remote control dialog component.
 */
@Component({
  selector: 'app-remote-control-dialog',
  standalone: true,
  templateUrl: './remote_control_dialog.ng.html',
  styleUrl: './remote_control_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    Dialog,
    ToggleSwitch,
  ],
})
export class RemoteControlDialog implements OnInit {
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly cdr = inject(ChangeDetectorRef);
  readonly data: RemoteControlDialogData = inject(MAT_DIALOG_DATA);

  runAs: string;
  timeoutHours = 1;
  proxyType: 'DEFAULT' | 'TCP' | 'ADB' | 'VNC' = 'DEFAULT';
  videoResolution: 'DEFAULT' | 'HIGH' | 'LOW' = 'DEFAULT';
  maxVideoSize: 'DEFAULT' | '1024' = 'DEFAULT';
  enableFlash = false;
  flashBranch = 'git_main';
  flashBuildId = '';
  flashBuildTarget = '';

  startingSession = signal(false);

  timeoutError = '';
  flashBranchError = '';
  flashBuildIdError = '';
  flashBuildTargetError = '';

  constructor() {
    this.runAs = this.data.defaultRunAs;
  }

  ngOnInit() {}

  validateTimeout(): boolean {
    if (
      this.timeoutHours === null ||
      this.timeoutHours < 1 ||
      this.timeoutHours > 12
    ) {
      this.timeoutError = 'Timeout must be between 1 and 12 hours.';
      this.cdr.markForCheck();
      return false;
    }
    this.timeoutError = '';
    this.cdr.markForCheck();
    return true;
  }

  validateFlashBranch(): boolean {
    if (this.enableFlash && !this.flashBranch) {
      this.flashBranchError = 'Branch is required.';
      this.cdr.markForCheck();
      return false;
    }
    this.flashBranchError = '';
    this.cdr.markForCheck();
    return true;
  }

  validateFlashBuildId(): boolean {
    if (this.enableFlash && !this.flashBuildId) {
      this.flashBuildIdError = 'Build ID is required.';
      this.cdr.markForCheck();
      return false;
    }
    this.flashBuildIdError = '';
    this.cdr.markForCheck();
    return true;
  }

  validateFlashBuildTarget(): boolean {
    if (this.enableFlash && !this.flashBuildTarget) {
      this.flashBuildTargetError = 'Build Target is required.';
      this.cdr.markForCheck();
      return false;
    }
    this.flashBuildTargetError = '';
    this.cdr.markForCheck();
    return true;
  }

  validate(): boolean {
    const isTimeoutValid = this.validateTimeout();
    const isFlashBranchValid = this.validateFlashBranch();
    const isFlashBuildIdValid = this.validateFlashBuildId();
    const isFlashBuildTargetValid = this.validateFlashBuildTarget();
    return (
      isTimeoutValid &&
      isFlashBranchValid &&
      isFlashBuildIdValid &&
      isFlashBuildTargetValid
    );
  }

  startSession() {
    if (!this.validate()) {
      return;
    }
    this.startingSession.set(true);

    const request: RemoteControlRequest = {
      runAs: this.runAs,
      timeoutHours: this.timeoutHours,
      proxyType: this.proxyType,
      videoResolution: this.videoResolution,
      maxVideoSize: this.maxVideoSize,
    };

    if (this.enableFlash) {
      request.flashOptions = {
        branch: this.flashBranch,
        buildId: this.flashBuildId,
        target: this.flashBuildTarget,
      };
    }

    this.deviceService
      .remoteControl(this.data.deviceId, request)
      .pipe(
        finalize(() => {
          this.startingSession.set(false);
        }),
      )
      .subscribe((response) => {
        openInNewTab(response.sessionUrl);
      });
  }
}
