import {
  afterRenderEffect,
  computed,
  DestroyRef,
  Directive,
  effect,
  ElementRef,
  inject,
  input,
  linkedSignal,
  Signal,
  signal,
  untracked,
} from '@angular/core';

/**
 * Placeholder row standing in for the rows hidden above (or below) the rendered
 * window. Hosts render it as a single empty `<tr>` whose cell is `heightPx`
 * tall, which keeps the scrollbar sized for the whole page while only the
 * windowed rows exist in the DOM.
 */
export interface TableWindowSpacer {
  readonly vsSpacer: true;
  /** Stable tracking key so the spacer row is reused across window shifts. */
  readonly key: string;
  /** Height in px covering the hidden rows. */
  readonly heightPx: number;
}

/** Half-open row index range `[start, end)` currently rendered. */
export interface TableWindowRange {
  readonly start: number;
  readonly end: number;
}

/** Type guard separating spacer rows from the host's own row items. */
export function isTableWindowSpacer(item: unknown): item is TableWindowSpacer {
  return (
    typeof item === 'object' &&
    item !== null &&
    (item as {vsSpacer?: unknown}).vsSpacer === true
  );
}

/** Tracking key of the spacer standing in for the rows above the window. */
export const TABLE_WINDOW_TOP_SPACER_KEY = '__vs_top__';

/** Tracking key of the spacer standing in for the rows below the window. */
export const TABLE_WINDOW_BOTTOM_SPACER_KEY = '__vs_bottom__';

/** Rows rendered beyond each edge of the visible area. */
const DEFAULT_OVERSCAN_ROWS = 10;

/** Pages at or below this many rows are rendered in full, exactly as before windowing existed. */
const DEFAULT_THRESHOLD_ROWS = 100;

/** Viewport height assumed until the host element has been laid out and measured. */
const DEFAULT_VIEWPORT_ESTIMATE_PX = 800;

/** Row height assumed when the host passes no estimate (the default-density flat row). */
const DEFAULT_ITEM_HEIGHT_ESTIMATE_PX = 52;

/** Finds the first real (non-spacer) body row of either a mat-table or a plain table. */
const DEFAULT_MEASURE_SELECTOR = 'tbody tr:not(.vs-spacer-row)';

interface RangeSource {
  readonly count: number;
  readonly active: boolean;
  readonly itemHeight: number;
  readonly scrollTop: number;
  readonly viewportHeight: number;
  readonly overscan: number;
}

/**
 * Windows ("virtual scrolls") the rows of a table that lives inside the host
 * scroll container, without touching the table markup itself.
 *
 * The host keeps its own scroll container, sticky header, and row templates;
 * it only iterates `windowedItems()` instead of its full row array and renders
 * a {@link TableWindowSpacer} as an empty fixed-height row. Because the spacer
 * rows are real table rows (no `transform`), the native scrollbar, sticky
 * `<th>`, horizontal scrolling, and `mat-table` column definitions all keep
 * working unchanged.
 *
 * Input: the full row array (`appTableWindow`), a row-height estimate, and
 * optional tuning. Output: `windowedItems()` = `[topSpacer?] + rows[start, end)
 * + [bottomSpacer?]`. The directive measures the first rendered row after each
 * render and corrects the estimate, so CSS drift (e.g. a checkbox stretching a
 * 32px row to 37px) cannot desynchronize the spacer math. Below `threshold`
 * rows the full array is passed through untouched.
 *
 * Usage:
 * ```html
 * <div class="scroller" [appTableWindow]="rows()" #win="appTableWindow"
 *      [itemHeight]="52" [scrollResetKey]="pageData()">
 *   <table>
 *     <tbody>
 *       @for (item of win.windowedItems(); track item.key) {
 *         @if (isSpacer(item)) {
 *           <tr class="vs-spacer-row"><td colspan="…" [style.height.px]="item.heightPx"></td></tr>
 *         } @else { … }
 *       }
 *     </tbody>
 *   </table>
 * </div>
 * ```
 */
@Directive({
  selector: '[appTableWindow]',
  standalone: true,
  exportAs: 'appTableWindow',
  host: {
    '(scroll)': 'onScroll()',
  },
})
export class TableWindowDirective<T extends {readonly key: string}> {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Full row array of the current page; only a window of it is rendered. */
  readonly items = input.required<readonly T[]>({alias: 'appTableWindow'});

  /** Initial row height estimate in px; replaced by the measured height after the first render. */
  readonly itemHeight = input<number>(DEFAULT_ITEM_HEIGHT_ESTIMATE_PX);

  /** Extra rows rendered beyond each edge of the visible area. */
  readonly overscan = input<number>(DEFAULT_OVERSCAN_ROWS);

  /** Row count above which windowing activates; smaller pages render in full. */
  readonly threshold = input<number>(DEFAULT_THRESHOLD_ROWS);

