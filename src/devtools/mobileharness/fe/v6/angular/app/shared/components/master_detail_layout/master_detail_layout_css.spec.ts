import {ChangeDetectionStrategy, Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';

import {MasterDetailLayout, NavItem} from './master_detail_layout';

@Component({
  imports: [MasterDetailLayout],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="test-scroller"
      [style.width.px]="containerWidth()"
      style="height: 260px; overflow-y: auto;"
    >
      <div style="height: 120px;">Hero header</div>
      <app-master-detail-layout [navList]="navList()">
        @for (item of navList(); track item.id) {
          <section [id]="item.id" style="height: 360px;">
            {{ item.label }}
          </section>
        }
      </app-master-detail-layout>
    </div>
  `,
})
class TestHostComponent {
  readonly containerWidth = signal(640);
  readonly navList = signal<NavItem[]>([
    {id: 'section-health', label: 'Health & Activity'},
    {id: 'section-basic', label: 'Basic Information'},
    {id: 'section-permissions', label: 'Permissions'},
    {id: 'section-capabilities', label: 'Capabilities'},
    {id: 'section-dimensions', label: 'Dimensions'},
    {id: 'section-properties', label: 'Properties'},
  ]);
}

/**
 * Waits for rendered frames. The stuck state of a scroll-state container and
 * scroll timelines only update when the browser renders a frame.
 */
async function waitForFrames(count = 2): Promise<void> {
  for (let i = 0; i < count; i++) {
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => {
        resolve();
      });
    });
  }
}

describe('MasterDetailLayout CSS', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let compiled: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    compiled = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('tints the pill strip surface only while it is stuck, at a constant 48px height', async () => {
    const scroller = compiled.querySelector<HTMLElement>('.test-scroller')!;
    const container = compiled.querySelector<HTMLElement>(
      '.master-detail-layout-nav-container',
    )!;
    const surface = compiled.querySelector<HTMLElement>(
      '.master-detail-layout-nav-surface',
    )!;
    surface.style.transition = 'none';
    await waitForFrames();
    expect(getComputedStyle(surface).backgroundColor).toBe('rgba(0, 0, 0, 0)');
    expect(container.getBoundingClientRect().height).toBe(48);

    scroller.scrollTop = 400;
    await waitForFrames();

    expect(container.getBoundingClientRect().top).toBeCloseTo(
      scroller.getBoundingClientRect().top,
      0,
    );
    expect(getComputedStyle(surface).backgroundColor).toBe(
      'rgb(248, 250, 253)',
    );
    expect(container.getBoundingClientRect().height).toBe(48);

    scroller.scrollTop = 0;
    await waitForFrames();
    expect(getComputedStyle(surface).backgroundColor).toBe('rgba(0, 0, 0, 0)');
  });

  it('fades the right edge only while pills overflow past it', async () => {
    const nav = compiled.querySelector<HTMLElement>(
      '.master-detail-layout-nav',
    )!;
    const fade = () =>
      Number(
        getComputedStyle(nav).getPropertyValue('--master-detail-pill-fade'),
      );

    host.containerWidth.set(1200);
    fixture.detectChanges();
    await waitForFrames();
    expect(nav.scrollWidth).toBeLessThanOrEqual(nav.clientWidth);
    expect(fade()).toBe(0);

    host.containerWidth.set(260);
    fixture.detectChanges();
    await waitForFrames();
    expect(nav.scrollWidth).toBeGreaterThan(nav.clientWidth);
    expect(fade()).toBe(1);

    nav.scrollLeft = nav.scrollWidth;
    await waitForFrames();
    expect(fade()).toBe(0);
  });
});
