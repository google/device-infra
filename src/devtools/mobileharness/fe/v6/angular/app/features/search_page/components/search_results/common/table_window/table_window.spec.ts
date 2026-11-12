import {Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';

import {
  isTableWindowSpacer,
  TABLE_WINDOW_BOTTOM_SPACER_KEY,
  TABLE_WINDOW_TOP_SPACER_KEY,
  TableWindowDirective,
  TableWindowSpacer,
} from './table_window';

interface TestRow {
  readonly key: string;
  readonly label: string;
}

const VIEWPORT_PX = 200;

/** Rows in `[WIDE_ROW_START, WIDE_ROW_END)` carry a much longer label than the rest. */
const WIDE_ROW_START = 500;
const WIDE_ROW_END = 520;

function makeRows(count: number): TestRow[] {
  return Array.from({length: count}, (_, i) => ({
    key: `row-${i}`,
    label:
      i >= WIDE_ROW_START && i < WIDE_ROW_END
        ? `Row ${i} with a considerably longer label than the other rows`
        : `Row ${i}`,
  }));
}

@Component({
  standalone: true,
  imports: [TableWindowDirective],
  template: `
    <div
      class="scroller"
      [style.height.px]="viewportPx()"
      style="overflow: auto;"
      [style.width.px]="scrollerWidthPx()"
      [appTableWindow]="rows()"
      #win="appTableWindow"
      [itemHeight]="estimatePx"
      [overscan]="overscan"
      [threshold]="threshold"
      [scrollResetKey]="resetKey()"
    >
      <table style="border-collapse: collapse; width: max-content; min-width: 100%; table-layout: auto;">
        <thead>
          <tr>
            <th class="col-label" style="padding: 0; white-space: nowrap; text-align: left;">Label</th>
            <th class="col-filler" style="padding: 0; width: 100%;">Filler</th>
          </tr>
        </thead>
        <tbody>
          @for (item of win.windowedItems(); track item.key) {
            @if (isSpacer(item)) {
              <tr class="vs-spacer-row" [attr.data-key]="item.key">
                <td colspan="2" style="padding: 0; border: 0;" [style.height.px]="item.heightPx"></td>
              </tr>
            } @else {
              <tr class="data-row" [attr.data-key]="item.key">
                <td style="padding: 0; border: 0; white-space: nowrap;" [style.height.px]="rowPx">{{ item.label }}</td>
                <td style="padding: 0; border: 0;"></td>
              </tr>
            }
          }
        </tbody>
      </table>
    </div>
  `,
})
class TestHostComponent {
  readonly rows = signal<TestRow[]>(makeRows(1000));
  readonly resetKey = signal<object>({});
  readonly isSpacer = isTableWindowSpacer;
  readonly viewportPx = signal(VIEWPORT_PX);
  readonly scrollerWidthPx = signal(600);
  /** Height the rows really render at. */
  rowPx = 20;
  /** Estimate handed to the directive (may deliberately differ from `rowPx`). */
  estimatePx = 20;
  overscan = 4;
  threshold = 100;
}

describe('TableWindowDirective', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
  });

  function directive(): TableWindowDirective<TestRow> {
    return fixture.debugElement.children[0].injector.get<
      TableWindowDirective<TestRow>
    >(TableWindowDirective);
  }

  function scroller(): HTMLElement {
    return fixture.nativeElement.querySelector('.scroller') as HTMLElement;
  }

  function headerHeight(): number {
    return scroller().querySelector('thead')!.getBoundingClientRect().height;
  }

  function dataRows(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tr.data-row'));
  }

  function spacer(key: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(
      `tr.vs-spacer-row[data-key="${key}"]`,
    );
  }

  /** Renders, lets the ResizeObserver report the laid-out viewport, and renders again. */
  async function render(): Promise<void> {
    fixture.detectChanges();
    await new Promise((resolve) => {
      setTimeout(resolve, 20);
    });
    fixture.detectChanges();
  }

  async function scrollTo(top: number): Promise<void> {
    const el = scroller();
    el.scrollTop = top;
    el.dispatchEvent(new Event('scroll'));
    await render();
  }

  /** Re-renders until `condition` holds (bounded), for changes the ResizeObserver delivers asynchronously. */
  async function waitUntil(condition: () => boolean): Promise<void> {
    for (let i = 0; i < 25 && !condition(); i++) {
      await render();
    }
  }

  it('passes rows through untouched at or below the threshold', async () => {
    host.rows.set(makeRows(100));
    await render();

    expect(directive().isActive()).toBeFalse();
    expect(directive().windowedItems()).toBe(host.rows());
    expect(dataRows().length).toBe(100);
    expect(spacer(TABLE_WINDOW_TOP_SPACER_KEY)).toBeNull();
    expect(spacer(TABLE_WINDOW_BOTTOM_SPACER_KEY)).toBeNull();
  });

  it('renders only a window of rows plus a bottom spacer sized for the hidden rows', async () => {
    await render();

    const dir = directive();
    expect(dir.isActive()).toBeTrue();
    const {start, end} = dir.range();
    expect(start).toBe(0);
    // 200px viewport / 20px rows = 10 visible rows, plus overscan.
    expect(end).toBeGreaterThanOrEqual(10);
    expect(end).toBeLessThan(100);
    expect(dataRows().length).toBe(end - start);
    expect(dataRows()[0].getAttribute('data-key')).toBe('row-0');

    expect(spacer(TABLE_WINDOW_TOP_SPACER_KEY)).toBeNull();
    const bottom = spacer(TABLE_WINDOW_BOTTOM_SPACER_KEY);
    expect(bottom).not.toBeNull();
    expect(bottom!.getBoundingClientRect().height).toBe((1000 - end) * 20);
    // The scroll height still covers the whole page (plus the header row).
    expect(scroller().scrollHeight).toBe(1000 * 20 + headerHeight());
  });

  it('shifts the window and both spacers when scrolled', async () => {
    await render();
    await scrollTo(500 * 20);

    const dir = directive();
    const {start, end} = dir.range();
    expect(start).toBe(500 - host.overscan);
    expect(end).toBeGreaterThan(500);
    expect(dataRows()[0].getAttribute('data-key')).toBe(`row-${start}`);

    const top = spacer(TABLE_WINDOW_TOP_SPACER_KEY)!;
    const bottom = spacer(TABLE_WINDOW_BOTTOM_SPACER_KEY)!;
    expect(top.getBoundingClientRect().height).toBe(start * 20);
    expect(bottom.getBoundingClientRect().height).toBe((1000 - end) * 20);
    expect(scroller().scrollTop).toBe(500 * 20);
  });

  it('keeps the rendered range while scrolling within the overscan buffer', async () => {
    await render();
    await scrollTo(500 * 20);
    const before = directive().range();

    // One row of travel stays well inside the 4-row overscan on both sides.
    await scrollTo(501 * 20);
    expect(directive().range()).toBe(before);

    // Leaving the buffer recomputes the window around the new position.
    await scrollTo(600 * 20);
    expect(directive().range()).not.toBe(before);
    expect(directive().range().start).toBe(600 - host.overscan);
  });

  it('sizes the window to the measured viewport height', async () => {
    await render();
    // A window shift computes the range from the live viewport:
    // 200px / 20px rows = 10 visible rows, plus overscan on both sides.
    await scrollTo(500 * 20);
    expect(directive().range()).toEqual({
      start: 500 - host.overscan,
      end: 510 + host.overscan,
    });

    // A taller viewport (reported by the ResizeObserver) shows more rows.
    host.viewportPx.set(2 * VIEWPORT_PX);
    await render();
    await scrollTo(700 * 20);
    expect(directive().range()).toEqual({
      start: 700 - host.overscan,
      end: 720 + host.overscan,
    });
  });

  it('corrects the row height estimate from the rendered rows', async () => {
    host.estimatePx = 20;
    host.rowPx = 30;
    await render();

    const dir = directive();
    expect(dir.effectiveItemHeight()).toBe(30);
    const {end} = dir.range();
    expect(
      spacer(TABLE_WINDOW_BOTTOM_SPACER_KEY)!.getBoundingClientRect().height,
    ).toBe((1000 - end) * 30);
    expect(scroller().scrollHeight).toBe(1000 * 30 + headerHeight());
  });

  it('scrolls back to the top when the reset key changes', async () => {
    await render();
    await scrollTo(500 * 20);
    expect(scroller().scrollTop).toBe(500 * 20);

    host.resetKey.set({});
    await render();

    expect(scroller().scrollTop).toBe(0);
    expect(directive().range().start).toBe(0);
    expect(dataRows()[0].getAttribute('data-key')).toBe('row-0');
  });

  it('clamps the window when the page shrinks below the scrolled position', async () => {
    await render();
    await scrollTo(900 * 20);

    host.rows.set(makeRows(300));
    await render();

    const {start, end} = directive().range();
    expect(end).toBe(300);
    expect(start).toBeLessThan(end);
    expect(dataRows().length).toBe(end - start);
    // The window reaches the last row, so nothing is left to stand in for below it.
    expect(spacer(TABLE_WINDOW_BOTTOM_SPACER_KEY)).toBeNull();
  });

  it('keeps a column from shrinking again once a wider window has been rendered', async () => {
    await render();
    const th = scroller().querySelector('th.col-label') as HTMLElement;
    const narrow = th.getBoundingClientRect().width;

    // Scrolling into the wide rows lets the auto-layout column grow…
    await scrollTo(WIDE_ROW_START * 20);
    const wide = th.getBoundingClientRect().width;
    expect(wide).toBeGreaterThan(narrow);

    // …and scrolling back to narrow rows keeps it there instead of jittering.
    await scrollTo(0);
    expect(th.getBoundingClientRect().width).toBe(wide);
    expect(Number(th.style.minWidth.replace(/px$/, ''))).toBeCloseTo(wide, 1);

    // Turning windowing off (small page) releases the ratchet.
    host.rows.set(makeRows(50));
    await render();
    expect(th.style.minWidth).toBe('');
  });

  it('keeps ratcheted widths across a height-only resize but re-measures them on a width change', async () => {
    await render();
    const th = scroller().querySelector('th.col-label') as HTMLElement;
    await scrollTo(WIDE_ROW_START * 20);
    await scrollTo(0);
    const pinned = th.style.minWidth;
    const pinnedWidth = th.getBoundingClientRect().width;
    expect(pinned).not.toBe('');

    // A taller viewport changes nothing horizontally: the widths stay pinned.
    const rangeBefore = directive().range();
    host.viewportPx.set(2 * VIEWPORT_PX);
    await waitUntil(() => directive().range() !== rangeBefore);
    expect(th.style.minWidth).toBe(pinned);

    // A horizontal resize (e.g. a side nav toggle) lets the columns lay out
    // freely again; the ratchet then starts over from the new widths.
    host.scrollerWidthPx.set(900);
    await waitUntil(() => th.style.minWidth !== pinned);
    expect(th.getBoundingClientRect().width).toBeLessThan(pinnedWidth);
  });

  it('exposes spacer items that the type guard recognizes', () => {
    const spacerItem: TableWindowSpacer = {
      vsSpacer: true,
      key: TABLE_WINDOW_TOP_SPACER_KEY,
      heightPx: 10,
    };
    expect(isTableWindowSpacer(spacerItem)).toBeTrue();
    expect(isTableWindowSpacer({key: 'row-1', label: 'Row 1'})).toBeFalse();
    expect(isTableWindowSpacer(null)).toBeFalse();
  });
});