  /** CSS selector (relative to the host) of a real row whose height is measured. */
  readonly measureSelector = input<string>(DEFAULT_MEASURE_SELECTOR);

  /**
   * Any value whose identity change means "this is a new page of rows" (e.g.
   * the response object): the scroller jumps back to the top so the user does
   * not land mid-way into the next page, sort order, or page size.
   */
  readonly scrollResetKey = input<unknown>(undefined);

  /**
   * Whether to keep auto-layout column widths from jittering as the window
   * moves. With `table-layout: auto` a column is as wide as its widest
   * *rendered* cell, so every window shift could re-flow the whole table.
   * When on, each header cell's `min-width` is ratcheted up to the widest
   * width seen so far (never down), so columns only ever grow and settle
   * after a few windows; the ratchet resets when the host width changes or
   * windowing turns off.
   */
  readonly stabilizeColumns = input<boolean>(true);

  private readonly scrollTop = signal(0);

  /** Host `clientHeight`; 0 until the ResizeObserver has reported a layout. */
  private readonly viewportHeight = signal(0);

  /** Host `clientWidth`; a change releases the column-width ratchet (see `ratchetedAtWidth`). */
  private readonly viewportWidth = signal(0);

  /** Host width the ratcheted header widths were measured at. */
  private ratchetedAtWidth = 0;

  /** Widest width seen per header cell (keyed by its class list) while windowing. */
  private readonly columnMinWidths = new Map<string, number>();

  /** `offsetHeight` of the first rendered real row, or null before the first measurement. */
  private readonly measuredItemHeight = signal<number | null>(null);

  /** Row height driving the window math: the measured height when known, else the estimate. */
  readonly effectiveItemHeight = computed<number>(
    () => this.measuredItemHeight() ?? Math.max(1, this.itemHeight()),
  );

  /** Whether rows are being windowed (page larger than `threshold`). */
  readonly isActive = computed<boolean>(
    () => this.items().length > this.threshold(),
  );

  /**
   * Rendered row range. Recomputed from the scroll position with hysteresis:
   * the range only moves once the visible rows get within half an overscan of
   * either edge, so plain scrolling is compositor-only work and Angular
   * re-renders rows every few rows of travel instead of on every scroll event.
   */
  readonly range: Signal<TableWindowRange> = linkedSignal<
    RangeSource,
    TableWindowRange
  >({
    source: () => ({
      count: this.items().length,
      active: this.isActive(),
      itemHeight: this.effectiveItemHeight(),
      scrollTop: this.scrollTop(),
      viewportHeight: this.viewportHeight() || DEFAULT_VIEWPORT_ESTIMATE_PX,
      overscan: this.overscan(),
    }),
    computation: (src, previous) => {
      if (!src.active) {
        return {start: 0, end: src.count};
      }
      // A stale scroll offset (e.g. a smaller page replacing a scrolled one) is
      // clamped to the content extent, as the browser will clamp `scrollTop`.
      const maxScrollTop = Math.max(
        0,
        src.count * src.itemHeight - src.viewportHeight,
      );
      const scrollTop = Math.min(src.scrollTop, maxScrollTop);
      const firstVisible = Math.floor(scrollTop / src.itemHeight);
      const lastVisible = Math.min(
        src.count,
        Math.ceil((scrollTop + src.viewportHeight) / src.itemHeight),
      );

      const prev = previous?.value;
      const prevSrc = previous?.source;
      if (
        prev &&
        prevSrc &&
        prevSrc.active &&
        prevSrc.count === src.count &&
        prevSrc.itemHeight === src.itemHeight &&
        prevSrc.overscan === src.overscan
      ) {
        const minBuffer = Math.ceil(src.overscan / 2);
        const topCovered =
          prev.start === 0 || firstVisible - prev.start >= minBuffer;
        const bottomCovered =
          prev.end >= src.count || prev.end - lastVisible >= minBuffer;
        if (topCovered && bottomCovered) {
          return prev;
        }
      }
      return {
        start: Math.max(0, firstVisible - src.overscan),
        end: Math.min(src.count, lastVisible + src.overscan),
      };
    },
  });

  /** Height in px of the rows hidden above the rendered window. */
  readonly topSpacerPx = computed<number>(
    () => this.range().start * this.effectiveItemHeight(),
  );

  /** Height in px of the rows hidden below the rendered window. */
  readonly bottomSpacerPx = computed<number>(
    () =>
      Math.max(0, this.items().length - this.range().end) *
      this.effectiveItemHeight(),
  );

