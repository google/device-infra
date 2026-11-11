import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';

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
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatWizardHeadline,
  formatWizardSupportText,
} from '../../../utils';
import {BulkClearBanner} from '../common/bulk_clear_banner';
import {BulkPanelStatus} from '../common/bulk_panel_status';
import {BulkReviewHeaderBanner} from '../common/bulk_review_header_banner';
import {BulkReviewTable} from '../common/bulk_review_table';
import {BulkWizardFooterActions} from '../common/bulk_wizard_footer_actions';
import {
  useBulkWizardSession,
  useInlineTask,
} from '../common/bulk_wizard_session';
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
    BulkClearBanner,
    BulkPanelStatus,
    BulkReviewHeaderBanner,
    BulkReviewTable,
    BulkWizardFooterActions,
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
  readonly ssidInputField =
    viewChild<ElementRef<HTMLInputElement>>('ssidInput');

  /** Pluralized device label (e.g. '1 device' or '3 devices'). */
  readonly deviceLabel = formatDeviceCount(this.dialogData.devices.length);

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

  /** Inline async task tracking Step 1 candidate Wi-Fi suggestions. */
  readonly networkTask = useInlineTask('Failed to load Wi-Fi configurations');

  /** Constructs the reactive TargetDeviceWifi proto message according to current UI state. */
  readonly targetDeviceWifi = computed<TargetDeviceWifi>(() => {
    if (this.isClearWifi()) return {clearWifi: {}};
    const {ssid, psk, scanSsid} = this.targetWifi();
    return {wifi: {ssid: ssid.trim(), psk, scanSsid}};
  });

  /** Dialog headline derived from the active wizard state. */
  readonly headline = computed<string>(() =>
    formatWizardHeadline(
      this.wizard.viewMode(),
      'Wi-Fi',
      this.wizard.phase() === 'review'
        ? 'Review and apply'
        : `Configure Wi-Fi for ${this.deviceLabel}`,
    ),
  );

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() =>
    formatWizardSupportText(
      this.wizard.viewMode(),
      this.wizard.phase() === 'network'
        ? 'Step 1 of 2 · Choose a network'
        : 'Step 2 of 2 · Confirm and apply',
    ),
  );

  /** Whether the user can proceed to Step 2. */
  readonly canGoNext = computed<boolean>(
    () => this.isClearWifi() || this.targetWifi().ssid.trim().length > 0,
  );

  /** Whether the 'No default Wi-Fi' row matches the current search filter. */
  readonly matchesClearOption = computed<boolean>(() => {
    const q = this.searchFilter().trim().toLowerCase();
    return !q || 'no default wi-fi clear none'.includes(q);
  });

  /** Filtered Wi-Fi options based on search input, with formatted subtitle and active selection state. */
  readonly filteredWifiOptions = computed(() => {
    const q = this.searchFilter().trim().toLowerCase();
    const isClear = this.isClearWifi();
    const target = this.targetWifi();
    const targetSsid = target.ssid.trim();

    const list = this.candidateWifis();
    const matched = q
      ? list.filter((c) => c.wifi.ssid.toLowerCase().includes(q))
      : list;
    return matched.map((c) => ({
      wifi: c.wifi,
      subtitle:
        c.fleetInUse?.deviceCount != null
          ? `${formatDeviceCount(c.fleetInUse.deviceCount)} in fleet`
          : c.curatedPreset
            ? 'Preset network'
            : '',
      isActive:
        !isClear &&
        c.wifi.ssid === targetSsid &&
        (c.wifi.psk || '') === target.psk &&
        Boolean(c.wifi.scanSsid) === target.scanSsid,
    }));
  });

  /** Security label for the target Wi-Fi configuration. */
  readonly securityLabel = computed<string>(() =>
    this.targetWifi().psk ? 'Password protected' : 'Open network',
  );

  /** Subtitle for the target Wi-Fi summary card shown in the review banner. */
  readonly reviewTargetSubtitle = computed<string>(() => {
    if (this.isClearWifi()) {
      return 'Removes default Wi-Fi from target devices';
    }
    const hidden = this.targetWifi().scanSsid ? ' · Hidden SSID' : '';
    return `Target Wi-Fi · ${this.securityLabel()}${hidden}`;
  });

  /** Informational message shown when willUpdateCount is 0 in the review step. */
  readonly nothingToUpdateMessage = computed<string>(() =>
    formatNothingToUpdateMessage(
      this.wizard.reviewSummary(),
      this.isClearWifi()
        ? 'already have no default Wi-Fi configured'
        : 'already use this Wi-Fi configuration',
      'Wi-Fi configuration',
    ),
  );

  constructor() {
    effect(() => {
      this.ssidInputField()?.nativeElement.focus();
    });
    this.loadNetworkStepData();
  }

  /** Loads candidate Wi-Fi suggestions from backend. */
  loadNetworkStepData() {
    this.networkTask.run(
      () => this.configService.getWifiSuggestions(),
      (suggestions) => {
        this.candidateWifis.set(suggestions.candidateWifis ?? []);
      },
    );
  }

  /** Updates one or more fields of the target Wi-Fi configuration (and exits clear mode). */
  updateTargetWifi(patch: Partial<DeviceWifi>) {
    this.isClearWifi.set(false);
    this.targetWifi.update((curr) => {
      const next = {...curr, ...patch};
      return {
        ssid: next.ssid || '',
        psk: next.psk || '',
        scanSsid: Boolean(next.scanSsid),
      };
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
    this.targetWifi.update((curr) => ({...curr, ssid: curr.ssid.trim()}));
    this.wizard.loadReview(
      () =>
        this.configService.batchGetDeviceWifiConfigs({
          devices: this.dialogData.devices,
          target: this.targetDeviceWifi(),
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
        }),
      'Server error while applying Wi-Fi update',
    );
  }
}
