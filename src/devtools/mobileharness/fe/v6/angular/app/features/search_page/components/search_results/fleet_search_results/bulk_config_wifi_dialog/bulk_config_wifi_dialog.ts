import {
  CdkOverlayOrigin,
  ConnectedPosition,
  OverlayModule,
} from '@angular/cdk/overlay';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTableModule} from '@angular/material/table';
import {finalize} from 'rxjs/operators';
import {
  CandidateWifi,
  DeviceWifiCurrent,
  WifiConfig,
} from '../../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../../shared/components/dialog/dialog';
import {
  BulkConfigWifiDialogData,
  BulkConfigWifiDialogResult,
} from '../../../../models';

/**
 * Bulk Wi-Fi Configuration Dialog (2-step wizard) for OmniLab Console FE v6.
 *
 * Step 1: Choose a network (Top target form + backend candidate networks list).
 * Step 2: Review and apply (Summary banner + comparison table directly showing
 * backend-provided writability, skipped status, and update preview).
 */
@Component({
  selector: 'app-bulk-config-wifi-dialog',
  standalone: true,
  templateUrl: './bulk_config_wifi_dialog.ng.html',
  styleUrl: './bulk_config_wifi_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Dialog,
    FormsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    OverlayModule,
  ],
})
export class BulkConfigWifiDialog {
  readonly dialogData: BulkConfigWifiDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(
    MatDialogRef<BulkConfigWifiDialog, BulkConfigWifiDialogResult>,
  );
  private readonly configService = inject(CONFIG_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  /** Current active wizard step (1: choose network, 2: review & apply). */
  readonly step = signal<1 | 2>(1);

  /** Whether the batch update mutation is in-flight. */
  readonly isApplying = signal<boolean>(false);

  /** Terminal outcome of applying the batch update. */
  readonly applyResult = signal<'success' | 'errors' | null>(null);

  /** Per-device failures returned by the backend. */
  readonly applyErrors = signal<Array<{deviceId: string; reason: string}>>([]);

  /** Snapshot of target device count captured when apply() starts. */
  private readonly appliedTargetCount = signal<number>(0);

  /** Target Wi-Fi configuration to apply to selected devices. */
  readonly targetWifi = signal<Pick<WifiConfig, 'ssid' | 'psk' | 'scanSsid'>>({
    ssid: '',
    psk: '',
    scanSsid: false,
  });

  /** Whether the password field is revealed. */
  readonly showPassword = signal<boolean>(false);

  /** Filter text used to search candidate networks in Step 1. */
  readonly searchFilter = signal<string>('');

  /** Candidate Wi-Fi networks returned by backend for Step 1. */
  readonly candidateWifis = signal<CandidateWifi[]>([]);

  /** Whether Step 1 Wi-Fi suggestions are currently loading. */
  readonly isSuggestionsLoading = signal<boolean>(false);

  /** Error message if loading Step 1 Wi-Fi suggestions failed. */
  readonly suggestionsError = signal<string | null>(null);

  /** Per-device Wi-Fi and writability contexts returned by backend for Step 2. */
  readonly deviceItems = signal<DeviceWifiCurrent[]>([]);

  /** Whether Step 2 device configurations are currently loading. */
  readonly isDevicesLoading = signal<boolean>(false);

  /** Error message if loading Step 2 device configurations failed. */
  readonly devicesError = signal<string | null>(null);

  /** Whether device configs have been successfully fetched for Step 2. */
  private devicesLoaded = false;

  /** Whether the active step's resource is currently loading. */
  readonly isLoading = computed<boolean>(() =>
    this.step() === 1
      ? this.isSuggestionsLoading()
      : this.isDevicesLoading(),
  );

  /** Error message if loading the active step's resource failed. */
  readonly loadError = computed<string | null>(() =>
    this.step() === 1 ? this.suggestionsError() : this.devicesError(),
  );

  /** Formats a device count with proper pluralization (e.g. '1 device', '1,200 devices'). */
  formatDeviceCount(count: number): string {
    return `${count.toLocaleString()} ${count === 1 ? 'device' : 'devices'}`;
  }

  /** Pluralized device label (e.g. '1 device' or '3 devices'). */
  readonly deviceLabel = this.formatDeviceCount(
    this.dialogData.deviceIds.length,
  );

  /** Dialog headline dynamically reflecting wizard step. */
  readonly headline = computed<string>(() =>
    this.step() === 2 ? 'Review and apply' : 'Configure Wi-Fi',
  );

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() => {
    if (this.isApplying() || this.applyResult()) return '';
    if (this.step() === 1) {
      return `Step 1 of 2 · Choose a network for ${this.deviceLabel}`;
    }
    return 'Step 2 of 2 · Confirm and apply';
  });

  /** Whether the user can proceed to Step 2. */
  readonly canGoNext = computed<boolean>(
    () => this.targetWifi().ssid.trim().length > 0,
  );

  /** Candidate sources filtered by search query. */
  readonly filteredSources = computed<CandidateWifi[]>(() => {
    const q = this.searchFilter().trim().toLowerCase();
    const candidates = this.candidateWifis();
    if (!q) return candidates;
    return candidates.filter((s) => s.ssid.toLowerCase().includes(q));
  });

