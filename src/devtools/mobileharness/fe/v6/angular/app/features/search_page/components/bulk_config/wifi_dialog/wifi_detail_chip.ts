import {ConnectedPosition, OverlayModule} from '@angular/cdk/overlay';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

import {DeviceWifi} from '../../../../../core/models/device_config_models';
import {ClipboardService} from '../../../../../shared/services/clipboard_service';

const TIP_POSITIONS: ConnectedPosition[] = [
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

/**
 * Interactive Wi-Fi chip with a rich hover overlay card showing security,
 * password reveal/copy, and SSID broadcast visibility.
 *
 * Encapsulates CdkOverlay positioning, hover debounce timers, and clipboard
 * interactions so parent wizard components remain decoupled from overlay state.
 */
@Component({
  selector: 'app-wifi-detail-chip',
  standalone: true,
  templateUrl: './wifi_detail_chip.ng.html',
  styleUrl: './wifi_detail_chip.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, OverlayModule],
})
export class WifiDetailChip {
  private readonly clipboardService = inject(ClipboardService);
  private readonly destroyRef = inject(DestroyRef);

  /** Wi-Fi network configuration to display. */
  readonly wifi = input.required<DeviceWifi>();

  /** Whether the rich Wi-Fi detail overlay is currently open. */
  readonly isOpen = signal<boolean>(false);

  /** Whether the password in the overlay is unmasked. */
  readonly showPsk = signal<boolean>(false);

  /** Whether the password was copied to the clipboard recently. */
  readonly copiedPsk = signal<boolean>(false);

  /** Connected overlay positions for the tooltip card. */
  readonly tipPositions = TIP_POSITIONS;

  private closeTimer: ReturnType<typeof setTimeout> | null = null;
  private copyTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.close();
    });
  }

  /** Opens the detail overlay when the pointer enters the chip. */
  open() {
    if (!this.wifi().ssid) return;
    this.cancelClose();
    if (!this.isOpen()) {
      this.clearCopyTimer();
      this.showPsk.set(false);
      this.copiedPsk.set(false);
      this.isOpen.set(true);
    }
  }

  /** Cancels a scheduled overlay close when the pointer enters the overlay card. */
  cancelClose() {
    clearTimeout(this.closeTimer!);
    this.closeTimer = null;
  }

  /** Schedules closing the overlay with a short debounce on pointer leave. */
  scheduleClose() {
    this.cancelClose();
    this.closeTimer = setTimeout(() => {
      this.close();
    }, 200);
  }

  /** Closes the overlay immediately and resets transient state. */
  close() {
    this.cancelClose();
    this.clearCopyTimer();
    this.isOpen.set(false);
    this.showPsk.set(false);
    this.copiedPsk.set(false);
  }

  /** Toggles password visibility between masked dots and plain text. */
  togglePsk() {
    this.showPsk.update((v) => !v);
  }

  /** Copies the Wi-Fi password to the operator's clipboard. */
  copyPsk() {
    const psk = this.wifi().psk;
    if (!psk || !this.isOpen()) return;
    if (!this.clipboardService.copyToClipboard(psk)) return;
    this.copiedPsk.set(true);
    this.clearCopyTimer();
    this.copyTimer = setTimeout(() => {
      this.copiedPsk.set(false);
    }, 2000);
  }

  private clearCopyTimer() {
    clearTimeout(this.copyTimer!);
    this.copyTimer = null;
  }
}
