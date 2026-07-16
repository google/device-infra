import {SelectionModel} from '@angular/cdk/collections';
import {
  CdkOverlayOrigin,
  ConnectedPosition,
  OverlayModule,
} from '@angular/cdk/overlay';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  computed,
  DestroyRef,
  inject,
  Input,
  OnChanges,
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

import {map, tap} from 'rxjs/operators';
import {ActionButton} from '../../../../shared/components/action_button/action_button';
import {NavLink} from '../../../../shared/components/nav_link/nav_link';

import {
  ActionBarAction,
  DEVICE_ACTION_UI_CONFIG,
  LAB_SERVER_ACTION_UI_CONFIG,
} from '../../../../core/constants/action_bar_config';
import {ActionButtonState} from '../../../../core/models/action_common';
import {APP_DATA, getLegacyFeUrl} from '../../../../core/models/app_data';
import {DeviceActions} from '../../../../core/models/device_action';
import {
  HealthState,
  type DeviceDimension,
  type SubDeviceInfo,
} from '../../../../core/models/device_overview';
import type {
  HostActions,
  LabServerActions,
} from '../../../../core/models/host_action';

import {
  DaemonServerStatus,
  DeviceSummary,
  DiagnosticLink,
  HostConnectivityStatus,
  LabServerActivity,
  UiLabType,
  type HostOverview,
} from '../../../../core/models/host_overview';

import {EnvUniverseService} from '../../../../core/services/env_universe_service';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
import {DecommissionContent} from '../../../../shared/components/decommission_content/decommission_content';
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
import {useDeviceActions} from '../../../../shared/composables/device_actions';
import {TooltipIfTruncatedDirective} from '../../../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {objectUtils} from '../../../../shared/utils/object_utils';
import {FlagsDialog} from './flags_dialog/flags_dialog';
import {LabServerActionService} from './lab_server_action_service';

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
    MasterDetailLayout,
    InfoCard,
    NavLink,
    SearchableListOverlayComponent,
    OverflowList,
    ActionButton,
    TooltipIfTruncatedDirective,
  ],
  providers: [LabServerActionService],
})
export class HostOverviewPage implements OnChanges {
  private readonly dialog = inject(MatDialog);
  private readonly hostService = inject(HOST_SERVICE);
  protected readonly deviceActions = useDeviceActions();
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(SnackBarService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly envUniverseService = inject(EnvUniverseService);
  private readonly comingSoonService = inject(ComingSoonService);
  private readonly appData = inject(APP_DATA);
  private readonly actionService = inject(LabServerActionService);

  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');

  readonly objectUtils = objectUtils;
  readonly dateUtils = dateUtils;

  readonly isGoogleInternal = this.envUniverseService.isGoogleInternal();
  readonly isGoogle1p = this.envUniverseService.isGoogle1P();

  readonly ActionBarAction = ActionBarAction;
  /**
   * Configurations driving the UI for Device Actions and Lab Server Actions.
   *
   * NOTE: Following the "next-level" refactoring, these configurations separate
   * UI metadata (labels, icons) from component logic. This allows us to use
   * data-driven templates (using @for loops) instead of hardcoding buttons,
   * significantly improving maintainability and reducing HTML duplication.
   */
  protected readonly actionUiConfig = DEVICE_ACTION_UI_CONFIG;
  /**
   * Layout configuration for device actions available in the device list menu.
   * These correspond to actions that can be performed on individual devices.
   */
  protected readonly deviceActionsLayout: Array<keyof DeviceActions> = [
    'configuration',
    'screenshot',
    'logcat',
    'remoteControl',
    'flash',
    'quarantine',
    'decommission',
  ];

  protected readonly labServerActionUiConfig = LAB_SERVER_ACTION_UI_CONFIG;
  protected readonly labServerActionsLayout: Array<
    keyof typeof this.labServerActionUiConfig
  > = ['release', 'restart', 'start', 'stop'];

  @Input({required: true}) host!: HostOverview;
  @Input() actions?: HostActions;

  readonly isAteHost = computed(() => {
    return this.host.uiLabTypes?.includes('ATE') ?? false;
  });

  readonly displayLabTypes = computed(() => {
    return (
      this.host.uiLabTypes?.map((type) => this.mapUiLabTypeToString(type)) ?? []
    );
  });

  readonly isSatelliteLab = computed(() => {
    return this.host.uiLabTypes?.includes('SATELLITE') ?? false;
  });

  private mapUiLabTypeToString(type: UiLabType): string {
    switch (type) {
      case 'CORE':
        return 'Core';
      case 'FUSION':
        return 'Fusion';
      case 'SATELLITE':
        return 'Satellite';
      case 'SLAAS':
        return 'SLaaS';
      case 'ATE':
        return 'ATE';
      case 'RIEMANN_FIELD':
        return 'Riemann Field';
      default:
        return 'Unknown';
    }
  }

  readonly passThroughFlags = this.actionService.passThroughFlags;

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

  readonly isOpeningReleaseDialog = this.actionService.isOpeningReleaseDialog;
  readonly isOpeningUpgrade = this.actionService.isOpeningUpgrade;
  readonly isOpeningRelease = this.actionService.isOpeningRelease;

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
    ...(this.isGoogleInternal ? ['select'] : []),
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

  readonly navList = computed<NavItem[]>(() => {
    const baseItems: NavItem[] = [
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
    ];

    if (this.isGoogle1p) {
      baseItems.push({
        id: 'daemon-server',
        label: 'Daemon Server',
      });
    }

    baseItems.push({
      id: 'host-properties',
      label: 'Host Properties',
    });

    return baseItems;
  });

  private handleHostDataChange() {
    this.selection.clear();
    this.passThroughFlags.set(this.host.labServer.passThroughFlags);
    this.loadDevices();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['host']) {
      this.handleHostDataChange();

      console.log('*** Current host data received by component:', this.host);
      console.log(
        '*** canUpgrade value:',
        this.host.canUpgrade,
        'Type:',
        typeof this.host.canUpgrade,
      );
    }
  }

