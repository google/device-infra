/**
 * @fileoverview Shared component that displays a datetime trigger.
 *
 * When clicked, it opens an overlay popover displaying the timestamp in multiple
 * important timezones (Local, Mountain View/PDT/PST, UTC, and Epoch)
 * with quick-copy-to-clipboard buttons.
 */

import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {ClipboardService} from '@deviceinfra/app/shared/services/clipboard_service';

/**
 * Component that renders a clickable datetime trigger showing the formatted
 * timestamp. Clicking it reveals a menu for comparing timezones.
 *
 * Highlights:
 * - Fully reactive using Angular Signals.
 * - Periodically updates the "Relative time" (e.g., '3 min ago') every second
 *   ONLY when the popover menu is open, saving browser resources.
 * - Handles baseline alignment natively inside inline text.
 */
@Component({
  selector: 'app-datetime-trigger',
  standalone: true,
  templateUrl: './datetime_trigger.ng.html',
  styleUrl: './datetime_trigger.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatMenuModule],
})
export class DatetimeTrigger implements OnChanges, OnDestroy {
  private readonly clipboardService = inject(ClipboardService);

  /**
   * The timestamp to display. Supports standard Date objects, Epoch numbers
   * (in ms), or standard ISO strings.
   */
  @Input({required: true}) timestamp!: Date | number | string;

  // Signals for reactive template bindings to prevent redundant rendering
  readonly formattedDate = signal('');
  readonly relativeTime = signal('');
  readonly localTime = signal('');
  readonly mtvTime = signal('');
  readonly utcTime = signal('');
  readonly epochTime = signal('');

  /** Signal tracking the copy-success state for each timezone row (key is row ID). */
  readonly copiedFields = signal<Record<string, boolean>>({});

  /** Timer ID tracking the relative-time ticks. */
  private updateIntervalId: ReturnType<typeof setInterval> | null = null;

  ngOnChanges(changes: SimpleChanges) {
    if (changes['timestamp']) {
      this.updateTimes();
    }
  }

  ngOnDestroy() {
    this.onMenuClosed();
  }

  /**
   * Triggered by (opened) event of mat-menu when the popover is shown.
   * Starts a 1-second interval to dynamically tick the relative time.
   */
  onMenuOpened() {
    this.updateRelativeTime(); // Recalculate immediately so it doesn't wait 1s
    this.updateIntervalId = setInterval(() => {
      this.updateRelativeTime();
    }, 1000);
  }

  /**
   * Triggered by (closed) event of mat-menu when the popover is closed or
   * in ngOnDestroy. Cleans up the timer to preserve cycles.
   */
  onMenuClosed() {
    if (this.updateIntervalId) {
      clearInterval(this.updateIntervalId);
      this.updateIntervalId = null;
    }
  }

  /**
   * Pre-formats the parsed timestamp in all target timezones.
   * Called once on init or when the timestamp input changes.
   */
  private updateTimes() {
    if (!this.timestamp) return;
    const date = new Date(this.timestamp);
    if (isNaN(date.getTime())) return;

    // Main trigger button display: "May 27, 2026, 12:56 AM PDT"
    this.formattedDate.set(
      date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        timeZoneName: 'short',
      })
    );

    this.updateRelativeTime();

    // Format for local timezone with numeric offset suffix (e.g. "09:43 AM UTC-7")
    this.localTime.set(
      `${this.formatTimezone(date, undefined, {timeZoneName: undefined})} ${this.getLocalTimezoneOffsetString(date)}`,
    );

    // Format for Mountain View timezone (standard operational time for Omnilab)
    this.mtvTime.set(this.formatTimezone(date, 'America/Los_Angeles'));

    // Format for UTC timezone (standard server-log reference)
    this.utcTime.set(this.formatTimezone(date, 'UTC'));

    // Raw Unix Epoch milliseconds
    this.epochTime.set(date.getTime().toString());
  }

  /** Recalculates the "time ago" string based on current wall time. */
  private updateRelativeTime() {
    if (!this.timestamp) return;
    const date = new Date(this.timestamp);
    if (isNaN(date.getTime())) return;
    this.relativeTime.set(this.timeAgo(date));
  }

  /**
   * Standard relative time rounding logic.
   * Converts milliseconds diff to human-friendly strings (seconds, mins, hours, days).
   */
  private timeAgo(date: Date): string {
    const now = new Date();
    const diffSeconds = Math.round((now.getTime() - date.getTime()) / 1000);
    const diffMinutes = Math.floor(diffSeconds / 60);
    const diffHours = Math.floor(diffMinutes / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffSeconds < 60) return `${diffSeconds} sec ago`;
    if (diffMinutes < 60) return `${diffMinutes} min ago`;
    if (diffHours < 24) return `${diffHours} hr ago`;
    return `${diffDays} days ago`;
  }

  /**
   * Formats the date in a specific IANA Time Zone name.
   * Falls back to browser's default locale if no timezone is specified.
   */
  private formatTimezone(
    date: Date,
    timeZone?: string,
    options: Intl.DateTimeFormatOptions = {},
  ): string {
    try {
      return date
        .toLocaleString('en-US', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          timeZoneName: 'short',
          timeZone,
          hour12: false,
          ...options,
        })
        .replace(',', '');
    } catch (e) {
      console.warn(`Timezone formatting failed for ${timeZone}:`, e);
      return 'Invalid Date';
    }
  }

  /**
   * Manually constructs standard offset strings (e.g., "UTC+5:30" or "UTC-7").
   * Safely handles fractional timezone offsets (like Indian Standard Time).
   */
  private getLocalTimezoneOffsetString(date: Date): string {
    const offsetMinutes = -date.getTimezoneOffset();
    const offsetHours = Math.floor(Math.abs(offsetMinutes) / 60);
    const offsetMins = Math.abs(offsetMinutes) % 60;
    const sign = offsetMinutes >= 0 ? '+' : '-';
    return `UTC${sign}${String(offsetHours).padStart(1, '0')}${
      offsetMins > 0 ? ':' + String(offsetMins).padStart(2, '0') : ''
    }`;
  }

  /**
   * Copies the given timezone text to clipboard and triggers a local
   * success checkmark icon for 1.5 seconds.
   *
   * Prevents event propagation to stop the popup menu from auto-closing.
   */
  copyTime(text: string, field: string, event: MouseEvent) {
    event.stopPropagation(); // CRITICAL: Prevents menu from closing on copy click
    if (this.clipboardService.copyToClipboard(text)) {
      this.copiedFields.update((prev) => ({...prev, [field]: true}));
      setTimeout(() => {
        this.copiedFields.update((prev) => ({...prev, [field]: false}));
      }, 1500);
    }
  }
}