  /**
   * Rows for the host to render: the full array when windowing is inactive,
   * otherwise `[topSpacer?] + items[start, end) + [bottomSpacer?]`.
   *
   * Example for a 5,000-row page scrolled to the middle (52px rows):
   * `range() = {start: 2490, end: 2523}` yields a top spacer of 129,480px, the
   * 33 real rows 2490…2522, and a bottom spacer of 128,804px — 35 table rows
   * in the DOM that still scroll like 5,000.
   */
  readonly windowedItems = computed<ReadonlyArray<T | TableWindowSpacer>>(
    () => {
      const items = this.items();

      // Small pages (≤ threshold) are passed through untouched: the host
      // renders exactly what it rendered before windowing existed, and the
      // array identity is preserved so `mat-table` does not diff anything.
      if (!this.isActive()) {
        return items;
      }

      // `range()` is the half-open index window the scroll position asks for
      // (visible rows plus overscan on both sides, see the hysteresis rules).
      const {start, end} = this.range();
      const out: Array<T | TableWindowSpacer> = [];

      // One empty row standing in for every row above the window. Its height
      // is `start × rowHeight`, so the rows below sit at the same scroll
      // offset as if the hidden rows were really there. Skipped at the top of
      // the page, where there is nothing to stand in for.
      const topPx = this.topSpacerPx();
      if (topPx > 0) {
        out.push({
          vsSpacer: true,
          key: TABLE_WINDOW_TOP_SPACER_KEY,
          heightPx: topPx,
        });
      }

      // The rows that really exist in the DOM. The host tracks them by their
      // own key, so a window shift of a few rows reuses the overlapping rows
      // and only creates / destroys the rows that entered or left the window.
      for (let i = start; i < end; i++) {
        out.push(items[i]);
      }

      // The counterpart below the window: `(count − end) × rowHeight` keeps
      // the scrollable height (and therefore the native scrollbar) equal to
      // the full page. Skipped once the window reaches the last row.
      const bottomPx = this.bottomSpacerPx();
      if (bottomPx > 0) {
        out.push({
          vsSpacer: true,
          key: TABLE_WINDOW_BOTTOM_SPACER_KEY,
          heightPx: bottomPx,
        });
      }

      // The two spacers reuse constant keys, so across window shifts they are
      // the same two DOM rows with an updated height rather than new rows.
      return out;
    },
  );

  constructor() {
    const el = this.host.nativeElement;

    if (typeof ResizeObserver !== 'undefined') {
      const resizeObserver = new ResizeObserver(() => {
        // Only record the size here: layout changes inside a ResizeObserver
        // callback trigger "ResizeObserver loop" errors, so the ratchet reset
        // happens in the render effect below instead.
        this.viewportHeight.set(el.clientHeight);
        this.viewportWidth.set(el.clientWidth);
      });
      resizeObserver.observe(el);
      inject(DestroyRef).onDestroy(() => {
        resizeObserver.disconnect();
      });
    }

    // Measure a real row after every render that could change its height: new
    // rows, a window shift, a density (estimate) change, or a selector change.
    afterRenderEffect(() => {
      this.range();
      this.items();
      this.itemHeight();
      const row = el.querySelector<HTMLElement>(this.measureSelector());
      const heightPx = row?.offsetHeight ?? 0;
      if (heightPx > 0) {
        untracked(() => {
          this.measuredItemHeight.set(heightPx);
        });
      }
      if (this.stabilizeColumns() && this.isActive()) {
        // A horizontal resize (e.g. a side nav toggle) invalidates the widths
        // pinned so far: let the table lay itself out again, then pin anew.
        const viewportWidth = this.viewportWidth();
        if (viewportWidth !== this.ratchetedAtWidth) {
          this.ratchetedAtWidth = viewportWidth;
          this.resetColumnWidths(el);
        }
        this.ratchetColumnWidths(el);
      } else if (this.columnMinWidths.size > 0) {
        this.resetColumnWidths(el);
      }
    });

    effect(() => {
      this.scrollResetKey();
      untracked(() => {
        this.scrollToTop();
      });
    });
  }

  /** Scrolls the host back to the first row. */
  scrollToTop(): void {
    const el = this.host.nativeElement;
    if (el.scrollTop !== 0) {
      el.scrollTop = 0;
    }
    this.scrollTop.set(0);
  }

  protected onScroll(): void {
    this.scrollTop.set(this.host.nativeElement.scrollTop);
  }

  /** Raises each header cell's `min-width` to the widest width it has rendered at so far. */
  private ratchetColumnWidths(el: HTMLElement): void {
    for (const th of Array.from(el.querySelectorAll<HTMLElement>('thead th'))) {
      const key = th.className;
      const width = th.getBoundingClientRect().width;
      if (width > (this.columnMinWidths.get(key) ?? 0) + 0.5) {
        this.columnMinWidths.set(key, width);
        th.style.minWidth = `${width}px`;
      }
    }
  }

  /** Drops the ratcheted header widths so the table lays itself out freely again. */
  private resetColumnWidths(el: HTMLElement): void {
    if (this.columnMinWidths.size === 0) {
      return;
    }
    this.columnMinWidths.clear();
    for (const th of Array.from(el.querySelectorAll<HTMLElement>('thead th'))) {
      th.style.minWidth = '';
    }
  }
}
