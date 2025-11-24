import {SelectionModel} from '@angular/cdk/collections';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnChanges,
  OnInit,
  signal,
  SimpleChanges,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTableDataSource, MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';
import {delay} from 'rxjs/operators';
import {HealthState} from '../../../../core/models/device_overview';
import {
  DaemonServerStatus,
  DeviceSummary,
  HostConnectivityStatus,
  LabServerActivity,
  type HostOverview,
} from '../../../../core/models/host_overview';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
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
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTooltipModule,
    RouterLink,
    OverviewPage,
    InfoCard,
  ],
})
export class HostOverviewPage implements OnInit, OnChanges {
  private readonly dialog = inject(MatDialog);
  private readonly hostService = inject(HOST_SERVICE);
  readonly objectUtils = objectUtils;
  readonly dateUtils = dateUtils;
  @Input({required: true}) host!: HostOverview;

  deviceDataSource = new MatTableDataSource<DeviceSummary>();
  selection = new SelectionModel<DeviceSummary>(true, []);
  deviceFilterInput = '';
  deviceFilterValue = '';
  isEditingFlags = false;
  editedFlags = '';
  isDeviceLoading = signal(false);
  isSavingFlags = signal(false);

  displayedColumns: string[] = [
    'select',
    'id',
    'health',
    'type',
    'status',
    'label',
    'requiredDims',
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
    this.loadDevices();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['host']) {
      this.selection.clear();
      this.loadDevices();
    }
  }

  loadDevices() {
    this.isDeviceLoading.set(true);
    this.hostService
      .getHostDeviceSummaries(this.host.hostName)
      .pipe(delay(2000)) // TODO: Remove this delay after the real implementation is done.
      .subscribe({
        next: (devices) => {
          this.deviceDataSource.data = devices;
          this.isDeviceLoading.set(false);
        },
        error: (err) => {
          console.error('Failed to load devices:', err);
          this.isDeviceLoading.set(false);
        },
      });
  }

  applyDeviceFilter() {
    this.deviceFilterValue = this.deviceFilterInput.trim().toLowerCase();
    this.deviceDataSource.filter = this.deviceFilterValue;
  }

  clearDeviceFilter() {
    this.deviceFilterInput = '';
    this.applyDeviceFilter();
  }

  isAllSelected(): boolean {
    if (this.deviceDataSource.filteredData.length === 0) {
      return false;
    }
    return this.deviceDataSource.filteredData.every((row) =>
      this.selection.isSelected(row),
    );
  }

  isPartiallySelected(): boolean {
    const hasSelection = this.deviceDataSource.filteredData.some((row) =>
      this.selection.isSelected(row),
    );
    return hasSelection && !this.isAllSelected();
  }

  toggleAllRows() {
    if (this.isAllSelected()) {
      this.selection.deselect(...this.deviceDataSource.filteredData);
    } else {
      this.selection.select(...this.deviceDataSource.filteredData);
    }
  }

  checkboxLabel(row?: DeviceSummary): string {
    if (!row) {
      return `${this.isAllSelected() ? 'deselect' : 'select'} all`;
    }
    return `${this.selection.isSelected(row) ? 'deselect' : 'select'} row ${
      row.id
    }`;
  }

  decommissionMissingCount(): number {
    return this.selection.selected.filter(
      (device) => device.deviceStatus.status === 'MISSING',
    ).length;
  }

  isTypeAbnormal(types: Array<{type: string; isAbnormal: boolean}>): boolean {
    return types.some((t) => t.isAbnormal);
  }

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
    if (this.editedFlags === this.host.labServer.passThroughFlags) {
      this.isEditingFlags = false;
      return;
    }

    this.isSavingFlags.set(true);
    this.hostService
      .updatePassThroughFlags(this.host.hostName, this.editedFlags)
      .pipe(delay(2000)) // TODO: Remove this delay after the real implementation is done.
      .subscribe({
        next: () => {
          this.host.labServer.passThroughFlags = this.editedFlags;
          this.isEditingFlags = false;
          this.isSavingFlags.set(false);
          this.showRestartDialog();
        },
        error: (err) => {
          console.error('Failed to save flags:', err);
          alert(`Failed to save flags: ${err.message}`);
          this.isSavingFlags.set(false);
        },
      });
  }

  showRestartDialog() {
    const dialogData = {
      title: 'Restart Required',
      content: `Pass through flags for ${this.host.hostName} have been updated. A Lab Server restart is required for changes to take effect. Restart now?`,
      type: 'warning',
      primaryButtonLabel: 'Restart Now',
      secondaryButtonLabel: 'Later',
    };

    const restartDialogRef = this.dialog.open(ConfirmDialog, {
      data: dialogData,
      disableClose: true,
    });

    restartDialogRef.afterClosed().subscribe((result) => {
      if (result === 'primary') {
        // TODO: Implement restart lab server logic.
        alert('Restarting lab server');
      }
    });
  }
}