  // --- Lab Server Card ---

  getDiagnosticLinks(
    category: 'OVERVIEW' | 'LAB_SERVER' | 'DAEMON_SERVER',
  ): DiagnosticLink[] {
    return (
      this.host.diagnosticLinks?.filter((link) => link.category === category) ??
      []
    );
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

  onUpgrade() {
    if (this.host.labServer.actions?.release?.isReady) {
      this.preflightAndOpenRelease({preSelectLatest: true}).subscribe({
        error: () => {},
      });
    } else {
      this.showComingSoonPopup('Release');
    }
  }

  isActionLoading(actionId: keyof LabServerActions): boolean {
    return this.actionService.isActionLoading(actionId);
  }

  onLabServerAction(actionId: keyof LabServerActions) {
    switch (actionId) {
      case 'release':
        this.onRelease();
        break;
      case 'start':
        this.onStart();
        break;
      case 'restart':
        this.onRestart();
        break;
      case 'stop':
        this.onStop();
        break;
      default:
        break;
    }
  }

  onRelease(
    options: {preSelectLatest?: boolean; preSelectCurrent?: boolean} = {},
  ) {
    this.preflightAndOpenRelease(options).subscribe({
      error: () => {},
    });
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
  openFlagsDialog() {
    const dialogRef = this.dialog.open(FlagsDialog, {
      data: {
        hostName: this.host.hostName,
        currentFlags: this.passThroughFlags(),
      },
      width: '72rem',
      maxHeight: '90vh',
      autoFocus: false,
    });

    dialogRef
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        if (
          typeof result === 'string' &&
          result !== 'close' &&
          result !== 'cancel'
        ) {
          this.passThroughFlags.set(result);
          this.host.labServer.passThroughFlags = result;
          this.showReleaseConfirmDialog();
        }
      });
  }

  showReleaseConfirmDialog() {
    const dialogData = {
      title: 'Flags Updated',
      content: `Pass-through flags have been updated successfully. Would you like to perform a release now to apply these changes?`,
      customIcon: 'rocket_launch',
      primaryButtonLabel: 'Release Now',
      secondaryButtonLabel: 'Later',
      onConfirm: () => this.preflightAndOpenRelease(),
    };

    this.dialog.open(ConfirmDialog, {
      data: dialogData,
      panelClass: 'confirm-dialog-panel',
      disableClose: true,
    });
  }

  preflightAndOpenRelease(
    options: {preSelectLatest?: boolean; preSelectCurrent?: boolean} = {},
  ) {
    return this.actionService.preflightAndOpenRelease(this.host, options);
  }

