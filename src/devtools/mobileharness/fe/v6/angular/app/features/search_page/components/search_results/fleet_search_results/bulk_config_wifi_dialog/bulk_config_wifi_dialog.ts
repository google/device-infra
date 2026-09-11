import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
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
  WifiConfig,
} from '../../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../../shared/components/dialog/dialog';

/** Input data structure passed into the BulkConfigWifiDialog. */
export interface BulkConfigWifiDialogData {
  deviceIds: string[];
}

/** Result returned when the BulkConfigWifiDialog closes. */
export interface BulkConfigWifiDialogResult {
  updated: boolean;
}

/** Aggregated network source item displayed in Step 1. */
export interface AggregatedWifiSource {
  key: string;
  ssid: string;
  psk: string;
  scanSsid: boolean;
  selectedCount: number;
  fleetCount?: number;
  isNotConfigured: boolean;
  isSuggested: boolean;
}

/** Per-device review comparison item displayed in Step 2. */
export interface WifiReviewRow {
  deviceId: string;
  currentWifi?: WifiConfig;
  writable: boolean;
  unwritableReason: string;
  isChanged: boolean;
}

/**
 * Bulk Wi-Fi Configuration Dialog (2-step wizard) for OmniLab Console FE v6.
 *
 * Step 1: Choose a network (Top target form + bottom aggregated networks list).
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

  /** Key of the network source currently chosen via "Use this". */
  readonly activeSourceKey = signal<string | null>(null);

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

  /** Dialog headline based on current step and outcome. */
  readonly headline = computed<string>(() => {
    if (this.isApplying()) return 'Applying Wi-Fi';
    if (this.applyResult() === 'success') {
      return `${this.totalCount() === 1 ? '1 device' : `${this.totalCount()} devices`} updated`;
    }
    if (this.applyResult() === 'errors') {
      const errCount = this.applyErrors().length;
      return `${errCount === 1 ? '1 device' : `${errCount} devices`} could not be updated`;
    }
    if (this.step() === 1) {
      return `Configure Wi-Fi for ${this.deviceLabel()}`;
    }
    return 'Review and apply';
  });

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() => {
    if (this.isApplying() || this.applyResult()) return '';
    if (this.step() === 1) return 'Step 1 of 2 · Choose a network';
    return 'Step 2 of 2 · Confirm and apply';
  });

  /** Whether the user can proceed to Step 2. */
  readonly canGoNext = computed<boolean>(
    () => this.targetSsid().trim().length > 0,
  );

  /** Deduplicated aggregated networks across selected devices + candidate networks. */
  readonly aggregatedSources = computed<AggregatedWifiSource[]>(() => {
    const map = new Map<string, AggregatedWifiSource>();
    let notConfiguredCount = 0;

    for (const item of this.deviceItems()) {
      const wifi = item.wifi;
      if (!wifi || !wifi.ssid) {
        notConfiguredCount++;
        continue;
      }
      const key = `${wifi.ssid}:::${wifi.scanSsid ? 'hidden' : 'broadcast'}`;
      if (!map.has(key)) {
        map.set(key, {
          key,
          ssid: wifi.ssid,
          psk: wifi.psk || '',
          scanSsid: !!wifi.scanSsid,
          selectedCount: 0,
          isNotConfigured: false,
          isSuggested: false,
        });
      }
      map.get(key)!.selectedCount++;
    }

    for (const cw of this.candidateWifis()) {
      const key = `${cw.ssid}:::${cw.scanSsid ? 'hidden' : 'broadcast'}`;
      if (!map.has(key)) {
        map.set(key, {
          key,
          ssid: cw.ssid,
          psk: cw.psk || '',
          scanSsid: !!cw.scanSsid,
          selectedCount: 0,
          fleetCount: cw.deviceCount || 0,
          isNotConfigured: false,
          isSuggested: true,
        });
      } else {
        const entry = map.get(key)!;
        entry.fleetCount = cw.deviceCount || 0;
      }
    }

    const list = Array.from(map.values()).sort(
      (a, b) => b.selectedCount - a.selectedCount || (b.fleetCount ?? 0) - (a.fleetCount ?? 0),
    );

    if (notConfiguredCount > 0) {
      list.push({
        key: '__not_configured__',
        ssid: '',
        psk: '',
        scanSsid: false,
        selectedCount: notConfiguredCount,
        isNotConfigured: true,
        isSuggested: false,
      });
    }

    return list;
  });

  /** Aggregated sources filtered by search query. */
  readonly filteredSources = computed<AggregatedWifiSource[]>(() => {
    const q = this.searchFilter().trim().toLowerCase();
    if (!q) return this.aggregatedSources();
    return this.aggregatedSources().filter(
      (s) => s.isNotConfigured || s.ssid.toLowerCase().includes(q),
    );
  });

  /** Target key string representing the currently entered target Wi-Fi configuration. */
  readonly targetConfigKey = computed<string>(() => {
    const ssid = this.targetSsid().trim();
    if (!ssid) return '';
    return `${ssid}:::${this.targetScanSsid() ? 'hidden' : 'broadcast'}`;
  });

  /** Per-device review rows for Step 2. */
  readonly reviewRows = computed<WifiReviewRow[]>(() => {
    const targetSsid = this.targetSsid().trim();
    const targetScanSsid = this.targetScanSsid();
    const targetPsk = this.targetPsk();

    return this.deviceItems().map((item) => {
      const cur = item.wifi;
      const curSsid = cur?.ssid || '';
      const curScanSsid = !!cur?.scanSsid;
      const curPsk = cur?.psk || '';

      const writable = item.writable !== false;
      const unwritableReason = item.unwritableReason || '';

      const isSame =
        curSsid === targetSsid &&
        curScanSsid === targetScanSsid &&
        curPsk === targetPsk;

      return {
        deviceId: item.deviceId,
        currentWifi: cur,
        writable,
        unwritableReason,
        isChanged: writable && !isSame,
      };
    });
  });

  /** Writable devices in the selection. */
  readonly writableRows = computed<WifiReviewRow[]>(() =>
    this.reviewRows().filter((r) => r.writable),
  );

  /** Devices that will have their Wi-Fi mutated. */
  readonly changedCount = computed<number>(
    () => this.writableRows().filter((r) => r.isChanged).length,
  );

  /** Read-only devices that will be skipped. */
  readonly skippedCount = computed<number>(
    () => this.reviewRows().filter((r) => !r.writable).length,
  );

  /** Devices whose current Wi-Fi already matches the target. */
  readonly sameCount = computed<number>(
    () => this.writableRows().filter((r) => !r.isChanged).length,
  );

  /** Whether at least one selected device is writable. */
  readonly hasWritableDevices = computed<boolean>(
    () => this.writableRows().length > 0,
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
      .subscribe({
        next: (res) => {
          const rawCurrent = res.current || [];
          const normalizedCurrent: DeviceCurrentWifi[] = rawCurrent.map((item) => ({
            deviceId: item.deviceId || '',
            wifi: item.wifi,
            writable: item.writable !== false,
            unwritableReason: item.unwritableReason || '',
          }));

          const rawCandidates = res.candidateWifis || [];
          const normalizedCandidates: CandidateWifi[] = rawCandidates.map((cw) => ({
            ssid: cw.ssid || '',
            psk: cw.psk || '',
            scanSsid: !!cw.scanSsid,
            deviceCount: cw.deviceCount ?? 0,
          }));

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

  /**
   * If all selected devices already share the exact same Wi-Fi config, default the target to it.
   */
  private initDefaultTarget(devices: DeviceCurrentWifi[]) {
    if (devices.length === 0) return;
    const first = devices[0].wifi;
    if (!first || !first.ssid) return;

    const firstKey = `${first.ssid}:::${first.psk || ''}:::${!!first.scanSsid}`;
    const allSame = devices.every((d) => {
      const c = d.wifi;
      if (!c || !c.ssid) return false;
      return `${c.ssid}:::${c.psk || ''}:::${!!c.scanSsid}` === firstKey;
    });

    if (allSame) {
      this.targetSsid.set(first.ssid);
      this.targetPsk.set(first.psk || '');
      this.targetScanSsid.set(!!first.scanSsid);
      this.activeSourceKey.set(
        `${first.ssid}:::${first.scanSsid ? 'hidden' : 'broadcast'}`,
      );
    }
  }

  /** Selects a network from the aggregated list and copies values into the target form. */
  selectSource(source: AggregatedWifiSource) {
    if (source.isNotConfigured) return;
    this.targetSsid.set(source.ssid);
    this.targetPsk.set(source.psk || '');
    this.targetScanSsid.set(source.scanSsid);
    this.activeSourceKey.set(source.key);
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
    const writableIds = this.writableRows().map((r) => r.deviceId);
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
      .subscribe({
        next: (res) => {
          this.isApplying.set(false);
          const errorsMap = res.errors || {};
          const errEntries = Object.entries(errorsMap);

          if (errEntries.length > 0) {
            const errList = errEntries.map(([did, err]) => {
              let reason = 'Update failed';
              if (typeof err === 'string') {
                reason = err;
              } else if (err && typeof err === 'object') {
                reason = err.message || err.code || 'Update failed';
              }
              return {deviceId: did, reason};
            });
            this.applyErrors.set(errList);
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

  /** Closes dialog upon completion, returning update confirmation. */
  done() {
    this.dialogRef.close({updated: true});
  }

  /** Closes dialog directly. */
  close() {
    this.dialogRef.close(
      this.applyResult() === 'success' ? {updated: true} : undefined,
    );
  }
}
