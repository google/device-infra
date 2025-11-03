import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import type {
  HostPermissions,
  PartStatus,
} from '../../../../../../core/models/host_config_models';
import {EntryChip} from '../../../../../../shared/components/config_common/entry_chip/entry_chip';

interface HostPermissionsUiStatus {
  hostAdmins: PartStatus;
  sshAccess: PartStatus;
}

/**
 * Component for displaying the host permissions in the host configuration
 * workflow.
 */
@Component({
  selector: 'app-host-permissions',
  standalone: true,
  templateUrl: './host_permissions.ng.html',
  styleUrl: './host_permissions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, EntryChip],
})
export class HostPermissionList implements OnInit {
  @Input() workflow: 'wizard' | 'settings' = 'wizard';
  @Input() uiStatus: HostPermissionsUiStatus = {
    hostAdmins: {visible: true, editability: {editable: true}},
    sshAccess: {visible: true, editability: {editable: true}},
  };

  @Input() permissions: HostPermissions = {
    hostAdmins: [],
    sshAccess: [],
  };
  @Output() readonly permissionsChange = new EventEmitter<string[]>();

  ngOnInit() {}
}