  onStart() {
    this.actionService.start(this.host, {
      onActionUnavailable: this.triggerBackgroundRefresh.bind(this),
    });
  }

  onStop() {
    this.actionService.stop(this.host, {
      onActionUnavailable: this.triggerBackgroundRefresh.bind(this),
    });
  }

  onRestart() {
    this.actionService.restart(this.host, {
      onActionUnavailable: this.triggerBackgroundRefresh.bind(this),
    });
  }

  private triggerBackgroundRefresh() {
    this.snackBar.showInfo('Refreshing page data...');
    this.hostService
      .getHostOverview(this.host.hostName)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.host = data.overviewContent;
          this.handleHostDataChange();
          this.snackBar.showSuccess('Page data refreshed.');
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.snackBar.showError(`Failed to refresh: ${err.message}`);
        },
      });
  }

  // --- Devices Card ---
  loadDevices() {
    this.isDeviceLoading.set(true);
    this.hostService
      .getHostDeviceSummaries(this.host.hostName)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.deviceDataSource.data = response.deviceSummaries;
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
  decommission(devices: DeviceSummary | DeviceSummary[]) {
    const devicesList = Array.isArray(devices) ? devices : [devices];
    const devicesToDecommission = devicesList.filter(
      (device) => device.deviceStatus.status === 'MISSING',
    );

    if (devicesToDecommission.length === 0) {
      return;
    }

    const isSingle = devicesToDecommission.length === 1;
    const title = isSingle
      ? `Decommission Device ${devicesToDecommission[0].id}?`
      : `Decommission ${devicesToDecommission.length} Devices?`;

    const dialogData = {
      title,
      contentComponent: DecommissionContent,
      contentComponentInputs: {
        'deviceIds': devicesToDecommission.map((d) => d.id),
      },
      type: 'error',
      primaryButtonLabel: 'Decommission',
      secondaryButtonLabel: 'Cancel',
      onConfirm: () => this.executeDecommission(devicesToDecommission),
    };

    this.dialog.open(ConfirmDialog, {
      panelClass: 'confirm-dialog-panel',
      data: dialogData,
      disableClose: true,
    });
  }

  private executeDecommission(devices: DeviceSummary[]) {
    return this.hostService
      .decommissionMissingDevices(
        this.host.hostName,
        devices.map((d) => d.id),
      )
      .pipe(
        tap(() => {
          const decommissionedIds = new Set(devices.map((d) => d.id));
          this.deviceDataSource.data = this.deviceDataSource.data.filter(
            (d) => !decommissionedIds.has(d.id),
          );
          this.selection.clear();
          this.snackBar.showSuccess(
            `${devices.length} devices decommissioned successfully.\nIt may take a few minutes to take effect on the UI side.`,
          );
        }),
      );
  }

  /**
   * Shows the "Coming Soon" popup for a given action.
   *
   * NOTE: This method was unified during refactoring to handle device actions,
   * lab server actions, and legacy inline actions consistently. It uses the
   * centralized configurations to derive the correct feature flag, and delegates
   * to ComingSoonService.showForHost with custom contexts ('hostDevicesItem' for
   * row-level device actions, 'hostDevices' for bulk actions or others) to display
   * context-aware messaging and legacy links.
   */
  showComingSoonPopup(actionId: string, element?: DeviceSummary) {
    let feature: ActionBarAction | undefined;

    if (actionId in this.actionUiConfig) {
      feature = this.actionUiConfig[actionId as keyof DeviceActions]?.feature;
    } else if (actionId in this.labServerActionUiConfig) {
      feature =
        this.labServerActionUiConfig[
          actionId as keyof typeof this.labServerActionUiConfig
        ]?.feature;
    } else {
      const legacyMap: Record<string, ActionBarAction> = {
        'Configure': ActionBarAction.DEVICE_CONFIGURATION,
        'Remote Control': ActionBarAction.DEVICE_REMOTE_CONTROL,
        'Decommission': ActionBarAction.DEVICE_DECOMMISSION,
        'Screenshot': ActionBarAction.DEVICE_SCREENSHOT,
        'Flash': ActionBarAction.DEVICE_FLASH,
        'Logcat': ActionBarAction.DEVICE_LOGCAT,
        'Quarantine': ActionBarAction.DEVICE_QUARANTINE,
        'Release': ActionBarAction.HOST_RELEASE,
        'Start': ActionBarAction.HOST_START,
        'Restart': ActionBarAction.HOST_RESTART,
        'Stop': ActionBarAction.HOST_STOP,
      };
      feature = legacyMap[actionId];
    }

    if (feature) {
      this.comingSoonService.showForHost(
        feature,
        this.legacyFeUrl,
        this.host.hostName,
        this.host.ip,
        element ? 'hostDevicesItem' : 'hostDevices',
      );
    } else {
      console.error(
        'showComingSoonPopup - feature is undefined for actionId:',
        actionId,
      );
    }
  }

  isDeviceActionReady(
    devices: DeviceSummary[],
    actionKey: keyof DeviceActions,
  ): boolean {
    return devices.every((d) => {
      if (actionKey === 'flash') {
        return d.actions?.flash?.state?.isReady || false;
      }
      const actions = d.actions as
        | Record<string, {isReady?: boolean}>
        | undefined;
      return actions?.[actionKey]?.isReady || false;
    });
  }

  // device table actions -  Multi-device remote control
  startRemoteControl(devices: DeviceSummary[]) {
    this.deviceActions.startRemoteControl(this.host.hostName, devices);
  }

  startSubDeviceRemoteControl(
    subDevice: SubDeviceInfo,
    parentDevice: DeviceSummary,
  ) {
    this.deviceActions.startRemoteControl(this.host.hostName, parentDevice, {
      isSubDevice: true,
      subDeviceOnly: subDevice,
    });
  }

  toggleRow(element: DeviceSummary) {
    this.expandedElement.set(
      this.expandedElement() === element ? null : element,
    );
  }

  isTestbed(element: DeviceSummary): boolean {
    return (
      (element.types?.some((t) => t.type === 'TestbedDevice') ?? false) &&
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
  isTypeAbnormal(
    types: Array<{type: string; isAbnormal: boolean}> | undefined,
  ): boolean {
    return types?.some((t) => t.isAbnormal) ?? false;
  }

  getFirstType(
    types: Array<{type: string; isAbnormal: boolean}> | undefined,
  ): string {
    if (!types || types.length === 0) return '';
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
    types: Array<{type: string; isAbnormal: boolean}> | undefined,
  ) {
    this.clearHideTimer();
    const chipItems = (types ?? []).map((t) => ({
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

  getVisibleDeviceActions(
    elementActions: DeviceActions,
  ): Array<keyof DeviceActions> {
    return this.deviceActionsLayout.filter(
      (actionId) => this.getAction(elementActions, actionId)?.visible,
    );
  }

  getAction(
    elementActions: DeviceActions,
    key: keyof DeviceActions,
  ): ActionButtonState | undefined {
    if (key === 'flash') {
      return elementActions.flash?.state;
    }
    return (
      elementActions as unknown as Record<string, ActionButtonState | undefined>
    )[key];
  }

  onAction(actionId: keyof DeviceActions, element: DeviceSummary) {
    switch (actionId) {
      case 'configuration':
        this.configureDevice(element);
        break;
      case 'screenshot':
        this.takeScreenshot(element);
        break;
      case 'logcat':
        this.getLogcat(element);
        break;
      case 'remoteControl':
        this.startRemoteControl([element]);
        break;
      case 'flash':
        this.flashDevice(element);
        break;
      case 'quarantine':
        this.quarantineDevice(element);
        break;
      case 'decommission':
        this.decommission(element);
        break;
      default:
        break;
    }
  }

  takeScreenshot(element: DeviceSummary): void {
    this.deviceActions.takeScreenshot(element.id);
  }

  getLogcat(element: DeviceSummary): void {
    this.deviceActions.getLogcat(element.id);
  }

  flashDevice(element: DeviceSummary): void {
    this.deviceActions.flashDevice(
      element.id,
      this.host.hostName,
      element.actions?.flash?.params,
    );
  }

  quarantineDevice(element: DeviceSummary): void {
    this.deviceActions.quarantineDevice(element.id, {
      onSuccess: () => {
        this.loadDevices();
      },
    });
  }

  configureDevice(element: DeviceSummary): void {
    this.deviceActions.configureDevice(
      element.id,
      this.host.hostName,
      this.host.ip,
    );
  }
}
