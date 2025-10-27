import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import type {PermissionInfo} from '../../../../../../core/models/device_config_models';
import {UserAdd} from '../../../../../../shared/components/common_config/user_add/user_add';

/**
 * Component for displaying the permissions of a device in the device
 * configuration workflow.
 */
@Component({
  selector: 'app-permissions',
  standalone: true,
  templateUrl: './permissions.ng.html',
  styleUrl: './permissions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, UserAdd],
})
export class Permissions implements OnInit, OnChanges {
  @Input() workflow: 'wizard' | 'settings' = 'wizard';
  @Input() permissions: PermissionInfo = {
    owners: [],
    executors: [],
  };

  @Output() readonly permissionsChange = new EventEmitter<PermissionInfo>();

  PERMISSIONS = [
    {
      type: 'owners',
      title: 'Owners',
      definition:
        this.workflow === 'wizard'
          ? "Who should be the owners of this device? They will have full control, including changing its configuration and running tests. You can add users (e.g., 'derekchen') or groups (e.g., 'gmm-prebuild')."
          : "Owners have full control over the device, including changing its configuration and executing tests. Can be a user (e.g., 'derekchen') or a group (e.g., 'gmm-prebuild').",
      users: this.permissions.owners,
    },
    {
      type: 'executors',
      title: 'Executors',
      definition:
        this.workflow === 'wizard'
          ? 'Besides the owners, who else should have permission to run tests?'
          : 'Executors can only execute tests on the device.',
      users: this.permissions.executors,
    },
  ];

  ngOnInit() {
    this.PERMISSIONS[0].users = this.permissions.owners;
    this.PERMISSIONS[1].users = this.permissions.executors;
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['permissions']) {
      this.PERMISSIONS[0].users = changes['permissions'].currentValue.owners;
      this.PERMISSIONS[1].users = changes['permissions'].currentValue.executors;
    }
  }
}
