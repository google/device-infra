import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';
import {
  DaemonServerStatus,
  DeviceSummary,
  HostConnectivityStatus,
  LabServerActivity,
  type HostOverview,
} from '../../../../core/models/host_overview';
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
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatTooltipModule,
    RouterLink,
    OverviewPage,
    InfoCard,
  ],
})
export class HostOverviewPage implements OnInit, OnChanges {
  readonly objectUtils = objectUtils;
  readonly dateUtils = dateUtils;
  @Input({required: true}) host!: HostOverview;

  deviceFilter = '';
  filteredDevices: readonly DeviceSummary[] = [];
  isEditingFlags = false;
  editedFlags = '';

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
    // this.filterDevices();
  }

  ngOnChanges(changes: SimpleChanges) {
    // if (changes['host']) {
    //   this.filterDevices();
    // }
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

  getFirstType(types: readonly string[]): string {
    if (types.length === 0) return '';
    const abnormalTypes = types.filter((t) =>
      /failed|abnormal|disconnected|offline|unauthorized|fastboot/i.test(t),
    );
    return abnormalTypes.length > 0 ? abnormalTypes[0] : types[0];
  }

  getRemainingTypeCount(types: readonly string[]): number {
    return types.length > 1 ? types.length - 1 : 0;
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
    // In a real application, you would send this.editedFlags to a service.
    // For this prototype, we just log it and switch the view.
    console.log('New flags saved:', this.editedFlags);
    this.host.labServer.passThroughFlags = this.editedFlags;
    this.isEditingFlags = false;
    alert('Flags saved!');
  }
}
