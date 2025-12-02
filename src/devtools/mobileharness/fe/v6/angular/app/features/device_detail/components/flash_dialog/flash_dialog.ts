import {Clipboard} from '@angular/cdk/clipboard';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
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
import {MatTooltipModule} from '@angular/material/tooltip';
import {FlashDialogData} from '../../../../core/models/device_action';
import {Dialog} from '../../../../shared/components/config_common/dialog/dialog';
import {SnackBarService} from '../../../../shared/services/snackbar_service';

/**
 * Flash dialog component.
 */
@Component({
  selector: 'app-flash-dialog',
  standalone: true,
  templateUrl: './flash_dialog.ng.html',
  styleUrl: './flash_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // encapsulation: ViewEncapsulation.None,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatButtonModule,
    MatTooltipModule,
    Dialog,
  ],
})
export class FlashDialog implements OnInit {
  readonly data: FlashDialogData = inject(MAT_DIALOG_DATA);
  private readonly snackBar = inject(SnackBarService);
  private readonly clipboard = inject(Clipboard);
  branch = 'git_main';
  buildId = '12345678';
  buildTarget = 'husky-next-userdebug';
  command = signal('');

  ngOnInit() {
    this.updateCommand();
  }

  updateCommand() {
    let cmd = `tools/android/tab/cli/device_flash.sh \\\n --uuid="${this.data.deviceId}" \\\n --hostname="${this.data.hostName}" \\\n --device_type="${this.data.deviceType}"`;
    if (this.data.requiredDimensions) {
      cmd += ` \\\n --required_dimensions="${this.data.requiredDimensions}"`;
    }
    cmd += ` \\\n --branch="${this.branch}" \\\n --build_id="${this.buildId}" \\\n --build_target="${this.buildTarget}"`;
    this.command.set(cmd);
  }

  copyCommand() {
    const success = this.clipboard.copy(this.command());
    if (success) {
      this.snackBar.showSuccess('Command copied to clipboard!');
    } else {
      this.snackBar.showError('Failed to copy command! Please copy manually.');
    }
  }
}