  /** Whether a Wi-Fi configuration matches the normalized target Wi-Fi. */
  isWifiMatchingTarget(wifi?: Partial<WifiConfig>): boolean {
    const target = this.targetWifi();
    return (
      (wifi?.ssid || '') === target.ssid.trim() &&
      (wifi?.psk || '') === target.psk &&
      Boolean(wifi?.scanSsid) === target.scanSsid
    );
  }

  /** Writable devices in the selection. */
  readonly writableRows = computed<DeviceWifiCurrent[]>(() =>
    this.deviceItems().filter((r) => r.writable),
  );

  /** Read-only devices that will be skipped. */
  readonly skippedCount = computed<number>(
    () => this.deviceItems().length - this.writableRows().length,
  );

  /** Writable devices whose Wi-Fi differs from the target and will actually be updated. */
  readonly willUpdateRows = computed<DeviceWifiCurrent[]>(() =>
    this.writableRows().filter((r) => !this.isWifiMatchingTarget(r.wifi)),
  );

  /** Number of writable devices that will update to the target Wi-Fi. */
  readonly willUpdateCount = computed<number>(
    () => this.willUpdateRows().length,
  );

  /** Pluralized label for devices that will actually be updated (e.g. '2 devices'). */
  readonly willUpdateLabel = computed<string>(() => {
    const count =
      (this.isApplying() || this.applyResult()
        ? this.appliedTargetCount()
        : 0) || this.willUpdateCount();
    return this.formatDeviceCount(count);
  });

  /** Whether at least one selected device needs to be updated. */
  readonly canApply = computed<boolean>(() => this.willUpdateCount() > 0);

  /** Number of writable devices that already match the target Wi-Fi. */
  readonly alreadyMatchCount = computed<number>(
    () => this.writableRows().length - this.willUpdateCount(),
  );

  /** Number of devices successfully updated when partial errors occur. */
  readonly applySuccessCount = computed<number>(() => {
    const baseCount = this.appliedTargetCount() || this.willUpdateCount();
    return Math.max(0, baseCount - this.applyErrors().length);
  });

  /** Subtitle for the target Wi-Fi shown in the review banner. */
  readonly targetSecurityMeta = computed<string>(() => {
    const target = this.targetWifi();
    const sec = target.psk ? 'Password protected' : 'Open network';
    const hidden = target.scanSsid ? ' · Hidden SSID' : '';
    return `Target Wi-Fi · ${sec}${hidden}`;
  });

  /** Columns displayed in the Step 2 review table. */
  readonly reviewColumns: readonly string[] = [
    'deviceId',
    'currentWifi',
    'status',
  ];

  /** Columns displayed in the failure / errors table. */
  readonly errorColumns: readonly string[] = ['deviceId', 'reason'];

  /** TrackBy function for device and error rows in MatTable. */
  readonly trackByDeviceId = (_: number, item: {deviceId: string}) =>
    item.deviceId;

  /** Active Wi-Fi tooltip state and anchor origin. */
  readonly activeTip = signal<{
    origin: CdkOverlayOrigin;
    wifi: Partial<WifiConfig>;
  } | null>(null);

  /** Whether the password in the active tooltip is unmasked. */
  readonly showTipPsk = signal<boolean>(false);

  /** Whether the password in the active tooltip was copied recently. */
  readonly copiedTipPsk = signal<boolean>(false);

  /** Overlay positions for the rich Wi-Fi tooltip popup. */
  readonly tipPositions: ConnectedPosition[] = [
    {
      originX: 'start',
      originY: 'bottom',
      overlayX: 'start',
      overlayY: 'top',
      offsetY: 6,
    },
    {
      originX: 'start',
      originY: 'top',
      overlayX: 'start',
      overlayY: 'bottom',
      offsetY: -6,
    },
  ];

