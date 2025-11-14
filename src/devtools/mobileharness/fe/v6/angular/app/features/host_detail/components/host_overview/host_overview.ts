import {SelectionModel} from '@angular/cdk/collections';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatMenuModule} from '@angular/material/menu';
import {MatTableDataSource, MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';
import {HealthState} from '../../../../core/models/device_overview';
import {
  DaemonServerStatus,
  DeviceSummary,
  HostConnectivityStatus,
  LabServerActivity,
  type HostOverview,
} from '../../../../core/models/host_overview';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  NavItem,
  OverviewPage,
} from '../../../../shared/components/overview_page/overview_page';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {objectUtils} from '../../../../shared/utils/object_utils';

/**
 * Component for displaying an overview of a host, including its basic
 * information, lab server status, devices, daemon server status, and host
 * properties.
 */
@Component({
  selector: 'app-host-overview-page',
  standalone: true,
  templateUrl: './host_overview.ng.html',
  styleUrl: './host_overview.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatTableModule,
    MatTooltipModule,
    RouterLink,
    OverviewPage,
    InfoCard,
  ],
})
export class HostOverviewPage implements OnInit, OnChanges {
  private readonly hostService = inject(HOST_SERVICE);
  readonly objectUtils = objectUtils;
  readonly dateUtils = dateUtils;
  @Input({required: true}) host!: HostOverview;

  deviceDataSource = new MatTableDataSource<DeviceSummary>();
  selection = new SelectionModel<DeviceSummary>(true, []);
  deviceFilter = '';
  isEditingFlags = false;
  editedFlags = '';

  displayedColumns: string[] = [
    'select',
    'id',
    'health',
    'type',
    'status',
    'label',
    'required_dims',
    'model',
    'version',
    'actions',
  ];

  navList: NavItem[] = [
    {
      id: 'overview',
      label: 'Overview',
    },
    {
      id: 'lab-server',
      label: 'Lab Server',
    },
    {
      id: 'device-list',
      label: 'Devices',
    },
    {
      id: 'daemon-server',
      label: 'Daemon Server',
    },
    {
      id: 'host-properties',
      label: 'Host Properties',
    },
  ];

