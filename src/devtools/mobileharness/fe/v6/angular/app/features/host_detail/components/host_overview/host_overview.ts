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
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
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
import {
  HealthState,
  type DeviceDimension,
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
import {OverflowList} from '../../../../shared/components/overflow_list/overflow_list';
import {
  NavItem,
  OverviewPage,
} from '../../../../shared/components/overview_page/overview_page';
import {
  SearchableListOverlayComponent,
  SearchableListOverlayData,
} from '../../../../shared/components/searchable_list_overlay/searchable_list_overlay';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {objectUtils} from '../../../../shared/utils/object_utils';

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
    OverviewPage,
    InfoCard,
    SearchableListOverlayComponent,
    OverflowList,
  ],
})
export class HostOverviewPage implements OnInit, OnChanges {
  private readonly dialog = inject(MatDialog);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

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
  expandedElement: DeviceSummary | null = null;

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

  subDeviceDisplayedColumns: string[] = ['id', 'type', 'dimensions'];

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
      .pipe(delay(2000), takeUntilDestroyed(this.destroyRef))
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

  toggleRow(element: DeviceSummary) {
    this.expandedElement = this.expandedElement === element ? null : element;
  }

  isTestbed(element: DeviceSummary): boolean {
    return (
      element.types.some((t) => t.type === 'TestbedDevice') &&
      !!element.subDevices &&
      element.subDevices.length > 0
    );
  }

  isTypeAbnormal(types: Array<{type: string; isAbnormal: boolean}>): boolean {
    return types.some((t) => t.isAbnormal);
  }

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

  getFirstType(types: Array<{type: string; isAbnormal: boolean}>): string {
    if (types.length === 0) return '';
    const abnormalTypes = types.filter((t) => t.isAbnormal);
    return abnormalTypes.length > 0 ? abnormalTypes[0].type : types[0].type;
  }

  getRemainingTypeCount(
    types: Array<{type: string; isAbnormal: boolean}>,
  ): number {
    return Math.max(0, types.length - 1);
  }

  getDeviceTypesString(
    types: Array<{type: string; isAbnormal: boolean}>,
  ): string {
    return types.map((t) => t.type).join(', ');
  }

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

  getHealthSemantic(state: HealthState) {
    return HEALTH_SEMANTIC_MAP[state] || DEFAULT_HEALTH_SEMANTIC;
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
      .pipe(delay(2000), takeUntilDestroyed(this.destroyRef))
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
}