  private tipTimer: ReturnType<typeof setTimeout> | null = null;
  private copyTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.closeTip();
    });
    this.loadSuggestions();
  }

  /** Loads candidate Wi-Fi suggestions for Step 1 from BFF. */
  private loadSuggestions() {
    this.isSuggestionsLoading.set(true);
    this.suggestionsError.set(null);
    this.configService
      .getWifiSuggestions(
        this.dialogData.fleet ? {fleet: this.dialogData.fleet} : undefined,
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSuggestionsLoading.set(false);
        }),
      )
      .subscribe({
        next: (res) => {
          this.candidateWifis.set(
            (res.candidateWifis || []).filter((cw) => !!cw.ssid),
          );
        },
        error: (err) => {
          this.suggestionsError.set(
            (err as {message?: string})?.message ||
              'Failed to load Wi-Fi suggestions',
          );
        },
      });
  }

  /** Loads selected device Wi-Fi configs and writability for Step 2 from BFF. */
  private loadDevices() {
    this.isDevicesLoading.set(true);
    this.devicesError.set(null);
    this.configService
      .batchGetDeviceWifiConfigs({
        deviceIds: this.dialogData.deviceIds,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isDevicesLoading.set(false);
        }),
      )
      .subscribe({
        next: (res) => {
          this.deviceItems.set(res.devices || []);
          this.devicesLoaded = true;
        },
        error: (err) => {
          this.devicesError.set(
            (err as {message?: string})?.message ||
              'Failed to load device configurations',
          );
        },
      });
  }

  /** Opens the rich Wi-Fi tooltip when pointer enters a Wi-Fi cell. */
  showWifiTip(origin: CdkOverlayOrigin, wifi?: Partial<WifiConfig>) {
    if (!wifi?.ssid) return;
    this.clearTipTimer();
    this.clearCopyTimer();
    this.showTipPsk.set(false);
    this.copiedTipPsk.set(false);
    this.activeTip.set({origin, wifi});
  }

  /** Cancels scheduled tooltip closure when pointer enters the tooltip body. */
  cancelHideTip() {
    this.clearTipTimer();
  }

  /** Schedules closing the tooltip with a short debounce. */
  scheduleHideTip() {
    this.clearTipTimer();
    this.tipTimer = setTimeout(() => {
      this.closeTip();
    }, 200);
  }

  /** Closes the active tooltip immediately. */
  closeTip() {
    this.clearTipTimer();
    this.clearCopyTimer();
    this.activeTip.set(null);
    this.showTipPsk.set(false);
    this.copiedTipPsk.set(false);
  }

  /** Toggles password visibility inside the tooltip between masked dots and plain text. */
  toggleTipPsk() {
    this.showTipPsk.update((v) => !v);
  }

  /** Copies the tooltip password to the operator's clipboard. */
  async copyTipPsk(psk?: string) {
    if (!psk) return;
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(psk);
      }
      if (!this.activeTip()) return;
      this.copiedTipPsk.set(true);
      this.clearCopyTimer();
      this.copyTimer = setTimeout(() => {
        this.copiedTipPsk.set(false);
      }, 2000);
    } catch {
      // Ignore clipboard permission or environment errors
    }
  }

  private clearTipTimer() {
    if (this.tipTimer) {
      clearTimeout(this.tipTimer);
      this.tipTimer = null;
    }
  }

  private clearCopyTimer() {
    if (this.copyTimer) {
      clearTimeout(this.copyTimer);
      this.copyTimer = null;
    }
  }

  /** Updates one or more fields of the target Wi-Fi configuration. */
  updateTargetWifi(
    patch: Partial<Pick<WifiConfig, 'ssid' | 'psk' | 'scanSsid'>>,
  ) {
    this.targetWifi.update((curr) => ({...curr, ...patch}));
  }

  /** Selects a candidate network and copies values into the target form. */
  selectSource(source: CandidateWifi) {
    this.targetWifi.set({
      ssid: source.ssid,
      psk: source.psk || '',
      scanSsid: Boolean(source.scanSsid),
    });
  }

  /** Toggles password visibility between masked and plain text. */
  togglePasswordVisibility() {
    this.showPassword.update((val) => !val);
  }

  /** Moves from Step 1 to Step 2 after validating input. */
  goToStep2() {
    if (!this.canGoNext()) return;
    this.targetWifi.update((curr) => ({...curr, ssid: curr.ssid.trim()}));
    this.step.set(2);
    if (!this.devicesLoaded || this.devicesError()) {
      this.loadDevices();
    }
  }

  /** Returns back from Step 2 to Step 1. */
  backToStep1() {
    this.closeTip();
    this.step.set(1);
  }

  /** Retries loading for the current step. */
  retryLoad() {
    if (this.step() === 2) {
      this.loadDevices();
    } else {
      this.loadSuggestions();
    }
  }

  /** Applies the batch Wi-Fi update to all writable devices that need updating. */
  apply() {
    const updateIds = this.willUpdateRows().map((r) => r.deviceId);
    if (updateIds.length === 0) return;

    this.closeTip();
    this.appliedTargetCount.set(updateIds.length);
    this.isApplying.set(true);
    this.dialogRef.disableClose = true;
    const target = this.targetWifi();

    this.configService
      .batchUpdateDeviceWifiConfigs({
        deviceIds: updateIds,
        wifi: {...target, ssid: target.ssid.trim()},
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isApplying.set(false);
          this.dialogRef.disableClose = false;
        }),
      )
      .subscribe({
        next: (res) => {
          const errors = Object.entries(res.errors || {}).map(
            ([deviceId, err]) => ({
              deviceId,
              reason: err.message || err.code,
            }),
          );
          if (errors.length > 0) {
            this.applyErrors.set(errors);
            this.applyResult.set('errors');
          } else {
            this.applyResult.set('success');
          }
        },
        error: (err) => {
          const reason =
            err?.message || 'Server error while applying Wi-Fi update';
          this.applyErrors.set(
            updateIds.map((deviceId) => ({deviceId, reason})),
          );
          this.applyResult.set('errors');
        },
      });
  }

  /** Closes dialog directly. */
  close() {
    const hasUpdated =
      this.applyResult() === 'success' ||
      (this.applyResult() === 'errors' && this.applySuccessCount() > 0);
    this.dialogRef.close(hasUpdated ? {updated: true} : undefined);
  }
}
