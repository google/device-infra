import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Input,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {EMPTY, Observable, Subject} from 'rxjs';
import {catchError, switchMap} from 'rxjs/operators';

import {
  DeviceChangeRecord,
  DeviceEventsResponse,
  DeviceInventoryRecord,
  DeviceRecoveryRecord,
} from '../../../../core/models/device_events';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';

/** Event category key. */
export type EventCategory =
  | 'status'
  | 'build'
  | 'host'
  | 'recovery'
  | 'inventory';

/** Category metadata for filter chips. */
export interface CategoryMeta {
  readonly key: EventCategory;
  readonly label: string;
  readonly icon: string;
  readonly description: string;
  readonly count: number;
  readonly selected: boolean;
}

/** A badge in the Details column ('state', 'alert', or 'tag'). */
export interface DetailsBadgePart {
  readonly type: 'badge';
  readonly text: string;
  readonly variant: 'state' | 'alert' | 'tag';
  readonly title?: string;
}

/** A single run inside a body-text phrase ('val' or 'word'). */
export interface DetailsPhraseRun {
  readonly text: string;
  readonly kind: 'val' | 'word';
  readonly title?: string;
}

/** A body-text phrase in the Details column. */
export interface DetailsPhrasePart {
  readonly type: 'phrase';
  readonly text: string;
  readonly runs: readonly DetailsPhraseRun[];
}

/** A part in the two-style Details column. */
export type DetailsPart = DetailsBadgePart | DetailsPhrasePart;

/** Normalized event row. */
export interface NormalizedEvent {
  readonly id: string;
  readonly category: EventCategory;
  kind:
    | 'statusChange'
    | 'outage'
    | 'restore'
    | 'build'
    | 'api'
    | 'host'
    | 'recovery'
    | 'inventory';
  readonly time: Date;
  readonly actor: string | null;
  readonly isFieldChange?: boolean;
  readonly fromState?: string;
  readonly toState?: string;
  readonly healthNote?: string;
  readonly fallbackDuration?: string;
  readonly from?: string;
  readonly to?: string;
  readonly apiFrom?: string;
  readonly apiTo?: string;
  readonly reason?: string;
  readonly note?: string;
  readonly body?: string;
  readonly isRelocation?: boolean;
  ongoing?: boolean;
  ongoingForMs?: number;
  relapseAfterMs?: number;
  downForMs?: number;
  endTime?: Date;
  title: string;
  icon: string;
  tone: '' | 'down' | 'up' | 'maint';
  parts: DetailsPart[];
  searchText: string;
}

/** Collapsible group of 3 or more consecutive build/API changes. */
export interface BuildGroupRow {
  readonly id: string;
  readonly kind: 'buildGroup';
  readonly category: 'build';
  readonly newestTime: Date;
  readonly oldestTime: Date;
  readonly dateRangeLabel: string;
  readonly children: readonly NormalizedEvent[];
  readonly chainParts: readonly DetailsPart[];
}

/** Display row rendered in the agenda event table. */
export type DisplayItem =
  | {
      readonly type: 'event';
      readonly row: NormalizedEvent;
      readonly showDate: boolean;
      readonly dateLabel: string;
      readonly timeLabel: string;
      readonly timeTooltip: string;
    }
  | {
      readonly type: 'child';
      readonly row: NormalizedEvent;
      readonly showDate: boolean;
      readonly dateLabel: string;
      readonly timeLabel: string;
      readonly timeTooltip: string;
    }
  | {
      readonly type: 'group';
      readonly row: BuildGroupRow;
      readonly expanded: boolean;
    };

/** Timeline outage segment in the summary card. */
export interface TimelineSegment {
  readonly eventId: string;
  readonly leftPct: number;
  readonly widthPct: number;
  readonly centerPct: number;
  readonly isMaintenance: boolean;
  readonly inspectKind: 'outage' | 'maint';
  readonly inspectTitle: string;
  readonly inspectBadge: string;
  readonly inspectTime: string;
  readonly inspectSub: string;
}

/** Timeline repair tick or relocation dot in the summary card. */
export interface TimelineMark {
  readonly eventId: string;
  readonly leftPct: number;
  readonly isMove: boolean;
  readonly inspectKind: 'repair' | 'move';
  readonly inspectTitle: string;
  readonly inspectBadge: string;
  readonly inspectTime: string;
  readonly inspectSub: string;
}

/** Timeline axis label. */
export interface TimelineAxisLabel {
  readonly text: string;
  readonly position: 'start' | 'end' | 'middle';
  readonly leftPct?: number;
}

/** Hover popover state for the summary timeline. */
export interface TimelineInspectState {
  readonly eventId: string;
  readonly kind: 'outage' | 'maint' | 'repair' | 'move';
  readonly title: string;
  readonly badge: string;
  readonly time: string;
  readonly sub: string;
  readonly leftPx: number;
}

