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
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import type {PermissionInfo} from '../../../../../../core/models/device_config_models';
import type {PartStatus} from '../../../../../../core/models/host_config_models';
import {EntryChip} from '../../../../../../shared/components/config_common/entry_chip/entry_chip';

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
  imports: [CommonModule, MatIconModule, MatTooltipModule, EntryChip],
})
export class Permissions implements OnInit, OnChanges {
  @Input() type: 'device' | 'host' = 'device';
  @Input() workflow: 'wizard' | 'settings' = 'wizard';
  @Input() uiStatus: PartStatus = {
    visible: true,
    editability: {editable: true},
  };

  @Input() title = '';
  @Input() description = '';
  @Input() hostTooltipText = '';

  @Input() permissions: PermissionInfo = {
    owners: [],
    executors: [],
  };
  @Output() readonly permissionsChange = new EventEmitter<PermissionInfo>();

  PERMISSIONS = [
    {
      type: 'owners',
      title: 'Owners',
      definition: '',
      icon: '',
      iconTitle: '',
      users: this.permissions.owners,
      emptyLabel: 'None added.',
      placeholder: 'Add user or group...',
      editable: true,
    },
    {
      type: 'executors',
      title: 'Executors',
      definition: '',
      icon: '',
      iconTitle: '',
      users: this.permissions.executors,
      emptyLabel: 'None added.',
      placeholder: 'Add user or group...',
      editable: true,
    },
  ];

  ngOnInit() {
    this.generate();
  }

  generate() {
    if (this.type === 'device') {
      this.PERMISSIONS = [
        {
          type: 'owners',
          title: 'Owners',
          definition:
            this.workflow === 'wizard'
              ? "Who should be the owners of this device? They will have full control, including changing its configuration and running tests. You can add users (e.g., 'derekchen') or groups (e.g., 'gmm-prebuild')."
              : "Owners have full control over the device, including changing its configuration and executing tests. Can be a user (e.g., 'derekchen') or a group (e.g., 'gmm-prebuild').",
          icon: '',
          iconTitle: '',
          users: this.permissions.owners,
          emptyLabel: 'None added.',
          placeholder: 'Add user or group...',
          editable: true,
        },
        {
          type: 'executors',
          title: 'Executors',
          definition:
            this.workflow === 'wizard'
              ? 'Besides the owners, who else should have permission to run tests?'
              : 'Executors can only execute tests on the device.',
          icon: '',
          iconTitle: '',
          users: this.permissions.executors,
          emptyLabel: 'None added.',
          placeholder: 'Add user or group...',
          editable: true,
        },
      ];
    }

    if (this.type === 'host') {
      this.PERMISSIONS = [
        {
          type: 'owners',
          title:
            this.workflow === 'wizard'
              ? 'Device Owners (Locked)'
              : 'Device Owners (Must be the same as host admins)',
          definition: '',
          icon: this.workflow === 'wizard' ? 'help_outline' : 'lock',
          iconTitle:
            this.workflow === 'wizard'
              ? 'Device owners must be the same as Host Admins in this configuration.'
              : 'Device owners must be the same as the Host Admins and are managed in the Host Permissions section.',
          users: this.permissions.owners,
          placeholder: '',
          emptyLabel: 'Matches Host Admins.',
          editable: false,
        },
        {
          type: 'executors',
          title: 'Device Executors',
          definition: '',
          icon: this.workflow === 'wizard' ? '' : 'help_outline',
          iconTitle:
            this.workflow === 'wizard'
              ? ''
              : 'Executors can run tests on devices but cannot change their configuration.',
          emptyLabel: 'None added.',
          placeholder: 'Add executors...',
          users: this.permissions.executors,
          editable: this.uiStatus.editability?.editable || false,
        },
      ];
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['permissions']) {
      this.PERMISSIONS[0].users = changes['permissions'].currentValue.owners;
      this.PERMISSIONS[1].users = changes['permissions'].currentValue.executors;
    }
  }
}
