import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, Input} from '@angular/core';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {HostConfig} from '../../host_config/host_config';

/**
 * Component to display "no permission" message for host actions.
 */
@Component({
  selector: 'app-action-no-permission-content',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './action_no_permission_content.ng.html',
  styleUrls: ['./action_no_permission_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActionNoPermissionContent {
  @Input({required: true}) hostName!: string;

  private readonly dialog = inject(MatDialog);
  private readonly dialogRef = inject(MatDialogRef);

  navigateToPermissionsPage() {
    this.dialog.open(HostConfig, {
      data: {hostName: this.hostName},
      autoFocus: false,
    });
    this.dialogRef.close();
  }
}