/** Computed summary view model for the 90-day summary card. */
export interface SummaryViewModel {
  readonly windowDays: number;
  readonly nowTone: 'bad' | 'good' | '';
  readonly nowValue: string;
  readonly nowSub: string;
  readonly outageValue: string;
  readonly outageSub: string;
  readonly repairValue: string;
  readonly repairSub: string;
  readonly hasLameduck: boolean;
  readonly hasRepairs: boolean;
  readonly hasMoves: boolean;
  readonly barAriaLabel: string;
  readonly segments: readonly TimelineSegment[];
  readonly marks: readonly TimelineMark[];
  readonly axisLabels: readonly TimelineAxisLabel[];
}

const WINDOW_STEP_DAYS = 90;
const MIN_GROUP_SIZE = 3;
const RELAPSE_WINDOW_MS = 60 * 60 * 1000;
const PACIFIC = 'America/Los_Angeles';

const DOWN_STATES = new Set(['failed', 'missing', 'lameduck']);
const MAINTENANCE_STATES = new Set(['lameduck']);
const STATUS_ICONS: Record<string, string> = {
  failed: 'error_outline',
  missing: 'link_off',
  lameduck: 'pause_circle_outline',
};
const CONFIG_FIELDS: Record<string, {title: string; icon: string}> = {
  build: {title: 'Build changed', icon: 'system_update'},
  api: {title: 'API level changed', icon: 'android'},
  host: {title: 'Host changed', icon: 'dns'},
};

const CATEGORIES: Record<
  EventCategory,
  {label: string; icon: string; description: string}
> = {
  status: {
    label: 'Status & health',
    icon: 'monitor_heart',
    description: 'Out of service and back in service transitions',
  },
  build: {
    label: 'Build',
    icon: 'system_update',
    description: 'Build and Android API level changes',
  },
  host: {
    label: 'Host',
    icon: 'dns',
    description: 'Automatically detected host changes',
  },
  recovery: {
    label: 'Recovery',
    icon: 'build',
    description: 'Manual repair records logged by lab technicians',
  },
  inventory: {
    label: 'Inventory',
    icon: 'inventory_2',
    description: 'Asset registration, location moves, and decommission records',
  },
};

const CATEGORY_ORDER: readonly EventCategory[] = [
  'status',
  'build',
  'host',
  'recovery',
  'inventory',
];

const INVENTORY_SUBJECTS: Record<string, string> = {
  'Create Unit': 'Added to inventory',
  'Moved': 'Moved in inventory',
  'Imported from Mobile Harness': 'Imported to inventory',
  'Decommissioned': 'Decommissioned',
  'Decommission Unit': 'Decommissioned',
};
const INVENTORY_RELOCATION_SUBJECTS = new Set(['Moved']);
const INVENTORY_AUTOMATION_AUTHOR = 'Omnilab Omnilab';

const PRESERVED_WORDS = new Set([
  'Acroname',
  'ADB',
  'Android',
  'API',
  'Google',
  'LED',
  'Monsoon',
  'Odin',
  'OOBE',
  'PIN',
  'Tigertail',
  'TVC',
  'USB',
]);

function makeFormatter(
  options: Intl.DateTimeFormatOptions,
  locale = 'en-US',
): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat(locale, {timeZone: PACIFIC, ...options});
}

const timeFormatter = makeFormatter({hour: 'numeric', minute: '2-digit'});
const pointFormatter = makeFormatter({
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});
const tooltipFormatter = makeFormatter({
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  second: '2-digit',
  timeZoneName: 'short',
});
const dayFormatter = makeFormatter({month: 'short', day: 'numeric'});
const dayYearFormatter = makeFormatter({
  month: 'short',
  day: 'numeric',
  year: 'numeric',
});
const dayNumberFormatter = makeFormatter({day: 'numeric'});
const monthFormatter = makeFormatter({month: 'short'});
const monthNumberFormatter = makeFormatter({month: 'numeric'});
const yearFormatter = makeFormatter({year: 'numeric'});
const dayKeyFormatter = makeFormatter(
  {year: 'numeric', month: '2-digit', day: '2-digit'},
  'en-CA',
);

function badge(
  text: string,
  variant: 'state' | 'alert' | 'tag',
  title = '',
): DetailsBadgePart {
  return {type: 'badge', text, variant, ...(title ? {title} : {})};
}

function phrase(...runs: DetailsPhraseRun[]): DetailsPhrasePart {
  return {
    type: 'phrase',
    text: runs.map((r) => r.text).join(' '),
    runs,
  };
}

function changePhrase(
  fromText: string,
  toText: string,
  fromTitle = '',
  toTitle = '',
): DetailsPhrasePart {
  return phrase(
    {text: fromText, kind: 'val', ...(fromTitle ? {title: fromTitle} : {})},
    {text: 'to', kind: 'word'},
    {text: toText, kind: 'val', ...(toTitle ? {title: toTitle} : {})},
  );
}

