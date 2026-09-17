import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {
  CandidateWifi,
  DeviceCurrentWifi,
} from '../../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../../shared/components/dialog/dialog';

/** Generates a unique key for a Wi-Fi configuration based on SSID and visibility. */
export function getWifiConfigKey(ssid: string, scanSsid: boolean): string {
  return `${ssid}:::${scanSsid ? 'hidden' : 'broadcast'}`;
}

/** Input data structure passed into the BulkConfigWifiDialog. */
export interface BulkConfigWifiDialogData {
  deviceIds: string[];
}

/** Result returned when the BulkConfigWifiDialog closes. */
export interface BulkConfigWifiDialogResult {
  updated: boolean;
}

/** Candidate Wi-Fi network item displayed in Step 1. */
export interface CandidateWifiSource extends CandidateWifi {
  key: string;
}

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
    CommonModule,
    Dialog,
    FormsModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
})
export class BulkConfigWifiDialog implements OnInit {
  readonly dialogData: BulkConfigWifiDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(
    MatDialogRef<BulkConfigWifiDialog, BulkConfigWifiDialogResult>,
  );
  private readonly configService = inject(CONFIG_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  /** Current active wizard step (1: choose network, 2: review & apply). */
  readonly step = signal<1 | 2>(1);

  /** Whether the initial batch context is being fetched from the backend. */
  readonly isLoading = signal<boolean>(true);

  /** Error message if loading the batch context failed. */
  readonly loadError = signal<string | null>(null);

  /** Whether the batch update mutation is in-flight. */
  readonly isApplying = signal<boolean>(false);

  /** Terminal outcome of applying the batch update. */
  readonly applyResult = signal<'success' | 'errors' | null>(null);

  /** Per-device failures returned by the backend. */
  readonly applyErrors = signal<Array<{deviceId: string; reason: string}>>([]);

  /** Target network SSID input by operator. */
  readonly targetSsid = signal<string>('');

  /** Target network password (PSK). */
  readonly targetPsk = signal<string>('');

  /** Whether the target network is hidden. */
  readonly targetScanSsid = signal<boolean>(false);

  /** Whether the password field is revealed. */
  readonly showPassword = signal<boolean>(false);

  /** Filter text used to search candidate networks in Step 1. */
  readonly searchFilter = signal<string>('');

  /** Raw per-device Wi-Fi and permission contexts returned by backend. */
  readonly deviceItems = signal<DeviceCurrentWifi[]>([]);

  /** Candidate Wi-Fi networks returned by backend ranked by fleet popularity. */
  readonly candidateWifis = signal<CandidateWifi[]>([]);

  /** Total number of selected devices. */
  readonly totalCount = computed<number>(() => this.dialogData.deviceIds.length);

  /** Number of devices successfully updated when partial errors occur. */
  readonly applySuccessCount = computed<number>(
    () => this.totalCount() - this.applyErrors().length,
  );

  /** Pluralized device label. */
  readonly deviceLabel = computed<string>(() =>
    this.totalCount() === 1 ? '1 device' : `${this.totalCount()} devices`,
  );

  /** Dialog headline, consistently maintained as 'Configure Wi-Fi'. */
  readonly headline = 'Configure Wi-Fi';

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() => {
    if (this.isApplying() || this.applyResult()) return '';
    if (this.step() === 1) {
      return `Step 1 of 2 · Choose a network for ${this.deviceLabel()}`;
    }
    return 'Step 2 of 2 · Review and apply';
  });

  /** Whether the user can proceed to Step 2. */
  readonly canGoNext = computed<boolean>(
    () => this.targetSsid().trim().length > 0,
  );

  /** Candidate Wi-Fi networks derived directly from backend-authoritative candidateWifis. */
  readonly candidateSources = computed<CandidateWifiSource[]>(() => {
    return this.candidateWifis()
      .filter((cw) => !!cw.ssid)
      .map((cw) => ({
        key: getWifiConfigKey(cw.ssid, !!cw.scanSsid),
        ssid: cw.ssid,
        psk: cw.psk || '',
        scanSsid: !!cw.scanSsid,
        deviceCount: cw.deviceCount,
      }));
  });

  /** Candidate sources filtered by search query. */
  readonly filteredSources = computed<CandidateWifiSource[]>(() => {
    const q = this.searchFilter().trim().toLowerCase();
    if (!q) return this.candidateSources();
    return this.candidateSources().filter((s) =>
      s.ssid.toLowerCase().includes(q),
    );
  });

  /** Target key string representing the currently entered target Wi-Fi configuration. */
  readonly targetConfigKey = computed<string>(() => {
    const ssid = this.targetSsid().trim();
    if (!ssid) return '';
    return getWifiConfigKey(ssid, this.targetScanSsid());
  });

  /** Per-device review rows for Step 2 (direct reference to normalized device items). */
  readonly reviewRows = this.deviceItems;

  /** Writable devices in the selection. */
  readonly writableRows = computed<DeviceCurrentWifi[]>(() =>
    this.deviceItems().filter((r) => r.writable),
  );

  /** Device IDs of writable devices that will be updated in batch. */
  readonly writableDeviceIds = computed<string[]>(() =>
    this.writableRows().map((r) => r.deviceId),
  );

  /** Number of writable devices that will have Wi-Fi applied. */
  readonly writableCount = computed<number>(
    () => this.writableDeviceIds().length,
  );

  /** Read-only devices that will be skipped. */
  readonly skippedCount = computed<number>(
    () => this.deviceItems().length - this.writableCount(),
  );

