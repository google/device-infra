/**
 * @fileoverview Dialog listing the errors that the lab reported for a device.
 *
 * Follows the HostDebugDialog two-pane layout:
 * - Left: resizable sidebar listing the errors chronologically, shown only
 *   when there is more than one error.
 * - Right: expandable sections (all expanded by default) whose headers show
 *   color-coded ErrorId chips and whose bodies show the raw diagnostic text in
 *   the same format as the V5 console.
 */

import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
  QueryList,
  signal,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatExpansionModule} from '@angular/material/expansion';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Subscription} from 'rxjs';
import {take} from 'rxjs/operators';
import {
  DeviceError,
  ErrorId,
  FlattenedExceptionDetail,
} from '../../../core/models/common_models';
import {DEVICE_SERVICE} from '../../../core/services/device/device_service';
import {useCopyFeedback} from '../../composables/copy';
import {
  findActiveSection,
  revealActiveNavItem,
  SCROLL_SPY_PAUSE_MS,
  useResizableSidebar,
} from '../../composables/dialog_sidebar';
import {TooltipIfTruncatedDirective} from '../../directives/tooltip_if_truncated/tooltip_if_truncated';
import {SnackBarService} from '../../services/snackbar_service';
import {DatetimeTrigger} from '../datetime_trigger/datetime_trigger';

/** Data passed to DeviceErrorDialog. */
export interface DeviceErrorDialogData {
  /** ID of the device whose errors are shown. */
  deviceId: string;
  /** Host of the device. Only the device on this host is looked up. */
  hostName: string;
}

/**
 * ErrorId reduced to what the section header chips show, where an empty string
 * or undefined code means that the value is unset.
 */
export interface NormalizedErrorId {
  code?: number;
  name: string;
  type: string;
  namespace: string;
}

/** View model of one error section. */
export interface NormalizedDeviceError {
  /** Unique ID of the section, also used as its element ID. */
  id: string;
  /** 1-based position of the error in chronological order. */
  index: number;
  /** Section title, e.g. "#2 · 2026-11-16 11:28:21 UTC+8". */
  heading: string;
  /** Error ID shown as header chips, if known. */
  errorId?: NormalizedErrorId;
  /** Short description shown in the sidebar, e.g. the error name. */
  shortLabel: string;
  /** Diagnostic text shown in the section body, in the V5 format. */
  displayContent: string;
  /** One-line summary copied by the section header copy button. */
  headerCopyText: string;
  /** Text copied for this error by the Copy All button. */
  fullCopyText: string;
}

/** localStorage key remembering whether the sidebar is icon-only. */
export const DEVICE_ERROR_SIDEBAR_ICON_ONLY_STORAGE_KEY =
  'mh_fe_v6_device_error_dialog_sidebar_icon_only';

/** Matches the highlighted error in either sidebar mode. */
const ACTIVE_NAV_ITEM_SELECTOR = [
  '.device-error-dialog__rail-item-btn--active',
  '.device-error-dialog__sidebar-item--active',
].join(', ');

/** Time for an expanded section to render before scrolling to it, in ms. */
const SECTION_RENDER_DELAY_MS = 100;

/** Space kept above a section scrolled to from the sidebar, in pixels. */
const SECTION_SCROLL_MARGIN_PX = 16;

/**
 * Matches the error ID tag that Fusion appends to exception messages, in the
 * form `[NAMESPACE|TYPE|NAME|CODE]`, e.g.
 * `[MH|INFRA_ISSUE|DEVICE_INFRA_UNKNOWN_FAILURE|53901]`.
 */
const BRACKET_ERROR_ID_REGEX =
  /\[([A-Z0-9_]+)\|([A-Z0-9_]+)\|([A-Z0-9_]+)\|(-?\d+)\]/;

/** Matches every bracketed error ID tag in a line, with surrounding spaces. */
const BRACKET_ERROR_ID_TAGS_REGEX = new RegExp(
  `\\s*${BRACKET_ERROR_ID_REGEX.source}\\s*`,
  'g',
);

/** Error names that only tell that the actual error is unknown. */
const PLACEHOLDER_ERROR_NAMES = new Set(['UNKNOWN', 'UNKNOWN_ERROR_ID']);

/** Proto3 default of ErrorId.type, which means that it is unset. */
const UNSET_ERROR_TYPE = 'UNCLASSIFIED';

/** Proto3 default of ErrorId.namespace, which means that it is unset. */
const UNSET_ERROR_NAMESPACE = 'UNKNOWN';

/**
 * Returns a concise local timezone offset label matching MHFE V6's
 * DatetimeTrigger convention (e.g. "UTC", "UTC+8", "UTC-7", "UTC+5:30").
 */
