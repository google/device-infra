import {SelectionModel} from '@angular/cdk/collections';
import {
  CdkOverlayOrigin,
  ConnectedPosition,
  OverlayModule,
} from '@angular/cdk/overlay';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  Input,
  OnChanges,
  OnInit,
  signal,
  SimpleChanges,
} from '@angular/core';
import {takeUntilDestroyed, toSignal} from '@angular/core/rxjs-interop';
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
import {delay, map, tap} from 'rxjs/operators';

import {
  HealthState,
  type DeviceDimension,
  type SubDeviceInfo,
} from '../../../../core/models/device_overview';
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
  MasterDetailLayout,
  NavItem,
} from '../../../../shared/components/master_detail_layout/master_detail_layout';
import {OverflowList} from '../../../../shared/components/overflow_list/overflow_list';
import {
  SearchableListOverlayComponent,
  SearchableListOverlayData,
} from '../../../../shared/components/searchable_list_overlay/searchable_list_overlay';
import {RemoteControlService} from '../../../../shared/services/remote_control_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {objectUtils} from '../../../../shared/utils/object_utils';
import {DecommissionContent} from './decommission_content/decommission_content';

const HEALTH_SEMANTIC_MAP: Record<
  string,
  {icon: string; colorClass: string; text: string}
> = {
  'IN_SERVICE_IDLE': {
    icon: 'check_circle',
    colorClass: 'text-green-600',
    text: 'In Service (Idle)',
  },
  'IN_SERVICE_BUSY': {
    icon: 'sync',
    colorClass: 'text-blue-600',
    text: 'In Service (Busy)',
  },
  'OUT_OF_SERVICE_RECOVERING': {
    icon: 'autorenew',
    colorClass: 'text-amber-600',
    text: 'Out of Service (Recovering)',
  },
  'OUT_OF_SERVICE_TEMP_MAINT': {
    icon: 'warning',
    colorClass: 'text-amber-600',
    text: 'Out of Service (Temp Maint)',
  },
  'OUT_OF_SERVICE_NEEDS_FIXING': {
    icon: 'error',
    colorClass: 'text-red-600',
    text: 'Out of Service (Needs Fixing)',
  },
};

const DEFAULT_HEALTH_SEMANTIC = {
  icon: 'help_outline',
  colorClass: 'text-gray-700',
  text: 'Unknown',
};

const LAB_ACTIVITY_SEMANTIC_MAP: Record<
  string,
  {icon: string; colorClass: string; isSpinning?: boolean}
> = {
  'STARTED': {icon: 'check', colorClass: 'text-green-600'},
  'STARTED_BUT_DISCONNECTED': {icon: 'warning', colorClass: 'text-amber-600'},
  'STARTING': {icon: 'sync', colorClass: 'text-blue-600', isSpinning: true},
  'ERROR': {icon: 'error_outline', colorClass: 'text-red-600'},
  'DRAINING': {icon: 'timelapse', colorClass: 'text-amber-600'},
  'DRAINED': {icon: 'hourglass_empty', colorClass: 'text-gray-600'},
  'STOPPING': {icon: 'sync', colorClass: 'text-blue-600', isSpinning: true},
  'STOPPED': {icon: 'stop_circle', colorClass: 'text-gray-700'},
};