function sentenceCase(text: string | undefined): string {
  return String(text || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((word, index) => {
      const bare = word.replace(/[()]/g, '');
      const keep =
        PRESERVED_WORDS.has(bare) ||
        /[0-9/.]/.test(word) ||
        (bare.length > 1 && bare === bare.toUpperCase());
      if (keep) return word;
      const lower = word.toLowerCase();
      return index === 0
        ? lower.charAt(0).toUpperCase() + lower.slice(1)
        : lower;
    })
    .join(' ');
}

function stateLabel(state: string | undefined): string {
  const lower = String(state || '')
    .trim()
    .toLowerCase();
  return lower ? lower.charAt(0).toUpperCase() + lower.slice(1) : '';
}

function isDown(state: string | undefined): boolean {
  return DOWN_STATES.has(
    String(state || '')
      .trim()
      .toLowerCase(),
  );
}

function shortHosts(fromHost: string, toHost: string): [string, string] {
  const a = String(fromHost || '').split('.');
  const b = String(toHost || '').split('.');
  let shared = 0;
  while (
    shared < a.length - 1 &&
    shared < b.length - 1 &&
    a[a.length - 1 - shared] === b[b.length - 1 - shared]
  ) {
    shared += 1;
  }
  return [
    a.slice(0, a.length - shared).join('.'),
    b.slice(0, b.length - shared).join('.'),
  ];
}

function formatDuration(ms: number): string {
  const totalMinutes = Math.max(0, Math.round(ms / 60000));
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) return hours > 0 ? `${days}d ${hours}h` : `${days}d`;
  if (hours > 0) return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
  return `${minutes}m`;
}

function sameTimeRank(event: NormalizedEvent): number {
  if (event.kind === 'restore') return 4;
  if (event.isFieldChange) return 3;
  if (event.category === 'recovery') return 2;
  if (event.kind === 'outage') return 1;
  return 0;
}

/**
 * Device Detail "Device Events" tab (Material 3).
 *
 * Renders the 90-day out-of-service summary card and timeline bar, category
 * filter chips, search box, collapsible build-change runs, and the two-style
 * Details column (`state`/`alert`/`tag` badges and `val`/`word` phrases).
 */
@Component({
  selector: 'app-device-events-tab',
  standalone: true,
  templateUrl: './device_events_tab.ng.html',
  styleUrl: './device_events_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
})
export class DeviceEventsTab implements OnInit {
  /** The device ID to fetch events for. */
  @Input({required: true}) deviceId!: string;
  /** Optional host name to disambiguate multi-host device IDs. */
  @Input() hostName = '';
  /** Optional observable trigger to reload events. */
  @Input() refreshTrigger$?: Observable<void>;
  /** Reference clock timestamp (defaults to Date.now(); overridable in tests). */
  @Input() referenceNowMs?: number;

  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly destroyRef = inject(DestroyRef);
  private readonly hostElement = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly loadTrigger$ = new Subject<number>();

  readonly windowDays = signal(WINDOW_STEP_DAYS);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly allEvents = signal<NormalizedEvent[]>([]);
  readonly serverHasOlderEvents = signal(false);
  readonly selectedCategories = signal<ReadonlySet<EventCategory>>(new Set());
  readonly query = signal('');
  readonly expandedGroups = signal<ReadonlySet<string>>(new Set());
  readonly activeInspect = signal<TimelineInspectState | null>(null);
  readonly flashedEventId = signal<string | null>(null);

  private nowMs(): number {
    return this.referenceNowMs ?? Date.now();
  }

  private asOfDateKey(): string {
    return dayKeyFormatter.format(new Date(this.nowMs()));
  }

  private dateKeyToUtcNoon(dateKey: string): Date {
    const [y, m, d] = dateKey.split('-').map(Number);
    return new Date(Date.UTC(y, m - 1, d, 12));
  }

  private dateKeyToSortTime(dateKey: string): Date {
    const [y, m, d] = dateKey.split('-').map(Number);
    return new Date(Date.UTC(y, m - 1, d, 8));
  }

  private addDays(dateKey: string, days: number): string {
    const date = this.dateKeyToUtcNoon(dateKey);
    date.setUTCDate(date.getUTCDate() + days);
    return date.toISOString().slice(0, 10);
  }

  private dayKey(date: Date): string {
    return dayKeyFormatter.format(date);
  }

  private dayLabel(date: Date): string {
    const sameYear =
      yearFormatter.format(date) === this.asOfDateKey().slice(0, 4);
    return (sameYear ? dayFormatter : dayYearFormatter).format(date);
  }

  private dayRangeLabel(oldest: Date, newest: Date): string {
    if (this.dayKey(oldest) === this.dayKey(newest)) {
      return this.dayLabel(newest);
    }
    const sameMonth =
      monthFormatter.format(oldest) === monthFormatter.format(newest) &&
      yearFormatter.format(oldest) === yearFormatter.format(newest);
    return sameMonth
      ? `${this.dayLabel(oldest)} – ${dayNumberFormatter.format(newest)}`
      : `${this.dayLabel(oldest)} – ${this.dayLabel(newest)}`;
  }