export function getLocalTimezoneSuffix(date: Date): string {
  const offsetMinutes = -date.getTimezoneOffset();
  if (offsetMinutes === 0) {
    return 'UTC';
  }
  const offsetHours = Math.floor(Math.abs(offsetMinutes) / 60);
  const offsetMins = Math.abs(offsetMinutes) % 60;
  const sign = offsetMinutes > 0 ? '+' : '-';
  return `UTC${sign}${offsetHours}${
    offsetMins > 0 ? ':' + String(offsetMins).padStart(2, '0') : ''
  }`;
}

/**
 * Normalizes raw device errors into view models sorted chronologically.
 *
 * Section header chips show the first meaningful ErrorId, falling back to the
 * `[NAMESPACE|TYPE|NAME|CODE]` tag that Fusion puts into exception messages.
 * Section bodies show the raw V5 diagnostic text without any fallbacks.
 */
export function normalizeDeviceErrors(
  rawErrors?: DeviceError[],
): NormalizedDeviceError[] {
  return [...(rawErrors ?? [])]
    .sort((a, b) => Number(a.timestampMs ?? 0) - Number(b.timestampMs ?? 0))
    .map(normalizeDeviceError);
}

/** Normalizes the device error at `position` in chronological order. */
function normalizeDeviceError(
  {timestampMs, error}: DeviceError,
  position: number,
): NormalizedDeviceError {
  const index = position + 1;
  const timestampText = formatLocalTimestamp(timestampMs);
  const heading = `#${index} · ${timestampText}`;
  const summary = error?.summary;
  const firstLine = (summary?.message ?? '').split(/\r?\n/, 1)[0].trim();
  const message = firstLine.replace(BRACKET_ERROR_ID_TAGS_REGEX, ' ').trim();
  const errorId =
    normalizeHeaderErrorId(error?.userFacingCriticalErrorId) ??
    normalizeHeaderErrorId(summary?.errorId) ??
    normalizeHeaderErrorId(error?.criticalErrorId) ??
    parseBracketErrorId(firstLine);
  const displayContent = formatV5ErrorText(error, timestampText);

  return {
    id: `device-err-${position}`,
    index,
    heading,
    errorId,
    shortLabel:
      errorId?.name || shortClassName(summary?.classType?.className) || message,
    displayContent,
    headerCopyText: [heading, formatErrorIdTag(errorId), message]
      .filter(Boolean)
      .join(' '),
    fullCopyText: `=== Error #${index} (${timestampText}) ===\n${displayContent}`,
  };
}

/**
 * Formats an epoch milliseconds timestamp in the browser's local timezone with
 * an explicit offset, e.g. "2026-11-16 03:28:21 UTC" or
 * "2026-11-16 11:28:21 UTC+8".
 */