const STATUS_SEMANTIC_MAP: Record<string, {icon: string; colorClass: string}> =
  {
    'RUNNING': {icon: 'check_circle', colorClass: 'text-green-600'},
    'MISSING': {icon: 'error', colorClass: 'text-red-600'},
  };

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
    OverlayModule,
    RouterLink,
    MasterDetailLayout,
    InfoCard,
    SearchableListOverlayComponent,
    OverflowList,
    DecommissionContent,
  ],
})
export class HostOverviewPage implements OnInit, OnChanges {
  private readonly dialog = inject(MatDialog);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly remoteControlService = inject(RemoteControlService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(SnackBarService);

  readonly objectUtils = objectUtils;
  readonly dateUtils = dateUtils;
  @Input({required: true}) host!: HostOverview;

  isEditingFlags = signal(false);
  editedFlags = '';
  isSavingFlags = signal(false);

  deviceDataSource = new MatTableDataSource<DeviceSummary>();
  selection = new SelectionModel<DeviceSummary>(true, []);

  decommissionMissingCount = toSignal(
    this.selection.changed.pipe(
      map(
        () =>
          this.selection.selected.filter(
            (device) => device.deviceStatus.status === 'MISSING',
          ).length,
      ),
    ),
    {initialValue: 0},
  );

  deviceFilterInput = '';
  deviceFilterValue = '';
  isDeviceLoading = signal(false);
  expandedElement = signal<DeviceSummary | null>(null);

  // --- Dimensions Overlay State ---
  activeOverlay = signal<{
    origin: CdkOverlayOrigin;
    data: SearchableListOverlayData;
  } | null>(null);

  // Timer to delay hiding the overlay, allowing user to move mouse into it
  private overlayHideTimer: ReturnType<typeof setTimeout> | null = null;

  readonly overlayPositions: ConnectedPosition[] = [
    // Priority 1: Right-aligned below
    {
      originX: 'end',
      originY: 'bottom',
      overlayX: 'end',
      overlayY: 'top',
      offsetY: 8,
    },
    // Priority 1: Right-aligned above
    {
      originX: 'end',
      originY: 'top',
      overlayX: 'end',
      overlayY: 'bottom',
      offsetY: -8,
    },
    // Priority 2: Left-aligned below
    {
      originX: 'start',
      originY: 'bottom',
      overlayX: 'start',
      overlayY: 'top',
      offsetY: 8,
    },
    // Priority 2: Left-aligned above
    {
      originX: 'start',
      originY: 'top',
      overlayX: 'start',
      overlayY: 'bottom',
      offsetY: -8,
    },
    // Fallback: Centered below
    {
      originX: 'center',
      originY: 'bottom',
      overlayX: 'center',
      overlayY: 'top',
      offsetY: 8,
    },
    // Fallback: Centered above
    {
      originX: 'center',
      originY: 'top',
      overlayX: 'center',
      overlayY: 'bottom',
      offsetY: -8,
    },
    // Fallback: Open to the left of the origin (button on the right of overlay)
    {
      originX: 'start',
      originY: 'bottom',
      overlayX: 'end',
      overlayY: 'top',
      offsetY: 8,
    },
    // Fallback: Open to the right of the origin (button on the left of overlay)
    {
      originX: 'end',
      originY: 'bottom',
      overlayX: 'start',
      overlayY: 'top',
      offsetY: 8,
    },
  ];

  displayedColumns: string[] = [
    'select',
    'expand',
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

  subDeviceDisplayedColumns: string[] = [
    'id',
    'type',
    'model',
    'version',
    'battery',
    'wifi',
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

  // --- Lab Server Card ---
  getStatusSemantic(status: HostConnectivityStatus | DaemonServerStatus) {
    const config = STATUS_SEMANTIC_MAP[status.state];
    const duration =
      status.state === 'MISSING' && status.missingStartTime
        ? this.dateUtils.formatTimeAgo(status.missingStartTime)
        : '';

    return {
      icon: config?.icon ?? 'help_outline',
      colorClass: config?.colorClass ?? 'text-gray-700',
      text: status.title,
      duration,
      tooltip: status.tooltip,
    };
  }

  getLabActivitySemantic(activity: LabServerActivity) {
    const config = LAB_ACTIVITY_SEMANTIC_MAP[activity.state];
    return {
      icon: config?.icon ?? 'help_outline',
      colorClass: config?.colorClass ?? 'text-gray-700',
      text: activity.title,
      isSpinning: config?.isSpinning ?? false,
      tooltip: activity.tooltip,
    };
  }

  // Flags editing
  startEditFlags() {
    this.editedFlags = this.host.labServer.passThroughFlags;
    this.isEditingFlags.set(true);
  }

  cancelEditFlags() {
    this.isEditingFlags.set(false);
  }

  saveFlags() {
    if (this.editedFlags === this.host.labServer.passThroughFlags) {
      this.isEditingFlags.set(false);
      return;
    }

    this.isSavingFlags.set(true);
    this.hostService
      .updatePassThroughFlags(this.host.hostName, this.editedFlags)
      .pipe(delay(2000), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.host.labServer.passThroughFlags = this.editedFlags;
          this.isEditingFlags.set(false);
          this.isSavingFlags.set(false);
          this.showRestartDialog();
        },
        error: (err) => {
          this.snackBar.showError(`Failed to save flags: ${err.message}`);
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

    restartDialogRef
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        if (result === 'primary') {
          // TODO: Implement restart lab server logic.
          alert('Restarting lab server');
        }
      });
  }

  // --- Devices Card ---
  loadDevices() {
    this.isDeviceLoading.set(true);
    this.hostService
      .getHostDeviceSummaries(this.host.hostName)
      .pipe(delay(2000), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (devices) => {
          this.deviceDataSource.data = devices;
          this.isDeviceLoading.set(false);
        },
        error: (err) => {
          this.snackBar.showError(`Failed to load devices: ${err.message}`);
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

  // device table actions - Decommission missing devices
  decommissionMissing() {
    const missingDevices = this.selection.selected.filter(
      (device) => device.deviceStatus.status === 'MISSING',
    );

    if (missingDevices.length === 0) {
      return;
    }

    const dialogData = {
      title: `Decommission ${missingDevices.length} Device${
        missingDevices.length > 1 ? 's' : ''
      }?`,
      contentComponent: DecommissionContent,
      contentComponentInputs: {devices: missingDevices},
      type: 'error',
      primaryButtonLabel: 'Decommission',
      secondaryButtonLabel: 'Cancel',
      onConfirm: () => this.decommissionDevices(missingDevices),
    };

    this.dialog.open(ConfirmDialog, {
      panelClass: 'confirm-dialog-panel',
      data: dialogData,
      disableClose: true,
    });
  }

  private decommissionDevices(devices: DeviceSummary[]) {
    return this.hostService
      .decommissionMissingDevices(
        this.host.hostName,
        devices.map((d) => d.id),
      )
      .pipe(
        delay(1000),
        tap(() => {
          const decommissionedIds = new Set(devices.map((d) => d.id));
          this.deviceDataSource.data = this.deviceDataSource.data.filter(
            (d) => !decommissionedIds.has(d.id),
          );
          this.selection.clear();
          this.snackBar.showSuccess(
            `${devices.length} devices decommissioned successfully.`,
          );
        }),
      );
  }

  // device table actions -  Multi-device remote control
  startMultiRemoteControl() {
    this.remoteControlService.startRemoteControl(
      this.host.hostName,
      this.selection.selected,
    );
  }

  startSubDeviceRemoteControl(subDevice: SubDeviceInfo) {
    // Construct a temporary DeviceSummary from SubDeviceInfo
    const deviceSummary: DeviceSummary = {
      id: subDevice.id,
      healthState: {
        health: 'UNKNOWN', // Default for sub-devices if unknown
        title: 'Unknown',
        tooltip: '',
      },
      types: subDevice.types,
      deviceStatus: {
        status: 'UNKNOWN',
        isCritical: false,
      },
      label: '',
      requiredDims: '',
      model: subDevice.model || '',
      version: subDevice.version || '',
      subDevices: [],
    };

    this.remoteControlService.startRemoteControl(this.host.hostName, [
      deviceSummary,
    ]);
  }

  toggleRow(element: DeviceSummary) {
    this.expandedElement.set(
      this.expandedElement() === element ? null : element,
    );
  }

  isTestbed(element: DeviceSummary): boolean {
    return (
      element.types.some((t) => t.type === 'TestbedDevice') &&
      !!element.subDevices &&
      element.subDevices.length > 0
    );
  }

  // Health Column
  getHealthSemantic(state: HealthState) {
    return HEALTH_SEMANTIC_MAP[state] || DEFAULT_HEALTH_SEMANTIC;
  }

  getWifiSignalIcon(rssi: number | undefined): string {
    if (rssi === undefined) return '';
    if (rssi >= -67) return 'signal_wifi_4_bar';
    if (rssi >= -75) return 'network_wifi_3_bar';
    if (rssi >= -85) return 'network_wifi_2_bar';
    return 'network_wifi_1_bar';
  }

  getWifiQualityText(rssi: number | undefined): string {
    if (rssi === undefined) return '';
    if (rssi >= -67) return 'Excellent';
    if (rssi >= -75) return 'Good';
    if (rssi >= -85) return 'Okay';
    return 'Weak';
  }

  // Type Column
  isTypeAbnormal(types: Array<{type: string; isAbnormal: boolean}>): boolean {
    return types.some((t) => t.isAbnormal);
  }

  getFirstType(types: Array<{type: string; isAbnormal: boolean}>): string {
    if (types.length === 0) return '';
    const abnormalTypes = types.filter((t) => t.isAbnormal);
    return abnormalTypes.length > 0 ? abnormalTypes[0].type : types[0].type;
  }

  // Dimensions and types hovers
  onDimHover(
    trigger: CdkOverlayOrigin,
    subDeviceId: string,
    dimensions: DeviceDimension[],
  ) {
    this.clearHideTimer();
    // Only update if it's a different overlay
    if (
      this.activeOverlay()?.data.subtitle !== subDeviceId ||
      this.activeOverlay()?.data.title !== 'Dimensions'
    ) {
      this.activeOverlay.set({
        origin: trigger,
        data: {
          subtitle: subDeviceId,
          type: 'key-value',
          items: dimensions.map((d) => ({
            name: d.name ?? '',
            value: d.value ?? '',
          })),
          title: 'Dimensions',
        },
      });
    }
  }

  onTypeHover(
    trigger: CdkOverlayOrigin,
    subDeviceId: string,
    types: Array<{type: string; isAbnormal: boolean}>,
  ) {
    this.clearHideTimer();
    const chipItems = types.map((t) => ({
      label: t.type,
      cssClass: t.isAbnormal ? 'type-chip-abnormal' : 'type-chip-normal',
    }));
    // Only update if it's a different overlay
    if (
      this.activeOverlay()?.data.subtitle !== subDeviceId ||
      this.activeOverlay()?.data.title !== 'Device Types'
    ) {
      this.activeOverlay.set({
        origin: trigger,
        data: {
          subtitle: subDeviceId,
          type: 'chip',
          items: chipItems,
          title: 'Device Types',
        },
      });
    }
  }

  handleMouseLeave(event?: MouseEvent) {
    this.scheduleHideOverlay(event?.target as Element);
  }

  onOverlayEnter() {
    this.clearHideTimer();
  }

  closeOverlay() {
    this.clearHideTimer();
    this.activeOverlay.set(null);
  }

  private scheduleHideOverlay(container?: Element | null) {
    this.clearHideTimer();
    this.overlayHideTimer = setTimeout(() => {
      if (
        container?.contains &&
        document.activeElement &&
        container.contains(document.activeElement)
      ) {
        return;
      }
      this.closeOverlay();
    }, 200);
  }

  private clearHideTimer() {
    if (this.overlayHideTimer) {
      clearTimeout(this.overlayHideTimer);
      this.overlayHideTimer = null;
    }
  }
}
