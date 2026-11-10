import {
  CdkVirtualScrollViewport,
  VirtualScrollStrategy,
} from '@angular/cdk/scrolling';
import {
  AfterRenderRef,
  ApplicationRef,
  Injectable,
  afterNextRender,
  inject,
  signal,
  untracked,
} from '@angular/core';
import {Observable, Subject} from 'rxjs';
import {distinctUntilChanged} from 'rxjs/operators';

/** Single visual row height matching `.log-line { line-height: 20px }` in CSS. */
const LINE_HEIGHT_PX = 20;

/**
 * Total horizontal padding inside `.log-viewer-viewport` (`24px` left + `24px`
 * right), subtracted from `clientWidth` to get the text wrapping width.
 */
const HORIZONTAL_PADDING_PX = 48;

/** Approximate glyph advance width for 13px monospace log text. */
const APPROX_CHAR_WIDTH_PX = 7.8;

/** Fallback wrap column count before the viewport has a laid-out width. */
const DEFAULT_CHARS_PER_ROW = 80;

/** Fallback viewport height (`max-height: 552px`) before layout measurement. */
const DEFAULT_VIEWPORT_HEIGHT_PX = 552;

/**
 * Minimum offscreen pixel cushion above and below the visible viewport.
 * As long as both buffers stay `>= 400px`, scrolling reuses the existing DOM
 * slice without triggering `*cdkVirtualFor` template updates.
 */
const MIN_BUFFER_PX = 400;

/**
 * Target offscreen pixel cushion when a buffer drops below `MIN_BUFFER_PX`.
 * Expanding to `800px` gives `400px` of pure GPU-composited scroll travel
 * before the next DOM range refill is needed.
 */
const MAX_BUFFER_PX = 800;

/**
 * Virtual scroll strategy for word-wrapped, variable-height log lines.
 *
 * ### Why `FixedSizeVirtualScrollStrategy` (`itemSize="20"`) Cannot Be Used
 * In the normal 1-row-per-item case, CDK computes item positions with `O(1)`
 * multiplication/division (`topY = i * 20`, `index = floor(scrollOffset / 20)`).
 * With word-wrap enabled (`white-space: pre-wrap; word-break: break-all`), a
 * single long log line can wrap across dozens of visual rows (e.g., `1,680px`).
 * When that line scrolls above the buffer and unmounts from the DOM, replacing
 * its `1,680px` DOM height with only `+20px` of `translateY` yanks every visible
 * line below upward by `-1,660px`.
 *
 * ### How This Strategy Generalizes the Standard Virtual Scroll Algorithm
 * It keeps CDK's 3-layer DOM structure (`.cdk-virtual-scroll-viewport`,
 * `.cdk-virtual-scroll-spacer`, and `.cdk-virtual-scroll-content-wrapper`) and
 * buffer hysteresis (`MIN_BUFFER_PX` / `MAX_BUFFER_PX`), replacing only two
 * operations:
 * 1. `i * 20` -> Prefix-sum array `offsets[i] = sum(height[0 .. i - 1])`
 *    rebuilt in `O(N)` (`rebuildOffsets()`).
 * 2. `floor(y / 20)` -> `O(log N)` upper-bound binary search on `offsets[]`
 *    (`findIndexAtOffset()`).
 * 3. Initial heights are estimated from line length and refined with exact
 *    browser `HTMLElement.offsetHeight` measurements after each render pass
 *    (`onContentRendered()`).
 */