function formatLocalTimestamp(timestampMs?: string | number): string {
  const date = new Date(Number(timestampMs));
  if (Number.isNaN(date.getTime()) || date.getTime() <= 0) {
    return 'Unknown time';
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  const datePart = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  const timePart = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  return `${datePart} ${timePart} ${getLocalTimezoneSuffix(date)}`;
}

/**
 * Formats an error the way the V5 console shows it, using the raw field
 * values (including proto3 defaults) so that both consoles show the same text.
 */
function formatV5ErrorText(
  error: FlattenedExceptionDetail | undefined,
  timestampText: string,
): string {
  const errorId = error?.userFacingCriticalErrorId;
  const code = errorId?.code ?? 0;
  const name = errorId?.name ?? '';
  const namespace = errorId?.namespace || UNSET_ERROR_NAMESPACE;
  const type = errorId?.type || UNSET_ERROR_TYPE;
  // The double spaces after the colons match the V5 text.
  return [
    `Time: ${timestampText}`,
    `User Facing Critical Error ID:  (${code}) ${name} [${namespace}|${type}]`,
    `Error Name Stack Trace:  ${error?.errorNameStackTrace ?? ''}`,
    'StackTrace:',
    error?.completeStackTrace ?? '',
  ].join('\n');
}

/**
 * Returns the parts of `errorId` shown as header chips, or undefined if it
 * identifies no actual error. Omitted fields (ESF) and printed proto3 defaults
 * (Envoy) are treated alike.
 */
function normalizeHeaderErrorId(
  errorId?: ErrorId,
): NormalizedErrorId | undefined {
  const name = errorId?.name?.trim() ?? '';
  const code = errorId?.code ?? 0;
  if (!errorId || (code <= 0 && (!name || PLACEHOLDER_ERROR_NAMES.has(name)))) {
    return undefined;
  }
  return {
    code: code > 0 ? code : undefined,
    name,
    type: valueOrEmpty(errorId.type, UNSET_ERROR_TYPE),
    namespace: valueOrEmpty(errorId.namespace, UNSET_ERROR_NAMESPACE),
  };
}

/**
 * Parses the first Fusion `[NAMESPACE|TYPE|NAME|CODE]` tag in `text`, with the
 * same rules as the structured error IDs, so that a placeholder tag such as
 * `[UNKNOWN|UNCLASSIFIED|UNKNOWN_ERROR_ID|-1]` yields no error ID.
 */
function parseBracketErrorId(text: string): NormalizedErrorId | undefined {
  const match = text.match(BRACKET_ERROR_ID_REGEX);
  if (!match) {
    return undefined;
  }
  const [, namespace, type, name, code] = match;
  return normalizeHeaderErrorId({namespace, type, name, code: Number(code)});
}

/** Formats `errorId` as a bracketed tag, e.g. "[MH|INFRA_ISSUE|NAME|123]". */
function formatErrorIdTag(errorId?: NormalizedErrorId): string {
  const parts = [
    errorId?.namespace,
    errorId?.type,
    errorId?.name,
    errorId?.code?.toString(),
  ].filter(Boolean);
  return parts.length > 0 ? `[${parts.join('|')}]` : '';
}

/** Returns `value`, or an empty string if it is missing or `unsetValue`. */
function valueOrEmpty(value: string | undefined, unsetValue: string): string {
  return value && value !== unsetValue ? value : '';
}

/** Returns the simple name of a fully qualified class name. */
function shortClassName(className?: string): string {
  return className?.split('.').pop() ?? '';
}

/** Replays the 'highlight-flash' animation on `element`. */
function flashHighlight(element: HTMLElement) {
  element.classList.remove('highlight-flash');
  // Reading offsetWidth forces a reflow, so that adding the class back
  // restarts the animation.
  if (element.offsetWidth >= 0) {
    element.classList.add('highlight-flash');
  }
}

/** Dialog listing the errors that the lab reported for a device. */
@Component({
  selector: 'app-device-error-dialog',
  standalone: true,
  templateUrl: './device_error_dialog.ng.html',
  styleUrl: './device_error_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatExpansionModule,
    MatIconModule,
    MatTooltipModule,
    DatetimeTrigger,
    TooltipIfTruncatedDirective,
  ],
})
export class DeviceErrorDialog implements OnInit, OnDestroy {
  /** Opens the dialog for a device, sized like HostDebugDialog. */
  static open(
    dialog: MatDialog,
    data: DeviceErrorDialogData,
  ): MatDialogRef<DeviceErrorDialog> {
    return dialog.open(DeviceErrorDialog, {
      data,
      autoFocus: false,
      width: '90vw',
      height: '90vh',
      maxHeight: '90vh',
      maxWidth: '1280px',
    });
  }

  @ViewChild('dialogLayout') dialogLayout?: ElementRef<HTMLElement>;
  @ViewChild('sidebarEl') sidebarEl?: ElementRef<HTMLElement>;
  @ViewChild('mainContent') mainContent?: ElementRef<HTMLElement>;
  @ViewChildren('errorPanel', {read: ElementRef}) errorPanels!: QueryList<
    ElementRef<HTMLElement>
  >;