  ngOnInit() {
    this.hostService
      .getHostDeviceSummaries(this.host.hostName)
      .subscribe((devices) => {
        this.deviceDataSource.data = devices;
      });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['host']) {
      this.selection.clear();
      this.hostService
        .getHostDeviceSummaries(this.host.hostName)
        .subscribe((devices) => {
          this.deviceDataSource.data = devices;
        });
    }
  }

  applyDeviceFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.deviceDataSource.filter = filterValue.trim().toLowerCase();
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.deviceDataSource.data.length;
    return numSelected === numRows;
  }

  toggleAllRows() {
    if (this.isAllSelected()) {
      this.selection.clear();
      return;
    }
    this.selection.select(...this.deviceDataSource.data);
  }

  checkboxLabel(row?: DeviceSummary): string {
    if (!row) {
      return `${this.isAllSelected() ? 'deselect' : 'select'} all`;
    }
    return `${this.selection.isSelected(row) ? 'deselect' : 'select'} row ${
      row.id
    }`;
  }

  isTypeAbnormal(types: Array<{type: string; isAbnormal: boolean}>): boolean {
    return types.some((t) => t.isAbnormal);
  }

  // filterDevices() {
  //   if (!this.deviceFilter) {
  //     this.filteredDevices = this.host.devices;
  //     return;
  //   }
  //   const filter = this.deviceFilter.toLowerCase();
  //   this.filteredDevices = this.host.devices.filter(
  //     (device) =>
  //       device.id.toLowerCase().includes(filter) ||
  //       device.status.toLowerCase().includes(filter) ||
  //       device.label.toLowerCase().includes(filter) ||
  //       device.required_dims.toLowerCase().includes(filter) ||
  //       device.model.toLowerCase().includes(filter) ||
  //       device.version.toLowerCase().includes(filter) ||
  //       device.types.some((type) => type.toLowerCase().includes(filter)),
  //   );
  // }

  // isTypeAbnormal(types: readonly string[]): boolean {
  //   return types.some((t) =>
  //     /failed|abnormal|disconnected|offline|unauthorized|fastboot/i.test(t),
  //   );
  // }

  getFirstType(types: Array<{type: string; isAbnormal: boolean}>): string {
    if (types.length === 0) return '';
    const abnormalTypes = types.filter((t) => t.isAbnormal);
    return abnormalTypes.length > 0 ? abnormalTypes[0].type : types[0].type;
  }

  getRemainingTypeCount(
    types: Array<{type: string; isAbnormal: boolean}>,
  ): number {
    return types.length > 1 ? types.length - 1 : 0;
  }

  getDeviceTypesString(
    types: Array<{type: string; isAbnormal: boolean}>,
  ): string {
    return types.map((t) => t.type).join(', ');
  }

  getStatusSemantic(status: HostConnectivityStatus | DaemonServerStatus) {
    const result = {
      icon: 'help_outline',
      colorClass: 'text-gray-700',
      text: status.title,
      duration: '',
      tooltip: status.tooltip,
    };

    switch (status.state) {
      case 'RUNNING':
        result.icon = 'check_circle';
        result.colorClass = 'text-green-600';
        break;
      case 'MISSING':
        result.icon = 'error';
        result.colorClass = 'text-red-600';
        if (status.missingStartTime) {
          result.duration = this.dateUtils.formatTimeAgo(
            status.missingStartTime,
          );
        }
        break;
      default:
        break;
    }
    return result;
  }

  getHealthSemantic(state: HealthState) {
    switch (state) {
      case 'IN_SERVICE_IDLE':
        return {
          icon: 'check_circle',
          colorClass: 'text-green-600',
          text: 'In Service (Idle)',
        };
      case 'IN_SERVICE_BUSY':
        return {
          icon: 'sync',
          colorClass: 'text-blue-600',
          text: 'In Service (Busy)',
        };
      case 'OUT_OF_SERVICE_RECOVERING':
        return {
          icon: 'autorenew',
          colorClass: 'text-amber-600',
          text: 'Out of Service (Recovering)',
        };
      case 'OUT_OF_SERVICE_TEMP_MAINT':
        return {
          icon: 'warning',
          colorClass: 'text-amber-600',
          text: 'Out of Service (Temp Maint)',
        };
      case 'OUT_OF_SERVICE_NEEDS_FIXING':
        return {
          icon: 'error',
          colorClass: 'text-red-600',
          text: 'Out of Service (Needs Fixing)',
        };
      default:
        return {
          icon: 'help_outline',
          colorClass: 'text-gray-700',
          text: 'Unknown',
        };
    }
  }

  getLabActivitySemantic(activity: LabServerActivity) {
    const result = {
      icon: 'help_outline',
      colorClass: 'text-gray-700',
      text: activity.title,
      isSpinning: false,
      tooltip: activity.tooltip,
    };
    switch (activity.state) {
      case 'STARTED':
        result.icon = 'check';
        result.colorClass = 'text-green-600';
        break;
      case 'STARTED_BUT_DISCONNECTED':
        result.icon = 'warning';
        result.colorClass = 'text-amber-600';
        break;
      case 'STARTING':
        result.icon = 'sync';
        result.isSpinning = true;
        result.colorClass = 'text-blue-600';
        break;
      case 'ERROR':
        result.icon = 'error_outline';
        result.colorClass = 'text-red-600';
        break;
      case 'DRAINING':
        result.icon = 'timelapse';
        result.colorClass = 'text-amber-600';
        break;
      case 'DRAINED':
        result.icon = 'hourglass_empty';
        result.colorClass = 'text-gray-600';
        break;
      case 'STOPPING':
        result.icon = 'sync';
        result.isSpinning = true;
        result.colorClass = 'text-blue-600';
        break;
      case 'STOPPED':
        result.icon = 'stop_circle';
        result.colorClass = 'text-gray-700';
        break;
      default:
        break;
    }
    return result;
  }

  startEditFlags() {
    this.editedFlags = this.host.labServer.passThroughFlags;
    this.isEditingFlags = true;
  }

  cancelEditFlags() {
    this.isEditingFlags = false;
  }

  saveFlags() {
    this.hostService
      .updatePassThroughFlags(this.host.hostName, this.editedFlags)
      .subscribe({
        next: () => {
          this.host.labServer.passThroughFlags = this.editedFlags;
          this.isEditingFlags = false;
          alert('Flags saved!');
        },
        error: (err) => {
          console.error('Failed to save flags:', err);
          alert(`Failed to save flags: ${err.message}`);
        },
      });
  }
}
