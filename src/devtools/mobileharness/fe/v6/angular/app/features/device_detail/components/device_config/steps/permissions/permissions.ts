import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  input,
  Output,
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
export class Permissions {
  readonly type = input<'device' | 'host'>('device');
  readonly workflow = input<'wizard' | 'settings'>('wizard');
  readonly uiStatus = input<PartStatus>({
    visible: true,
    editability: {
      editable: true,
    },
  });

  readonly title = input<string>('');
  readonly description = input<string>('');
  readonly hostTooltipText = input<string>('');

  readonly permissions = input<PermissionInfo>({
    owners: [],
    executors: [],
  });
  @Output() readonly permissionsChange = new EventEmitter<PermissionInfo>();

  readonly PERMISSIONS = computed(() => {
    const type = this.type();
    const workflow = this.workflow();
    const permissions = this.permissions();
    const uiStatus = this.uiStatus();
    const editable = uiStatus.editability?.editable ?? false;

    if (type === 'device') {
      return [
        {
          type: 'owners',
          title: 'Owners',
          definition:
            workflow === 'wizard'
              ? "Who should be the owners of this device? They will have full control, including changing its configuration and running tests. You can add users (e.g., 'derekchen') or groups (e.g., 'gmm-prebuild')."
              : "Owners have full control over the device, including changing its configuration and executing tests. Can be a user (e.g., 'derekchen') or a group (e.g., 'gmm-prebuild').",
          icon: '',
          iconTitle: '',
          users: permissions.owners || [],
          emptyLabel: 'None added.',
          placeholder: 'Add user or group...',
          editable,
        },
        {
          type: 'executors',
          title: 'Executors',
          definition:
            workflow === 'wizard'
              ? 'Besides the owners, who else should have permission to run tests?'
              : 'Executors can only execute tests on the device.',
          icon: '',
          iconTitle: '',
          users: permissions.executors || [],
          emptyLabel: 'None added.',
          placeholder: 'Add user or group...',
          editable,
        },
      ];
    }

    if (type === 'host') {
      return [
        {
          type: 'owners',
          title:
            workflow === 'wizard'
              ? 'Device Owners (Locked)'
              : 'Device Owners (Must be the same as host admins)',
          definition: '',
          icon: workflow === 'wizard' ? 'help_outline' : 'lock',
          iconTitle:
            workflow === 'wizard'
              ? 'Device owners must be the same as Host Admins in this configuration.'
              : 'Device owners must be the same as the Host Admins and are managed in the Host Permissions section.',
          users: permissions.owners || [],
          placeholder: '',
          emptyLabel: 'Matches Host Admins.',
          editable: false,
        },
        {
          type: 'executors',
          title: 'Device Executors',
          definition: '',
          icon: workflow === 'wizard' ? '' : 'help_outline',
          iconTitle:
            workflow === 'wizard'
              ? ''
              : 'Executors can run tests on devices but cannot change their configuration.',
          emptyLabel: 'None added.',
          placeholder: 'Add executors...',
          users: permissions.executors || [],
          editable,
        },
      ];
    }
    return [];
  });
}
