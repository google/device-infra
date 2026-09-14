import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {forkJoin} from 'rxjs';

import {
  CandidateWifi,
  DeviceWifi,
  DeviceWifiConfigEntry,
  TargetDeviceWifi,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/dialog/dialog';
import {
  BulkConfigDialogBaseData,
  BulkConfigDialogResult,
  BulkWifiOption,
  WifiWizardPhase,
} from '../../../models';
import {
  formatCurrentSelectionBadge,
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatSuccessSupportText,
} from '../common/bulk_config_utils';
import {BulkReviewHeaderBanner} from '../common/bulk_review_header_banner';
import {BulkStatusPill} from '../common/bulk_status_pill';
import {useBulkWizardSession} from '../common/bulk_wizard_session';
import {BulkWizardStatusView} from '../common/bulk_wizard_status_view';
import {WifiDetailChip} from './wifi_detail_chip';

/**
 * Bulk Wi-Fi Configuration Dialog (2-step wizard) for OmniLab Console FE v6.
 *
 * Aligned with Google3 Angular 20+ and BFF best practices:
 * - Step 1 (`'network'`): Choose a network (Top target form + backend candidate Wi-Fi
 *   suggestions from GetWifiSuggestions and a "No default Wi-Fi" option).
 * - Step 2 (`'review'`): Review and apply (Authoritative change preview evaluated by
 *   backend against target Wi-Fi, summary banner, count chips, and applying only
 *   WILL_UPDATE devices to BatchUpdateDeviceWifiConfigs).
 */
@Component({
  selector: 'app-bulk-config-wifi-dialog',
  standalone: true,
  templateUrl: './bulk_config_wifi_dialog.ng.html',
  styleUrl: './bulk_config_wifi_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BulkReviewHeaderBanner,
    BulkStatusPill,
    BulkWizardStatusView,
    Dialog,
    FormsModule,
    MatIconModule,
    WifiDetailChip,
  ],
})
export class BulkConfigWifiDialog {
  readonly dialogData: BulkConfigDialogBaseData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(
    MatDialogRef<BulkConfigWifiDialog, BulkConfigDialogResult>,
  );
  private readonly configService = inject(CONFIG_SERVICE);
  readonly wizard = useBulkWizardSession<
    DeviceWifiConfigEntry,
    WifiWizardPhase
  >(this.dialogRef, 'network');

  readonly formatDeviceCount = formatDeviceCount;

  /** Total number of selected devices. */
  readonly totalCount = this.dialogData.deviceIds.length;

  /** Pluralized device label (e.g. '1 device' or '3 devices'). */
  readonly deviceLabel = formatDeviceCount(this.totalCount);

  /** Current active wizard phase ('network' -> 'review'). */
  readonly phase = this.wizard.phase;

  /** Whether the active step's async data request is loading. */
  readonly isStepLoading = this.wizard.isStepLoading;

  /** Error message if the active step's async data request failed. */
  readonly stepError = this.wizard.stepError;

  /** Whether the batch update mutation is in-flight. */
  readonly isApplying = this.wizard.isApplying;

  /** Terminal outcome of applying the batch update. */
  readonly applyResult = this.wizard.applyResult;

  /** Per-device failures returned by the backend. */
  readonly applyErrors = this.wizard.applyErrors;

  /** Device entries with change status and read-only reasons evaluated by the backend for the review step. */
  readonly reviewEntries = this.wizard.reviewEntries;

  /** Single-pass review summary consolidating counts (willUpdateCount, alreadyMatchCount, readOnlyCount) and willUpdateRows. */
  readonly reviewSummary = this.wizard.reviewSummary;

  /** Number of devices successfully updated when partial errors occur. */
  readonly applySuccessCount = this.wizard.applySuccessCount;

  /** Unified wizard view mode driving dialog header, body, and footer templates. */
  readonly viewMode = this.wizard.viewMode;

  /** Whether the target operation is to clear/remove the default Wi-Fi. */
  readonly isClearWifi = signal<boolean>(false);

  /** Target Wi-Fi configuration to apply to selected devices. */
  readonly targetWifi = signal<DeviceWifi>({
    ssid: '',
    psk: '',
    scanSsid: false,
  });

  /** Whether the password field is revealed. */
  readonly showPassword = signal<boolean>(false);

  /** Filter text used to search candidate networks in the network phase. */
  readonly searchFilter = signal<string>('');

  /** Candidate Wi-Fi networks returned directly by backend GetWifiSuggestions. */
  readonly candidateWifis = signal<CandidateWifi[]>([]);