  private formatTimeAgo(date: Date): string {
    const minutes = Math.max(
      0,
      Math.round((this.nowMs() - date.getTime()) / 60000),
    );
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.round(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.round(hours / 24)}d ago`;
  }

  readonly sinceDateKey = computed(() =>
    this.addDays(this.asOfDateKey(), -this.windowDays()),
  );

  readonly sinceFormatted = computed(() =>
    dayYearFormatter.format(this.dateKeyToUtcNoon(this.sinceDateKey())),
  );

  readonly windowEvents = computed(() => {
    const since = this.dateKeyToSortTime(this.sinceDateKey());
    return this.allEvents().filter((event) => event.time >= since);
  });

  readonly hasOlderEvents = computed(() => {
    const since = this.dateKeyToSortTime(this.sinceDateKey());
    return (
      this.serverHasOlderEvents() ||
      this.allEvents().some((event) => event.time < since)
    );
  });

  readonly categories = computed<readonly CategoryMeta[]>(() => {
    const windowEvts = this.windowEvents();
    const selected = this.selectedCategories();
    return CATEGORY_ORDER.map((key) => ({
      key,
      label: CATEGORIES[key].label,
      icon: CATEGORIES[key].icon,
      description: CATEGORIES[key].description,
      count: windowEvts.filter((e) => e.category === key).length,
      selected: selected.has(key),
    }));
  });

  readonly visibleEvents = computed(() => {
    const q = this.query().trim().toLowerCase();
    const selected = this.selectedCategories();
    return this.windowEvents().filter((event) => {
      if (selected.size > 0 && !selected.has(event.category)) return false;
      return !q || event.searchText.includes(q);
    });
  });

  readonly displayRows = computed<readonly DisplayItem[]>(() => {
    const grouped = this.groupBuildRuns(this.visibleEvents());
    const expanded = this.expandedGroups();
    const out: DisplayItem[] = [];
    let previousDay: string | null = null;
    for (const row of grouped) {
      if (row.kind === 'buildGroup') {
        const isExpanded = expanded.has(row.id);
        out.push({type: 'group', row, expanded: isExpanded});
        previousDay = null;
        if (isExpanded) {
          let childDay: string | null = null;
          for (const child of row.children) {
            const day = this.dayKey(child.time);
            out.push({
              type: 'child',
              row: child,
              showDate: day !== childDay,
              dateLabel: this.dayLabel(child.time),
              timeLabel: timeFormatter.format(child.time),
              timeTooltip: `${tooltipFormatter.format(child.time)} (${this.formatTimeAgo(child.time)})`,
            });
            childDay = day;
          }
        }
        continue;
      }
      const day = this.dayKey(row.time);
      out.push({
        type: 'event',
        row,
        showDate: day !== previousDay,
        dateLabel: this.dayLabel(row.time),
        timeLabel: timeFormatter.format(row.time),
        timeTooltip: `${tooltipFormatter.format(row.time)} (${this.formatTimeAgo(row.time)})`,
      });
      previousDay = day;
    }
    return out;
  });

  readonly filtersActive = computed(
    () => this.selectedCategories().size > 0 || this.query().trim() !== '',
  );

  readonly firstLoad = computed(
    () => this.loading() && this.allEvents().length === 0,
  );

  readonly footerSummary = computed(() => {
    const winCount = this.windowEvents().length;
    if (winCount === 0) return '';
    const visCount = this.visibleEvents().length;
    const noun = winCount === 1 ? 'event' : 'events';
    const since = this.sinceFormatted();
    return this.filtersActive()
      ? `${visCount} of ${winCount} ${noun} since ${since}`
      : `${winCount} ${noun} since ${since}`;
  });

  readonly summary = computed<SummaryViewModel>(() => {
    const windowStart = this.dateKeyToSortTime(this.sinceDateKey()).getTime();
    const windowEnd = this.nowMs();
    const span = Math.max(1, windowEnd - windowStart);
    const pct = (t: number) =>
      Math.min(100, Math.max(0, ((t - windowStart) / span) * 100));
    const inWindow = (event: NormalizedEvent) => {
      const t = event.time.getTime();
      return t >= windowStart && t <= windowEnd;
    };

    const allEvts = this.allEvents();
    const periods = allEvts
      .filter((event) => event.kind === 'outage')
      .map((event) => ({
        event,
        start: event.time.getTime(),
        end: event.ongoing
          ? windowEnd
          : event.endTime
            ? event.endTime.getTime()
            : null,
      }))
      .filter(
        (
          period,
        ): period is {event: NormalizedEvent; start: number; end: number} =>
          period.end !== null &&
          period.end > windowStart &&
          period.start < windowEnd,
      );

    const downMs = periods.reduce(
      (sum, period) =>
        sum +
        Math.min(period.end, windowEnd) -
        Math.max(period.start, windowStart),
      0,
    );
    const repairs = allEvts.filter(
      (event) => event.category === 'recovery' && inWindow(event),
    );
    const moves = allEvts.filter(
      (event) =>
        (event.kind === 'host' || event.isRelocation) && inWindow(event),
    );
    const ongoing = allEvts.find(
      (event) => event.kind === 'outage' && event.ongoing,
    );
    const lastRestore = allEvts.find((event) => event.kind === 'restore');
    const isMaintenance = (period: {event: NormalizedEvent}) =>
      MAINTENANCE_STATES.has(String(period.event.toState || '').toLowerCase());

    let nowTone: 'bad' | 'good' | '' = '';
    let nowValue = 'In service';
    let nowSub = '';
    if (ongoing) {
      nowTone = 'bad';
      nowValue = `Out of service for ${formatDuration(ongoing.ongoingForMs ?? 0)}`;
      nowSub = [stateLabel(ongoing.toState), ongoing.healthNote]
        .filter(Boolean)
        .join(' · ');
    } else if (lastRestore) {
      nowTone = 'good';
      nowValue = `In service for ${formatDuration(windowEnd - lastRestore.time.getTime())}`;
    }

    const percent = Math.round((downMs / span) * 100);
    const outageValue = `${periods.length} ${periods.length === 1 ? 'time' : 'times'}`;
    const outageSub = periods.length
      ? `${formatDuration(downMs)} in total (${percent}%)`
      : '';

    const lastRepair = repairs[0];
    const repairValue = String(repairs.length);
    const repairSub = lastRepair
      ? `Last: ${lastRepair.title}${lastRepair.actor ? ` by ${lastRepair.actor}` : ''}, ${this.dayLabel(lastRepair.time)}`
      : '';

    const segments: TimelineSegment[] = periods.map((period) => {
      const leftPct = pct(Math.max(period.start, windowStart));
      const widthPct = Math.max(
        0.4,
        pct(Math.min(period.end, windowEnd)) - leftPct,
      );
      const centerPct = Math.min(100, Math.max(0, leftPct + widthPct / 2));
      const maint = isMaintenance(period);
      const stateText = stateLabel(period.event.toState);
      const inspectTitle = `Out of service · ${stateText}`;
      const inspectBadge = period.event.ongoing
        ? `Ongoing for ${formatDuration(period.event.ongoingForMs ?? 0)}`
        : formatDuration(period.end - period.start);
      const inspectTime = period.event.ongoing
        ? `Since ${pointFormatter.format(period.event.time)}`
        : `${pointFormatter.format(period.event.time)} – ${pointFormatter.format(period.event.endTime!)}`;
      const subParts: string[] = [];
      if (period.event.relapseAfterMs != null) {
        subParts.push(
          `Relapsed ${formatDuration(period.event.relapseAfterMs)} after recovery`,
        );
      }
      if (period.event.healthNote) subParts.push(period.event.healthNote);
      return {
        eventId: period.event.id,
        leftPct,
        widthPct,
        centerPct,
        isMaintenance: maint,
        inspectKind: maint ? 'maint' : 'outage',
        inspectTitle,
        inspectBadge,
        inspectTime,
        inspectSub: subParts.join(' · '),
      };
    });

    const toMark = (event: NormalizedEvent, isMove: boolean): TimelineMark => {
      const leftPct = pct(event.time.getTime());
      const detail = event.parts.map((part) => part.text).join(' · ');
      const inspectSub = [detail, event.actor ? `by ${event.actor}` : '']
        .filter(Boolean)
        .join(' · ');
      return {
        eventId: event.id,
        leftPct,
        isMove,
        inspectKind: isMove ? 'move' : 'repair',
        inspectTitle: event.title,
        inspectBadge: isMove ? 'Moved' : 'Repair',
        inspectTime: pointFormatter.format(event.time),
        inspectSub,
      };
    };

    const marks: TimelineMark[] = [
      ...repairs.map((e) => toMark(e, false)),
      ...moves.map((e) => toMark(e, true)),
    ];

    const axisLabels: TimelineAxisLabel[] = [
      {text: this.dayLabel(new Date(windowStart)), position: 'start'},
    ];
    const startDate = new Date(windowStart);
    let year = Number(yearFormatter.format(startDate));
    let month = Number(monthNumberFormatter.format(startDate));
    for (;;) {
      month += 1;
      if (month > 12) {
        month = 1;
        year += 1;
      }
      const monthStart = Date.UTC(year, month - 1, 1, 8);
      if (monthStart >= windowEnd) break;
      const position = pct(monthStart);
      if (position < 5 || position > 95) continue;
      axisLabels.push({
        text: monthFormatter.format(new Date(monthStart)),
        position: 'middle',
        leftPct: position,
      });
    }
    axisLabels.push({
      text: this.dayLabel(new Date(windowEnd)),
      position: 'end',
    });

    const winDays = this.windowDays();
    return {
      windowDays: winDays,
      nowTone,
      nowValue,
      nowSub,
      outageValue,
      outageSub,
      repairValue,
      repairSub,
      hasLameduck: periods.some(isMaintenance),
      hasRepairs: repairs.length > 0,
      hasMoves: moves.length > 0,
      barAriaLabel: `${periods.length} out of service periods in the last ${winDays} days`,
      segments,
      marks,
      axisLabels,
    };
  });

  ngOnInit(): void {
    this.loadTrigger$
      .pipe(
        switchMap((days) => {
          this.loading.set(true);
          this.error.set('');
          return this.deviceService
            .getDeviceEvents(this.deviceId, this.hostName, days)
            .pipe(
              catchError(() => {
                this.error.set('Failed to load device events.');
                this.loading.set(false);
                return EMPTY;
              }),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((response) => {
        this.allEvents.set(this.normalizeResponse(response));
        this.serverHasOlderEvents.set(Boolean(response.hasOlderEvents));
        this.loading.set(false);
      });

    this.loadTrigger$.next(this.windowDays());

    if (this.refreshTrigger$) {
      this.refreshTrigger$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          this.loadTrigger$.next(this.windowDays());
        });
    }
  }

  toggleCategory(category: EventCategory): void {
    const next = new Set(this.selectedCategories());
    if (next.has(category)) {
      next.delete(category);
    } else {
      next.add(category);
    }
    this.selectedCategories.set(next);
  }

  clearCategoryFilters(): void {
    this.selectedCategories.set(new Set());
  }

  clearAllFilters(): void {
    this.selectedCategories.set(new Set());
    this.query.set('');
  }

  onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.query.set(input?.value ?? '');
  }

  clearSearch(): void {
    this.query.set('');
  }

  toggleGroup(groupId: string): void {
    const next = new Set(this.expandedGroups());
    if (next.has(groupId)) {
      next.delete(groupId);
    } else {
      next.add(groupId);
    }
    this.expandedGroups.set(next);
  }

  loadOlderEvents(): void {
    if (this.loading()) return;
    const nextDays = this.windowDays() + WINDOW_STEP_DAYS;
    this.windowDays.set(nextDays);
    this.loadTrigger$.next(nextDays);
  }

  showSegmentInspect(seg: TimelineSegment, timelineEl: HTMLElement): void {
    this.setInspect(
      {
        eventId: seg.eventId,
        kind: seg.inspectKind,
        title: seg.inspectTitle,
        badge: seg.inspectBadge,
        time: seg.inspectTime,
        sub: seg.inspectSub,
      },
      seg.centerPct,
      timelineEl,
    );
  }

  showMarkInspect(mark: TimelineMark, timelineEl: HTMLElement): void {
    this.setInspect(
      {
        eventId: mark.eventId,
        kind: mark.inspectKind,
        title: mark.inspectTitle,
        badge: mark.inspectBadge,
        time: mark.inspectTime,
        sub: mark.inspectSub,
      },
      mark.leftPct,
      timelineEl,
    );
  }

  hideInspect(): void {
    this.activeInspect.set(null);
  }

  selectTimelineEvent(eventId: string): void {
    const targetEvent = this.allEvents().find((ev) => ev.id === eventId);
    const selected = this.selectedCategories();
    if (
      targetEvent &&
      selected.size > 0 &&
      !selected.has(targetEvent.category)
    ) {
      const next = new Set(selected);
      next.add(targetEvent.category);
      this.selectedCategories.set(next);
    }
    this.flashedEventId.set(eventId);
    const root = this.hostElement.nativeElement;
    const row = root.querySelector<HTMLElement>(
      `[data-row-event-id="${eventId}"]`,
    );
    row?.scrollIntoView?.({behavior: 'smooth', block: 'center'});
  }

  private setInspect(
    base: Omit<TimelineInspectState, 'leftPx'>,
    centerPct: number,
    timelineEl: HTMLElement,
  ): void {
    const width = timelineEl.clientWidth || 800;
    const rawX = (centerPct / 100) * width;
    const halfCard = 130;
    const clampedX = Math.min(width - halfCard, Math.max(halfCard, rawX));
    this.activeInspect.set({...base, leftPx: clampedX});
  }

  private normalizeResponse(response: DeviceEventsResponse): NormalizedEvent[] {
    const events: NormalizedEvent[] = [
      ...this.normalizeChanges(response.changes ?? []),
      ...this.normalizeRecovery(response.recoveryRecords ?? []),
      ...this.normalizeInventory(response.inventoryRecords ?? []),
    ];
    this.annotateStatus(events);
    return events
      .map((event) => this.decorate(event))
      .sort(
        (a, b) =>
          b.time.getTime() - a.time.getTime() ||
          sameTimeRank(b) - sameTimeRank(a),
      );
  }

  private normalizeChanges(
    rows: readonly DeviceChangeRecord[],
  ): NormalizedEvent[] {
    return rows.map((row, idx) => {
      const iso = row.changeTime ?? '';
      const field = row.field ?? 'status';
      const fromVal = row.fromValue ?? '';
      const toVal = row.toValue ?? '';
      const metaText = String(row.meta ?? '').trim();
      const base = {
        id: `${this.deviceId}-${field}-${idx}-${iso}`,
        time: new Date(iso),
        actor: null,
        title: '',
        icon: '',
        tone: '' as const,
        parts: [] as DetailsPart[],
        searchText: '',
      };
      if (field === 'status') {
        const notes = metaText
          .replace(/^Ongoing\s*·?\s*/, '')
          .split('·')
          .map((note) => note.trim())
          .filter(Boolean);
        return {
          ...base,
          category: 'status',
          kind: 'statusChange',
          fromState: fromVal,
          toState: toVal,
          healthNote: notes
            .filter((note) => !/^(Down for|Relapsed)\b/.test(note))
            .join(', '),
          fallbackDuration:
            notes.find((note) => /^Down for\b/.test(note)) || '',
          ongoing: false,
        };
      }
      const apiChange = /^API\s+(\S+)\s*(?:→|to)\s*(\S+)$/.exec(metaText);
      const kind: 'build' | 'api' | 'host' =
        field === 'host' ? 'host' : field === 'api' ? 'api' : 'build';
      return {
        ...base,
        category: field === 'host' ? 'host' : 'build',
        kind,
        isFieldChange: true,
        from: fromVal,
        to: toVal,
        apiFrom: apiChange ? apiChange[1] : '',
        apiTo: apiChange ? apiChange[2] : '',
      };
    });
  }

  private normalizeRecovery(
    rows: readonly DeviceRecoveryRecord[],
  ): NormalizedEvent[] {
    return rows.map((row, idx) => {
      const iso = row.createTime ?? '';
      return {
        id: `${this.deviceId}-recovery-${idx}-${iso}`,
        category: 'recovery',
        kind: 'recovery',
        time: new Date(iso),
        title: sentenceCase(row.recoveryAction) || 'Recovery record',
        reason: sentenceCase(row.offlineReason),
        note: (row.note || '').trim(),
        actor: row.author || null,
        icon: 'build',
        tone: '',
        parts: [],
        searchText: '',
      };
    });
  }

  private normalizeInventory(
    rows: readonly DeviceInventoryRecord[],
  ): NormalizedEvent[] {
    return rows.map((row, idx) => {
      const iso = row.eventTime ?? '';
      const subject = row.subject ?? '';
      const author = row.author ?? '';
      const isAuto = !author || author === INVENTORY_AUTOMATION_AUTHOR;
      return {
        id: `${this.deviceId}-inventory-${idx}-${iso}`,
        category: 'inventory',
        kind: 'inventory',
        time: new Date(iso),
        title: INVENTORY_SUBJECTS[subject] || subject || 'Inventory update',
        body: (row.body || '').trim(),
        isRelocation: INVENTORY_RELOCATION_SUBJECTS.has(subject),
        actor: isAuto ? null : author,
        icon: 'inventory_2',
        tone: '',
        parts: [],
        searchText: '',
      };
    });
  }

  private annotateStatus(events: NormalizedEvent[]): void {
    let openOutage: NormalizedEvent | null = null;
    let lastRestore: NormalizedEvent | null = null;
    events
      .filter((event) => event.category === 'status')
      .sort((a, b) => a.time.getTime() - b.time.getTime())
      .forEach((event) => {
        const fromDown = isDown(event.fromState);
        const toDown = isDown(event.toState);
        if (!fromDown && toDown) {
          event.kind = 'outage';
          if (
            lastRestore &&
            event.time.getTime() - lastRestore.time.getTime() <=
              RELAPSE_WINDOW_MS
          ) {
            event.relapseAfterMs =
              event.time.getTime() - lastRestore.time.getTime();
          }
          openOutage = event;
        } else if (fromDown && !toDown) {
          event.kind = 'restore';
          if (openOutage) {
            event.downForMs = event.time.getTime() - openOutage.time.getTime();
            openOutage.endTime = event.time;
          }
          openOutage = null;
          lastRestore = event;
        }
      });
    if (openOutage) {
      const ongoingEvent = openOutage as NormalizedEvent;
      ongoingEvent.ongoing = true;
      ongoingEvent.ongoingForMs = this.nowMs() - ongoingEvent.time.getTime();
    }
  }

  private decorateStatus(event: NormalizedEvent): void {
    const toLower = String(event.toState || '')
      .trim()
      .toLowerCase();
    if (event.kind === 'outage') {
      event.title = 'Out of service';
      event.icon = STATUS_ICONS[toLower] || 'error_outline';
      event.tone = MAINTENANCE_STATES.has(toLower) ? 'maint' : 'down';
      event.parts = [badge(stateLabel(event.toState), 'state')];
      if (event.ongoing) {
        event.parts.push(
          badge(
            `Ongoing for ${formatDuration(event.ongoingForMs ?? 0)}`,
            'alert',
          ),
        );
      }
      if (event.relapseAfterMs != null) {
        event.parts.push(
          badge(
            `Relapsed ${formatDuration(event.relapseAfterMs)} after recovery`,
            'alert',
          ),
        );
      }
      if (event.healthNote) {
        event.parts.push(phrase({text: event.healthNote, kind: 'word'}));
      }
      return;
    }
    if (event.kind === 'restore') {
      event.title = 'Back in service';
      event.icon = 'check_circle_outline';
      event.tone = 'up';
      const recorded = /^Down for\s+(.+)$/i.exec(event.fallbackDuration || '');
      const duration =
        event.downForMs != null
          ? formatDuration(event.downForMs)
          : recorded
            ? recorded[1]
            : '';
      event.parts = duration
        ? [
            phrase(
              {text: 'Down for', kind: 'word'},
              {text: duration, kind: 'val'},
            ),
          ]
        : [];
      return;
    }
    const toDown = isDown(event.toState);
    event.title = 'Status changed';
    event.icon = toDown
      ? STATUS_ICONS[toLower] || 'error_outline'
      : 'swap_horiz';
    event.tone = toDown ? 'down' : '';
    event.parts = [
      badge(stateLabel(event.fromState), 'state'),
      phrase({text: 'to', kind: 'word'}),
      badge(stateLabel(event.toState), 'state'),
    ];
    if (event.healthNote) {
      event.parts.push(phrase({text: event.healthNote, kind: 'word'}));
    }
  }

  private decorate(event: NormalizedEvent): NormalizedEvent {
    if (event.category === 'status') {
      this.decorateStatus(event);
    } else if (event.isFieldChange) {
      const field = CONFIG_FIELDS[event.kind] || {
        title: 'Configuration changed',
        icon: 'tune',
      };
      event.title = field.title;
      event.icon = field.icon;
      event.tone = '';
      if (event.kind === 'host') {
        const [fromShort, toShort] = shortHosts(
          event.from ?? '',
          event.to ?? '',
        );
        event.parts = [
          changePhrase(fromShort, toShort, event.from ?? '', event.to ?? ''),
        ];
      } else {
        event.parts = [changePhrase(event.from ?? '', event.to ?? '')];
        if (event.apiFrom && event.apiTo) {
          event.parts.push(
            badge(`API ${event.apiFrom} to ${event.apiTo}`, 'tag'),
          );
        }
      }
    } else if (event.category === 'recovery') {
      event.icon = 'build';
      event.tone = '';
      event.parts = [];
      if (event.reason) {
        event.parts.push(badge(event.reason, 'tag', `Reason: ${event.reason}`));
      }
      if (event.note) {
        event.parts.push(phrase({text: `“${event.note}”`, kind: 'word'}));
      }
    } else {
      event.icon = 'inventory_2';
      event.tone = '';
      const lead = /^(Deployed to|Mounted in cabinet)\s+(.+)$/.exec(
        event.body || '',
      );
      if (lead) {
        event.parts = [
          phrase({text: lead[1], kind: 'word'}, {text: lead[2], kind: 'val'}),
        ];
      } else {
        event.parts = event.body
          ? [phrase({text: event.body, kind: 'val'})]
          : [];
      }
    }
    event.searchText = [
      event.title,
      ...event.parts.map((part) => part.text),
      event.actor || '',
      event.from || '',
      event.to || '',
      CATEGORIES[event.category]?.label || '',
    ]
      .join(' ')
      .toLowerCase();
    return event;
  }

  private groupBuildRuns(
    events: readonly NormalizedEvent[],
  ): Array<NormalizedEvent | BuildGroupRow> {
    const rows: Array<NormalizedEvent | BuildGroupRow> = [];
    let run: NormalizedEvent[] = [];
    const flush = () => {
      if (run.length >= MIN_GROUP_SIZE) {
        const builds = run.filter((child) => child.kind === 'build');
        const chainParts = builds.length
          ? [
              changePhrase(
                builds[builds.length - 1].from ?? '',
                builds[0].to ?? '',
              ),
            ]
          : [];
        rows.push({
          id: `group-${run[run.length - 1].id}`,
          kind: 'buildGroup',
          category: 'build',
          newestTime: run[0].time,
          oldestTime: run[run.length - 1].time,
          dateRangeLabel: this.dayRangeLabel(
            run[run.length - 1].time,
            run[0].time,
          ),
          children: run,
          chainParts,
        });
      } else {
        rows.push(...run);
      }
      run = [];
    };
    for (const event of events) {
      if (event.kind === 'build' || event.kind === 'api') {
        run.push(event);
      } else {
        flush();
        rows.push(event);
      }
    }
    flush();
    return rows;
  }
}
