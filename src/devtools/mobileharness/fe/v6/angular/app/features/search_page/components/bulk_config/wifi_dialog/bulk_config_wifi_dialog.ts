import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
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
import {BulkConfigDialogData} from '../../../models';
import {
  formatCurrentSelectionBadge,
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatSuccessSupportText,
} from '../../../utils';
import {BulkReviewHeaderBanner} from '../common/bulk_review_header_banner';
import {BulkReviewTable} from '../common/bulk_review_table';
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
    BulkReviewTable,
    BulkWizardStatusView,
    Dialog,
    FormsModule,
    MatIconModule,
    WifiDetailChip,
  ],
})
export class BulkConfigWifiDialog {
  readonly dialogData: BulkConfigDialogData = inject(MAT_DIALOG_DATA);
  private readonly configService = inject(CONFIG_SERVICE);
  readonly wizard = useBulkWizardSession<
    DeviceWifiConfigEntry,
    'network' | 'review'
  >('network');

  /** Total number of selected devices. */
  readonly totalCount = this.dialogData.devices.length;

  /** Pluralized device label (e.g. '1 device' or '3 devices'). */
  readonly deviceLabel = formatDeviceCount(this.totalCount);

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
  readonly headline = computed<string>(() =>
    this.wizard.resolveHeadline(
      'Wi-Fi',
      this.wizard.phase() === 'review'
        ? 'Review and apply'
        : `Configure Wi-Fi for ${this.deviceLabel}`,
    ),
  );

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() =>
    this.wizard.resolveSupportText(
      this.wizard.phase() === 'network'
        ? 'Step 1 of 2 · Choose a network'
        : 'Step 2 of 2 · Confirm and apply',
    ),
  );

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
  readonly wifiOptions = computed(() => {
    const totalSelected = this.totalCount;
    const optionsBySsid = new Map<
      string,
      {wifi: DeviceWifi; count: number; subtitle?: string}
    >();

    // 1. Current Wi-Fi networks on selected devices
    for (const {currentWifi} of this.initialDeviceEntries()) {
      if (!currentWifi?.ssid) continue;
      const existing = optionsBySsid.get(currentWifi.ssid);
      if (existing) {
        existing.count++;
      } else {
        optionsBySsid.set(currentWifi.ssid, {wifi: currentWifi, count: 1});
      }
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
        optionsBySsid.set(ssid, {wifi: candidate.wifi, count: 0, subtitle});
      }
    }

    return Array.from(optionsBySsid.values(), ({wifi, count, subtitle}) => ({
      wifi,
      subtitle,
      badge: formatCurrentSelectionBadge(count, totalSelected) ?? undefined,
    }));
  });

  /** Filtered Wi-Fi options based on search input, with pre-computed active selection state. */
  readonly filteredWifiOptions = computed(() => {
    const q = this.searchFilter().trim().toLowerCase();
    const isClear = this.isClearWifi();
    const target = this.targetWifi();
    const targetSsid = target.ssid.trim();
    const targetPsk = target.psk || '';
    const targetScan = Boolean(target.scanSsid);

    const list = this.wifiOptions();
    const matched = q
      ? list.filter((opt) => opt.wifi.ssid.toLowerCase().includes(q))
      : list;
    return matched.map((opt) => ({
      ...opt,
      isActive:
        !isClear &&
        opt.wifi.ssid === targetSsid &&
        (opt.wifi.psk || '') === targetPsk &&
        Boolean(opt.wifi.scanSsid) === targetScan,
    }));
  });

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
      this.wizard.reviewSummary(),
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
      this.wizard.reviewSummary(),
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
      () =>
        forkJoin({
          suggestions: this.configService.getWifiSuggestions({
            universe: this.dialogData.universe,
            fleet: this.dialogData.fleet,
          }),
          currentConfigs: this.configService.batchGetDeviceWifiConfigs({
            devices: this.dialogData.devices,
            universe: this.dialogData.universe,
            fleet: this.dialogData.fleet,
          }),
        }),
      ({suggestions, currentConfigs}) => {
        this.candidateWifis.set(suggestions.candidateWifis ?? []);
        this.initialDeviceEntries.set(currentConfigs.entries ?? []);
      },
      'Failed to load Wi-Fi configurations',
      'network',
      'Loading Wi-Fi configurations…',
    );
  }

  /** Updates one or more fields of the target Wi-Fi configuration (and exits clear mode). */
  updateTargetWifi(patch: Partial<DeviceWifi>) {
    this.isClearWifi.set(false);
    this.targetWifi.update((curr) => ({...curr, ...patch}));
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
    this.wizard.loadReview(
      () =>
        this.configService.batchGetDeviceWifiConfigs({
          devices: this.dialogData.devices,
          target: this.targetDeviceWifi(),
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      'Failed to check device Wi-Fi status',
    );
  }

  /** Applies the batch Wi-Fi update to all writable devices that need updating. */
  apply() {
    this.wizard.apply(
      (devices) =>
        this.configService.batchUpdateDeviceWifiConfigs({
          devices,
          target: this.targetDeviceWifi(),
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      'Server error while applying Wi-Fi update',
    );
  }
}