  readonly data = inject<DeviceErrorDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<DeviceErrorDialog>);
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly snackBar = inject(SnackBarService);

  /** Whether a device errors request is in flight. */
  readonly isLoading = signal(false);
  /** Whether at least one device errors request has succeeded. */
  readonly hasLoaded = signal(false);
  /** Whether the initial load failed and no request has succeeded yet. */
  readonly hasLoadError = signal(false);
  /** When the shown device errors were fetched. */
  readonly snapshotTimestamp = signal<Date | undefined>(undefined);
  /** Device errors in chronological order. */
  readonly normalizedErrors = signal<NormalizedDeviceError[]>([]);
  /** Whether there are several errors, which is when the sidebar is shown. */
  readonly hasMultipleErrors = computed(
    () => this.normalizedErrors().length > 1,
  );
  /** Which error sections are expanded, keyed by section ID. */
  readonly expandedErrors = signal<Record<string, boolean>>({});
  /** Section ID of the error in view, highlighted in the sidebar. */
  readonly activeErrorId = signal('');
  /** Whether all error sections are expanded. */
  readonly allExpanded = computed(() => {
    const expanded = this.expandedErrors();
    const keys = Object.keys(expanded);
    if (keys.length === 0) return false;
    return keys.every((key) => expanded[key]);
  });

  /**
   * Left sidebar that can be resized or collapsed into an icon-only rail. The
   * sidebar only exists for several errors, so the icon-only preference is
   * neither restored nor saved for a single error.
   */
  readonly sidebar = useResizableSidebar({
    storageKey: DEVICE_ERROR_SIDEBAR_ICON_ONLY_STORAGE_KEY,
    layoutWidth: () => this.dialogLayout?.nativeElement.clientWidth ?? 0,
    preferenceEnabled: this.hasMultipleErrors,
  });
  /** Copies error texts, showing a check mark on the used button. */
  readonly clipboard = useCopyFeedback();

  private fetchSubscription?: Subscription;
  /** Scroll-spy is paused until this epoch time while the dialog scrolls. */
  private suppressScrollSpyUntil = 0;
  /** Pending scroll to the error picked in the sidebar. */
  private navigateTimer?: ReturnType<typeof setTimeout>;

  ngOnInit() {
    this.fetchDeviceErrors();
  }

  ngOnDestroy() {
    this.fetchSubscription?.unsubscribe();
    clearTimeout(this.navigateTimer);
  }

  /**
   * Fetches the device errors. The dialog stays open when this fails, so that
   * the user can retry with the Refresh button.
   */
  fetchDeviceErrors(isRefresh = false) {
    this.fetchSubscription?.unsubscribe();
    this.isLoading.set(true);
    this.hasLoadError.set(false);

    this.fetchSubscription = this.deviceService
      .getDeviceErrors(this.data.deviceId, this.data.hostName)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          const errors = normalizeDeviceErrors(response.deviceErrors);
          this.normalizedErrors.set(errors);
          this.snapshotTimestamp.set(new Date());
          this.hasLoaded.set(true);
          this.isLoading.set(false);
          this.sidebar.restorePreference();

          const initialExpanded: Record<string, boolean> = {};
          for (const err of errors) {
            initialExpanded[err.id] = true;
          }
          this.expandedErrors.set(initialExpanded);
          if (!errors.some((err) => err.id === this.activeErrorId())) {
            this.activeErrorId.set(errors[0]?.id ?? '');
          }

          if (isRefresh) {
            this.snackBar.showSuccess('Device errors refreshed successfully');
          }
        },
        error: (error: {message?: string}) => {
          const action = isRefresh ? 'refresh' : 'get';
          this.snackBar.showError(
            `Failed to ${action} device errors for ${this.data.deviceId}: ${
              error.message || 'Unknown error'
            }`,
          );
          this.isLoading.set(false);
          this.hasLoadError.set(!this.hasLoaded());
        },
      });
  }

  setExpandedState(errorId: string, expanded: boolean) {
    this.expandedErrors.update((prev) => ({
      ...prev,
      [errorId]: expanded,
    }));
  }

  toggleExpandAll() {
    const nextState = !this.allExpanded();
    this.expandedErrors.update((prev) => {
      const updated = {...prev};
      for (const key of Object.keys(updated)) {
        updated[key] = nextState;
      }
      return updated;
    });
  }

  /** Expands the section of `errorId`, then scrolls it into view. */
  navigateToError(errorId: string) {
    this.activeErrorId.set(errorId);
    this.suppressScrollSpyUntil = Date.now() + SCROLL_SPY_PAUSE_MS;
    this.setExpandedState(errorId, true);

    clearTimeout(this.navigateTimer);
    this.navigateTimer = setTimeout(() => {
      this.navigateTimer = undefined;
      const mainEl = this.mainContent?.nativeElement;
      const panel = this.errorPanels.find(
        (panelRef) => panelRef.nativeElement.id === errorId,
      )?.nativeElement;
      if (!mainEl || !panel) {
        return;
      }
      this.suppressScrollSpyUntil = Date.now() + SCROLL_SPY_PAUSE_MS;
      mainEl.scrollTop = panel.offsetTop - SECTION_SCROLL_MARGIN_PX;
      flashHighlight(panel);
    }, SECTION_RENDER_DELAY_MS);
  }

  /** Highlights the sidebar item of the error scrolled into view. */
  onMainScroll() {
    const mainEl = this.mainContent?.nativeElement;
    if (!mainEl || Date.now() < this.suppressScrollSpyUntil) {
      return;
    }
    const panels = this.errorPanels.map((panel) => panel.nativeElement);
    const activeSection = findActiveSection(mainEl, panels);
    // No active section when there are no error sections, or when they fit in
    // the main pane so that it scrolls by at most a couple of pixels (e.g.
    // subpixel rounding). There is no scroll position to track then, so keep
    // the current highlight, which is set when the errors load or one is
    // picked in the sidebar.
    if (!activeSection) {
      return;
    }
    const {index, isAtTop} = activeSection;
    const errorId = this.normalizedErrors()[index]?.id;
    if (errorId && (errorId !== this.activeErrorId() || isAtTop)) {
      this.activeErrorId.set(errorId);
      revealActiveNavItem(
        this.sidebarEl?.nativeElement,
        ACTIVE_NAV_ITEM_SELECTOR,
        isAtTop,
      );
    }
  }

  copyAll() {
    const errors = this.normalizedErrors();
    if (errors.length === 0) return;
    const text = errors.map((err) => err.fullCopyText).join('\n\n');
    this.clipboard.copy(text, 'All device errors copied');
  }

  onClose() {
    this.dialogRef.close();
  }
}