  /** Initial current configs of selected devices fetched in the network phase. */
  readonly initialDeviceEntries = signal<DeviceWifiConfigEntry[]>([]);

  /** Constructs the reactive TargetDeviceWifi proto message according to current UI state. */
  readonly targetDeviceWifi = computed<TargetDeviceWifi>(() => {
    if (this.isClearWifi()) return {clearWifi: {}};
    const {ssid, psk = '', scanSsid = false} = this.targetWifi();
    return {wifi: {ssid: ssid.trim(), psk, scanSsid: Boolean(scanSsid)}};
  });

  /** Dialog headline derived from the active wizard state. */
  readonly headline = computed<string>(() => {
    switch (this.viewMode()) {
      case 'applying':
        return 'Applying Wi-Fi';
      case 'success':
        return 'Wi-Fi updated';
      case 'errors':
        return `${formatDeviceCount(this.applyErrors().length)} could not be updated`;
      case 'review_loading':
      case 'review_error':
      case 'review':
        return 'Review and apply';
      default:
        return `Configure Wi-Fi for ${this.deviceLabel}`;
    }
  });

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() => {
    if (this.isApplying() || this.applyResult()) return '';
    return this.phase() === 'network'
      ? 'Step 1 of 2 · Choose a network'
      : 'Step 2 of 2 · Confirm and apply';
  });

  /** Whether the user can proceed to Step 2. */
  readonly canGoNext = computed<boolean>(
    () => this.isClearWifi() || this.targetWifi().ssid.trim().length > 0,
  );

  /** Badge text for 'No default Wi-Fi' option when one or more selected devices have no Wi-Fi. */
  readonly unconfiguredBadge = computed<string | null>(() => {
    const unconfiguredCount = this.initialDeviceEntries().filter(
      (e) => !e.currentWifi?.ssid,
    ).length;
    return formatCurrentSelectionBadge(unconfiguredCount, this.totalCount);
  });

  /** Whether the 'No default Wi-Fi' row matches the current search filter. */
  readonly matchesClearOption = computed<boolean>(() => {
    const q = this.searchFilter().trim().toLowerCase();
    return !q || 'no default wi-fi clear none'.includes(q);
  });

  /** Concatenated current Wi-Fi networks on selected devices with backend candidate suggestions. */
  readonly wifiOptions = computed<BulkWifiOption[]>(() => {
    const totalSelected = this.totalCount;
    const countsBySsid = new Map<string, number>();
    const optionsBySsid = new Map<string, BulkWifiOption>();

    // 1. Current Wi-Fi networks on selected devices
    for (const {currentWifi} of this.initialDeviceEntries()) {
      if (!currentWifi?.ssid) continue;
      const count = (countsBySsid.get(currentWifi.ssid) ?? 0) + 1;
      countsBySsid.set(currentWifi.ssid, count);
      if (!optionsBySsid.has(currentWifi.ssid)) {
        optionsBySsid.set(currentWifi.ssid, {wifi: currentWifi});
      }
    }
    for (const [ssid, opt] of optionsBySsid) {
      opt.badge =
        formatCurrentSelectionBadge(
          countsBySsid.get(ssid) ?? 0,
          totalSelected,
        ) ?? undefined;
    }

    // 2. Backend candidate suggestions (enrich existing or append new)
    for (const candidate of this.candidateWifis()) {
      const {ssid} = candidate.wifi;
      const subtitle =
        candidate.fleetInUse?.deviceCount != null
          ? `${candidate.fleetInUse.deviceCount.toLocaleString()} devices in fleet`
          : candidate.curatedPreset
            ? 'Preset network'
            : '';
      const existing = optionsBySsid.get(ssid);
      if (existing) {
        existing.subtitle ??= subtitle;
      } else {
        optionsBySsid.set(ssid, {wifi: candidate.wifi, subtitle});
      }
    }

    return [...optionsBySsid.values()];
  });

  /** Filtered Wi-Fi options based on search input. */
  readonly filteredWifiOptions = computed<BulkWifiOption[]>(() => {
    const q = this.searchFilter().trim().toLowerCase();
    const list = this.wifiOptions();
    return q
      ? list.filter((opt) => opt.wifi.ssid.toLowerCase().includes(q))
      : list;
  });

  /** Whether a Wi-Fi option matches the current target configuration in the network step. */
  matchesTargetWifi(wifi: DeviceWifi): boolean {
    if (this.isClearWifi()) return false;
    const target = this.targetWifi();
    return (
      wifi.ssid === target.ssid &&
      (wifi.psk || '') === (target.psk || '') &&
      Boolean(wifi.scanSsid) === Boolean(target.scanSsid)
    );
  }

  /** Subtitle for the target Wi-Fi summary card shown in the review banner. */
  readonly reviewTargetSubtitle = computed<string>(() => {
    if (this.isClearWifi()) {
      return 'Removes default Wi-Fi from target devices';
    }
    const target = this.targetWifi();
    const sec = target.psk ? 'Password protected' : 'Open network';
    const hidden = target.scanSsid ? ' · Hidden SSID' : '';
    return `Target Wi-Fi · ${sec}${hidden}`;
  });

  /** Informational message shown when willUpdateCount is 0 in the review step. */
  readonly nothingToUpdateMessage = computed<string>(() =>
    formatNothingToUpdateMessage(
      this.reviewSummary(),
      this.totalCount,
      this.isClearWifi()
        ? 'already have no default Wi-Fi configured'
        : 'already use this Wi-Fi configuration',
      'Wi-Fi configuration',
    ),
  );

  /** Explanatory support text shown on the success screen. */
  readonly successSupportText = computed<string>(() =>
    formatSuccessSupportText(
      this.reviewSummary(),
      this.isClearWifi()
        ? 'Removed default Wi-Fi from every updated device.'
        : 'Every updated device now uses this network.',
    ),
  );

  constructor() {
    this.loadNetworkStepData();
  }

  /** Loads candidate Wi-Fi suggestions and currently configured networks of selected devices. */
  loadNetworkStepData() {
    this.wizard.runStepTask(
      forkJoin({
        suggestions: this.configService.getWifiSuggestions({
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
        currentConfigs: this.configService.batchGetDeviceWifiConfigs({
          deviceIds: this.dialogData.deviceIds,
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      }),
      ({suggestions, currentConfigs}) => {
        this.candidateWifis.set(suggestions.candidateWifis ?? []);
        this.initialDeviceEntries.set(currentConfigs.entries ?? []);
      },
      'Failed to load Wi-Fi configurations',
    );
  }

  /** Loads review device status evaluated against the target by the backend. */
  loadReviewData() {
    this.wizard.loadReview(
      this.configService.batchGetDeviceWifiConfigs({
        deviceIds: this.dialogData.deviceIds,
        target: this.targetDeviceWifi(),
        universe: this.dialogData.universe,
        fleet: this.dialogData.fleet,
      }),
      'Failed to check device Wi-Fi status',
    );
  }

  /** Updates one or more fields of the target Wi-Fi configuration (and exits clear mode). */
  updateTargetWifi(patch: Partial<DeviceWifi>) {
    this.isClearWifi.set(false);
    if (Object.keys(patch).length > 0) {
      this.targetWifi.update((curr) => ({...curr, ...patch}));
    }
  }

  /** Selects a Wi-Fi network from the list and populates the target form. */
  selectWifi(wifi: DeviceWifi) {
    this.updateTargetWifi({
      ssid: wifi.ssid,
      psk: wifi.psk || '',
      scanSsid: Boolean(wifi.scanSsid),
    });
  }

  /** Sets target to clear/remove the default Wi-Fi from selected devices. */
  clearTargetWifi() {
    this.isClearWifi.set(true);
    this.targetWifi.set({
      ssid: '',
      psk: '',
      scanSsid: false,
    });
  }

  /** Toggles password visibility between masked and plain text. */
  togglePasswordVisibility() {
    this.showPassword.update((val) => !val);
  }

  /** Moves from network phase to review phase after validating input and fetching backend review status. */
  goToReview() {
    if (!this.canGoNext()) return;
    if (!this.isClearWifi()) {
      this.targetWifi.update((curr) => ({...curr, ssid: curr.ssid.trim()}));
    }
    this.loadReviewData();
  }

  /** Returns from review phase back to network phase. */
  goBackToNetwork() {
    this.wizard.setPhase('network');
  }

  /** Applies the batch Wi-Fi update to all writable devices that need updating. */
  apply() {
    this.wizard.apply(
      (deviceIds) =>
        this.configService.batchUpdateDeviceWifiConfigs({
          deviceIds,
          target: this.targetDeviceWifi(),
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      'Server error while applying Wi-Fi update',
    );
  }

  /** Closes dialog and reports whether any devices were updated. */
  close() {
    this.wizard.close();
  }
}