@Injectable()
export class VariableHeightVirtualScrollStrategy
  implements VirtualScrollStrategy
{
  private readonly scrolledIndexChangeSubject = new Subject<number>();

  /** Emits the index of the topmost visible log line whenever it changes. */
  readonly scrolledIndexChange: Observable<number> =
    this.scrolledIndexChangeSubject.pipe(distinctUntilChanged());

  /**
   * Reactive total content height in pixels across all log lines.
   * Consumed by `StreamingLogViewerComponent` to size compact log containers.
   */
  readonly totalContentHeight = signal<number>(0);

  private readonly appRef = inject(ApplicationRef);
  private viewport: CdkVirtualScrollViewport | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private pendingMeasureRef: AfterRenderRef | null = null;

  /** Current logical log lines (`1` entry = `1` `<div class="log-line">`). */
  private lines: readonly string[] = [];

  /**
   * Sparse cache of exact rendered DOM heights (`index -> el.offsetHeight`)
   * measured at the current viewport width. Unvisited indices fall back to
   * `estimateHeight()`.
   */
  private readonly measuredHeights = new Map<number, number>();

  /**
   * Prefix-sum Y-coordinate table of length `lines.length`.
   * `offsets[i]` is the pixel distance from the top of line `0` to the top of
   * line `i` (`offsets[0] === 0`, strictly increasing).
   */
  private readonly offsets: number[] = [];

  /** Sum of heights across all `lines` (`offsets[N - 1] + height[N - 1]`). */
  private totalHeightPx = 0;

  /** Last observed `viewportEl.clientWidth` used to detect wrap-width changes. */
  private lastWidthPx = 0;

  /** Last observed `viewportEl.clientHeight` used to detect dynamic height changes. */
  private lastHeightPx = 0;

  /** Returns whether `offsets` currently reflects the exact `lines` array. */
  isTrackingLines(lines: readonly string[]): boolean {
    return this.lines === lines;
  }

  /** Returns the top Y-coordinate in pixels for logical line `index`. */
  getOffsetForIndex(index: number): number {
    if (index <= 0 || this.offsets.length === 0) {
      return 0;
    }
    return index >= this.offsets.length
      ? this.totalHeightPx
      : this.offsets[index];
  }

  /**
   * Returns the best known height in pixels for logical line `index`:
   * exact rendered `offsetHeight` if previously measured, otherwise estimated.
   */
  getHeightForIndex(index: number): number {
    if (index < 0 || index >= this.lines.length) {
      return 0;
    }
    return (
      this.measuredHeights.get(index) ??
      this.estimateHeight(this.lines[index], this.getCharsPerRow())
    );
  }

  /**
   * Called by `CdkVirtualScrollViewport` when the viewport initializes.
   * (Part of the `VirtualScrollStrategy` interface; TypeScript disallows
   * `override` here because `VirtualScrollStrategy` is an `interface`, not a
   * base class.)
   */
  attach(viewport: CdkVirtualScrollViewport): void {
    this.viewport = viewport;
    const viewportEl = viewport.elementRef.nativeElement;
    this.lastWidthPx = viewportEl.clientWidth;
    this.lastHeightPx = viewportEl.clientHeight;

    // Horizontal width changes alter how many rows each line wraps into, while
    // dynamic `[style.height.px]` changes alter the visible vertical window.
    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => {
        const width = viewportEl.clientWidth;
        const height = viewportEl.clientHeight;
        if (width > 0 && width !== this.lastWidthPx) {
          this.lastWidthPx = width;
          this.lastHeightPx = height;
          untracked(() => {
            // Anchor on the line currently at the top of the viewport. Every
            // height is re-estimated at the new wrap width, so keeping the raw
            // pixel `scrollTop` would land the user on a different line.
            const anchorIdx = this.findIndexAtOffset(
              viewport.measureScrollOffset(),
            );
            const prevRange = viewport.getRenderedRange();
            this.measuredHeights.clear();
            this.rebuildOffsets();
            viewport.scrollToOffset(this.getOffsetForIndex(anchorIdx));
            this.updateRenderedRange(true);
            // Measure already-reflowed DOM synchronously only when
            // `{start, end}` stayed identical (the DOM still holds that exact
            // slice); otherwise `children[k]` would be attributed to the wrong
            // line index. The post-render pass covers the changed-range case.
            const nextRange = viewport.getRenderedRange();
            if (
              nextRange.start === prevRange.start &&
              nextRange.end === prevRange.end
            ) {
              this.onContentRendered();
            }
            this.scheduleMeasure();
          });
        } else if (height > 0 && height !== this.lastHeightPx) {
          // CDK's `ViewportRuler` only listens to `window` resize, so refresh
          // `_viewportSize` and buffer ranges when element height changes.
          this.lastHeightPx = height;
          untracked(() => {
            this.viewport?.checkViewportSize();
          });
        }
      });
      this.resizeObserver.observe(viewportEl);
    }

    untracked(() => {
      this.rebuildOffsets();
      this.updateRenderedRange(true);
      this.scheduleMeasure();
    });
  }

  /** Called by `CdkVirtualScrollViewport` on destroy to clean up observers. */
  detach(): void {
    this.pendingMeasureRef?.destroy();
    this.pendingMeasureRef = null;
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.scrolledIndexChangeSubject.complete();
    this.viewport = null;
  }

  /**
   * Updates the tracked log lines when streaming chunks arrive or reset.
   *
   * At a fixed viewport width, `<div class="log-line">{{ line }}</div>` height
   * depends solely on its string content. Retaining `measuredHeights` only for
   * indices whose string is strictly unchanged (`===`) handles append-only
   * polling, trailing partial-line growth, and `reset: true` replacements
   * (including replacements with `>=` lines and an identical first line).
   */
  setLines(nextLines: readonly string[]): void {
    untracked(() => {
      if (nextLines === this.lines) {
        return;
      }
      for (const idx of this.measuredHeights.keys()) {
        if (idx >= nextLines.length || nextLines[idx] !== this.lines[idx]) {
          this.measuredHeights.delete(idx);
        }
      }
      this.lines = nextLines;
      this.rebuildOffsets();
      this.updateRenderedRange(true);
      // `CdkVirtualScrollViewport.setRenderedRange` skips `onContentRendered()`
      // whenever `{start, end}` is unchanged (e.g., a 1-line wrapped log
      // replacing `'Loading logs...'` or trailing partial-line growth).
      this.scheduleMeasure();
    });
  }

  /** Called by `CdkVirtualScrollViewport` on every native scroll event. */
  onContentScrolled(): void {
    untracked(() => {
      this.updateRenderedRange(false);
    });
  }

  /** Called by `CdkVirtualScrollViewport` when the `*cdkVirtualFor` length changes. */
  onDataLengthChanged(): void {
    untracked(() => {
      this.rebuildOffsets();
      this.updateRenderedRange(true);
    });
  }

  /**
   * Called by `CdkVirtualScrollViewport` (inside `afterNextRender`) right after
   * `*cdkVirtualFor` renders a new `[range.start, range.end)` slice into
   * `.cdk-virtual-scroll-content-wrapper`.
   *
   * Measures each mounted `<div class="log-line">` via `offsetHeight` so exact
   * word-break, tab-stop, and wide CJK glyph wrapping replaces initial
   * character-count estimates before those lines ever scroll out of view.
   */
  onContentRendered(): void {
    untracked(() => {
      if (!this.viewport) {
        return;
      }
      const viewportEl = this.viewport.elementRef.nativeElement;
      const wrapper = viewportEl.querySelector<HTMLElement>(
        '.cdk-virtual-scroll-content-wrapper',
      );
      const range = this.viewport.getRenderedRange();
      const children = wrapper?.children;

      // Bail out if the viewport is hidden (`clientWidth <= 0` would report bogus
      // heights) or if the DOM child count does not yet match the active range
      // `[range.start, range.end)` (e.g., before Angular flushes the view).
      if (
        !children ||
        viewportEl.clientWidth <= 0 ||
        children.length !== range.end - range.start ||
        range.end > this.lines.length
      ) {
        return;
      }

      const scrollOffset = this.viewport.measureScrollOffset();
      const prevViewportSize = this.getViewportHeightPx();
      const prevMaxScroll = Math.max(0, this.totalHeightPx - prevViewportSize);
      const wasAtBottom =
        range.end === this.lines.length &&
        prevMaxScroll > 0 &&
        scrollOffset >= prevMaxScroll - 2;
      const firstVisibleIdx = this.findIndexAtOffset(scrollOffset);
      let deltaAboveViewport = 0;
      let changed = false;

      // `children[k]` corresponds 1-to-1 with logical line `range.start + k`.
      for (let k = 0; k < children.length; k++) {
        const idx = range.start + k;
        const actualHeight = (children[k] as HTMLElement).offsetHeight;
        const prevHeight = this.getHeightForIndex(idx);
        if (actualHeight > 0 && actualHeight !== prevHeight) {
          this.measuredHeights.set(idx, actualHeight);
          changed = true;
          // Track height changes occurring strictly above the visible window
          // (happens when scrolling upward into previously unmeasured lines).
          if (idx < firstVisibleIdx) {
            deltaAboveViewport += actualHeight - prevHeight;
          }
        }
      }

      if (!changed) {
        return;
      }

      // Recompute prefix-sum `offsets[]` and update wrapper `translateY` to
      // match the refined height sum above `range.start`.
      this.rebuildOffsets();
      this.viewport.setRenderedContentOffset(
        this.getOffsetForIndex(range.start),
      );

      // Bottom-pin anchor: if the viewport was already pinned to the bottom
      // (`initialScrollPosition="bottom"` or scrolled to end) and newly mounted
      // bottom lines `[firstVisibleIdx .. N - 1]` measured taller than
      // estimated (`deltaAboveViewport === 0`), advance `scrollTop` to the new
      // `maxScroll` so the latest lines stay visible at the bottom.
      if (wasAtBottom) {
        const nextMaxScroll = Math.max(
          0,
          this.totalHeightPx - this.getViewportHeightPx(),
        );
        this.viewport.scrollToOffset(nextMaxScroll);
      } else if (deltaAboveViewport !== 0 && scrollOffset > 0) {
        // Upward-scroll anchor: if an offscreen line in the top buffer measured
        // taller or shorter than estimated, `offsets[firstVisibleIdx]` shifted
        // by `deltaAboveViewport`. Shift `scrollTop` by the exact same delta so
        // the line the user is currently reading stays stationary on screen.
        this.viewport.scrollToOffset(
          Math.max(0, scrollOffset + deltaAboveViewport),
        );
      }
      this.updateRenderedRange(false);
    });
  }

  /**
   * Required by the `VirtualScrollStrategy` interface and invoked by
   * `CdkVirtualScrollViewport.setRenderedContentOffset()` after `translateY`
   * updates. Intentional no-op (matching CDK's `FixedSizeVirtualScrollStrategy`)
   * because this strategy itself drives all `setRenderedContentOffset` updates.
   */
  onRenderedOffsetChanged(): void {}

  /**
   * Required by the `VirtualScrollStrategy` interface; delegated to by
   * `CdkVirtualScrollViewport.scrollToIndex(index, behavior)` when a caller
   * programmatically jumps to a specific log line index.
   */
  scrollToIndex(index: number, behavior: ScrollBehavior): void {
    if (!this.viewport || this.offsets.length === 0) {
      return;
    }
    const clamped = Math.max(0, Math.min(index, this.offsets.length - 1));
    this.viewport.scrollToOffset(this.offsets[clamped], behavior);
    if (behavior === 'auto') {
      untracked(() => {
        this.updateRenderedRange(false);
      });
    }
  }

  /**
   * Binary-searches `offsets` in `O(log N)` for the unique line index `i`
   * covering `offsetPx` (`offsets[i] <= offsetPx < offsets[i + 1]`).
   */
  findIndexAtOffset(offsetPx: number): number {
    const len = this.offsets.length;
    if (len <= 1 || offsetPx <= 0) {
      return 0;
    }
    if (offsetPx >= this.totalHeightPx) {
      return len - 1;
    }
    let low = 0;
    let high = len - 1;
    while (low <= high) {
      const mid = (low + high) >>> 1;
      if (this.offsets[mid] <= offsetPx) {
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return Math.max(0, high);
  }

  /**
   * Schedules a coalesced post-render `onContentRendered()` pass via
   * `ApplicationRef.injector` so DOM `offsetHeight` is still measured when
   * `CdkVirtualScrollViewport.setRenderedRange` sees an unchanged `{start, end}`
   * or when `ResizeObserver` runs outside `NgZone`.
   */
  private scheduleMeasure(): void {
    if (this.pendingMeasureRef || !this.viewport) {
      return;
    }
    this.pendingMeasureRef = afterNextRender(
      () => {
        this.pendingMeasureRef = null;
        this.onContentRendered();
      },
      {injector: this.appRef.injector, manualCleanup: true},
    );
  }

  /**
   * Reads the live vertical height of `.cdk-virtual-scroll-viewport` directly
   * from `clientHeight` because CDK's cached `getViewportSize()` only updates
   * automatically on `window` resize, not when `[style.height.px]` changes.
   */
  private getViewportHeightPx(): number {
    return (
      this.viewport?.elementRef.nativeElement.clientHeight ||
      this.viewport?.getViewportSize() ||
      DEFAULT_VIEWPORT_HEIGHT_PX
    );
  }

  /** Estimates how many monospace characters fit on one visual row. */
  private getCharsPerRow(): number {
    const width = this.viewport?.elementRef.nativeElement.clientWidth ?? 0;
    if (width <= 0) {
      return DEFAULT_CHARS_PER_ROW;
    }
    return Math.max(
      1,
      Math.floor((width - HORIZONTAL_PADDING_PX) / APPROX_CHAR_WIDTH_PX),
    );
  }

  /** Estimates wrapped pixel height for `line` prior to DOM measurement. */
  private estimateHeight(line: string, charsPerRow: number): number {
    return Math.max(1, Math.ceil(line.length / charsPerRow)) * LINE_HEIGHT_PX;
  }

  /**
   * Rebuilds the `offsets[]` prefix-sum coordinate table and updates the
   * viewport spacer height (`totalHeightPx`).
   *
   * Runs in `< 0.2ms` even for 100k lines because it is a flat numeric loop.
   */
  private rebuildOffsets(): void {
    const charsPerRow = this.getCharsPerRow();
    this.offsets.length = this.lines.length;
    let y = 0;
    for (let i = 0; i < this.lines.length; i++) {
      this.offsets[i] = y;
      y +=
        this.measuredHeights.get(i) ??
        this.estimateHeight(this.lines[i], charsPerRow);
    }
    this.totalHeightPx = y;
    this.totalContentHeight.set(y);

    if (this.viewport) {
      this.viewport.setTotalContentSize(y);
      // Also apply height directly to `.cdk-virtual-scroll-spacer` so callers
      // invoking `viewport.scrollTo({bottom: 0})` inside `requestAnimationFrame`
      // observe the updated `scrollHeight` immediately before next CD flush.
      const spacer =
        this.viewport.elementRef.nativeElement.querySelector<HTMLElement>(
          '.cdk-virtual-scroll-spacer',
        );
      if (spacer && this.viewport.orientation !== 'horizontal') {
        spacer.style.height = `${y}px`;
      }
    }
  }

  /**
   * Computes and applies the mounted `ListRange [start, end)` and wrapper
   * `translateY` (`offsets[start]`) for the current scroll position.
   *
   * - Fast path (`!needsRefill`): If offscreen buffers above and below the
   *   visible window are both `>= MIN_BUFFER_PX` (`400px`), keeps the current
   *   `ListRange` untouched so native scrolling causes zero DOM churn.
   * - Refill path (`needsRefill`): Expands the rendered slice `[start, end)` to
   *   cover `[scrollOffset - MAX_BUFFER_PX, scrollOffset + viewportSize + MAX_BUFFER_PX]`
   *   and shifts `.cdk-virtual-scroll-content-wrapper` down by `offsets[start]`,
   *   matching the exact height of unmounted lines `[0 .. start)` for a `0px`
   *   visual shift.
   */
  private updateRenderedRange(force: boolean): void {
    if (!this.viewport) {
      return;
    }
    const count = Math.min(this.offsets.length, this.viewport.getDataLength());
    if (count === 0) {
      this.viewport.setRenderedRange({start: 0, end: 0});
      this.viewport.setRenderedContentOffset(0);
      this.scrolledIndexChangeSubject.next(0);
      return;
    }

    const viewportSize = this.getViewportHeightPx();
    const maxScroll = Math.max(0, this.totalHeightPx - viewportSize);
    const scrollOffset = Math.max(
      0,
      Math.min(this.viewport.measureScrollOffset(), maxScroll),
    );
    this.scrolledIndexChangeSubject.next(this.findIndexAtOffset(scrollOffset));

    // Check remaining offscreen pixel cushions for the currently mounted slice.
    const cur = this.viewport.getRenderedRange();
    const startBuffer = scrollOffset - this.getOffsetForIndex(cur.start);
    const endBuffer =
      this.getOffsetForIndex(cur.end) - (scrollOffset + viewportSize);

    const needsRefill =
      force ||
      cur.start === cur.end ||
      cur.end > count ||
      (startBuffer < MIN_BUFFER_PX && cur.start > 0) ||
      (endBuffer < MIN_BUFFER_PX && cur.end < count);

    if (!needsRefill) {
      this.viewport.setRenderedContentOffset(this.offsets[cur.start]);
      return;
    }

    // Binary-search `offsets[]` to mount `MAX_BUFFER_PX` (`800px`) of lines
    // above and below the visible `[scrollOffset, scrollOffset + viewportSize]` window.
    const start = this.findIndexAtOffset(
      Math.max(0, scrollOffset - MAX_BUFFER_PX),
    );
    const targetEndPx = scrollOffset + viewportSize + MAX_BUFFER_PX;
    const end =
      targetEndPx >= this.totalHeightPx
        ? count
        : Math.min(count, this.findIndexAtOffset(targetEndPx) + 1);

    this.viewport.setRenderedRange({start, end});
    this.viewport.setRenderedContentOffset(this.offsets[start]);
  }
}