  /** Whether at least one selected device is writable. */
  readonly hasWritableDevices = computed<boolean>(
    () => this.writableCount() > 0,
  );

  ngOnInit() {
    this.loadBatchWifiContext();
  }

  /** Loads current Wi-Fi contexts and candidate networks for selected devices. */
  loadBatchWifiContext() {
    this.isLoading.set(true);
    this.loadError.set(null);

    this.configService
      .getBatchWifiContext(this.dialogData.deviceIds)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const normalizedCurrent = this.normalizeDeviceItems(res.current);
          const normalizedCandidates = this.normalizeCandidateWifis(
            res.candidateWifis,
          );

          this.deviceItems.set(normalizedCurrent);
          this.candidateWifis.set(normalizedCandidates);
          this.initDefaultTarget(normalizedCurrent);
          this.isLoading.set(false);
        },
        error: (err) => {
          this.loadError.set(err?.message || 'Failed to load Wi-Fi context');
          this.isLoading.set(false);
        },
      });
  }

  /** Normalizes raw per-device items from backend response. */
  private normalizeDeviceItems(
    items?: DeviceCurrentWifi[],
  ): DeviceCurrentWifi[] {
    return (items || []).map((item) => ({
      deviceId: item.deviceId || '',
      wifi: item.wifi,
      writable: item.writable !== false,
      unwritableReason: item.unwritableReason || '',
    }));
  }

  /** Normalizes raw candidate Wi-Fi list from backend response. */
  private normalizeCandidateWifis(
    candidates?: CandidateWifi[],
  ): CandidateWifi[] {
    return (candidates || []).map((cw) => ({
      ssid: cw.ssid || '',
      psk: cw.psk || '',
      scanSsid: !!cw.scanSsid,
      deviceCount: cw.deviceCount ?? 0,
    }));
  }

  /**
   * If all selected devices already share the exact same Wi-Fi config, default the target to it.
   */
  private initDefaultTarget(devices: DeviceCurrentWifi[]) {
    if (devices.length === 0) return;
    const firstWifi = devices[0].wifi;
    if (!firstWifi?.ssid) return;

    const allSame = devices.every(
      (d) =>
        d.wifi?.ssid === firstWifi.ssid &&
        (d.wifi?.psk || '') === (firstWifi.psk || '') &&
        !!d.wifi?.scanSsid === !!firstWifi.scanSsid,
    );

    if (allSame) {
      this.setTargetWifi(
        firstWifi.ssid,
        firstWifi.psk || '',
        !!firstWifi.scanSsid,
      );
    }
  }

  /** Sets target Wi-Fi form values. */
  setTargetWifi(ssid: string, psk = '', scanSsid = false) {
    this.targetSsid.set(ssid);
    this.targetPsk.set(psk);
    this.targetScanSsid.set(scanSsid);
  }

  /** Selects a candidate network and copies values into the target form. */
  selectSource(source: CandidateWifiSource) {
    this.setTargetWifi(source.ssid, source.psk, source.scanSsid);
  }

  /** Whether the given candidate network source is currently selected/active. */
  isSourceActive(source: CandidateWifiSource): boolean {
    return source.key === this.targetConfigKey();
  }

  /** Toggles password visibility between masked and plain text. */
  togglePasswordVisibility() {
    this.showPassword.update((val) => !val);
  }

  /** Moves from Step 1 to Step 2 after validating input. */
  goToStep2() {
    if (!this.canGoNext()) return;
    this.step.set(2);
  }

  /** Returns back from Step 2 to Step 1. */
  backToStep1() {
    this.step.set(1);
  }

  /** Applies the batch Wi-Fi update to all writable devices. */
  apply() {
    const writableIds = this.writableDeviceIds();
    if (writableIds.length === 0) return;

    this.isApplying.set(true);
    const ssid = this.targetSsid().trim();
    const psk = this.targetPsk();
    const scanSsid = this.targetScanSsid();

    this.configService
      .batchUpdateDeviceConfig({
        deviceIds: writableIds,
        wifi: {
          ssid,
          psk,
          scanSsid,
          type: 'custom',
        },
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.isApplying.set(false);
          const errorsMap = res.errors || {};
          if (Object.keys(errorsMap).length > 0) {
            this.applyErrors.set(this.parseBatchErrors(errorsMap));
            this.applyResult.set('errors');
          } else {
            this.applyResult.set('success');
          }
        },
        error: (err) => {
          this.isApplying.set(false);
          this.applyErrors.set([
            {
              deviceId: 'All writable devices',
              reason: err?.message || 'Server error while applying Wi-Fi update',
            },
          ]);
          this.applyResult.set('errors');
        },
      });
  }

  /** Parses backend per-device errors into a formatted list. */
  private parseBatchErrors(
    errorsMap: Record<string, unknown>,
  ): Array<{deviceId: string; reason: string}> {
    return Object.entries(errorsMap).map(([deviceId, err]) => {
      let reason = 'Update failed';
      if (typeof err === 'string') {
        reason = err;
      } else if (err && typeof err === 'object') {
        const obj = err as {message?: string; code?: string};
        reason = obj.message || obj.code || 'Update failed';
      }
      return {deviceId, reason};
    });
  }

  /** Closes dialog upon completion, returning update confirmation. */
  done() {
    this.close();
  }

  /** Closes dialog directly. */
  close() {
    this.dialogRef.close(
      this.applyResult() === 'success' ? {updated: true} : undefined,
    );
  }
}
