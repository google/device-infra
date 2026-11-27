import {ChangeDetectionStrategy, Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {MasterDetailLayout, NavItem} from './master_detail_layout';

@Component({
  standalone: true,
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

interface CapturedObserver {
  callback: IntersectionObserverCallback;
  options: IntersectionObserverInit | undefined;
  targets: Element[];
  disconnected: boolean;
}

/** Reports one section entering or leaving the scrollspy reading band. */
function emitIntersection(
  observer: CapturedObserver,
  target: Element,
  isIntersecting: boolean,
): void {
  observer.callback(
    [{target, isIntersecting} as unknown as IntersectionObserverEntry],
    {} as IntersectionObserver,
  );
}

describe('MasterDetailLayout Component', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let compiled: HTMLElement;
  let capturedObservers: CapturedObserver[];
  let originalIntersectionObserver: typeof IntersectionObserver;

  function getLinks(): HTMLAnchorElement[] {
    return Array.from(
      compiled.querySelectorAll<HTMLAnchorElement>(
        '.master-detail-layout-nav-link',
      ),
    );
  }

  function getSection(id: string): HTMLElement {
    return compiled.querySelector<HTMLElement>(`#${id}`)!;
  }

  function getScrollspy(): CapturedObserver {
    const capabilities = getSection('section-capabilities');
    const observer = capturedObservers.find(
      (o) => !o.disconnected && o.targets.includes(capabilities),
    );
    expect(observer).toBeDefined();
    return observer!;
  }

  beforeEach(async () => {
    capturedObservers = [];
    originalIntersectionObserver = window.IntersectionObserver;
    class FakeIntersectionObserver implements IntersectionObserver {
      readonly root: Element | Document | null;
      readonly rootMargin: string;
      readonly scrollMargin = '0px';
      readonly thresholds: readonly number[];
      private readonly record: CapturedObserver;

      constructor(
        callback: IntersectionObserverCallback,
        options?: IntersectionObserverInit,
      ) {
        this.root = options?.root ?? null;
        this.rootMargin = options?.rootMargin ?? '0px';
        this.thresholds = Array.isArray(options?.threshold)
          ? options.threshold
          : [options?.threshold ?? 0];
        this.record = {callback, options, targets: [], disconnected: false};
        capturedObservers.push(this.record);
      }

      observe(target: Element): void {
        this.record.targets.push(target);
      }

      unobserve(): void {}
      disconnect(): void {
        this.record.disconnected = true;
      }
      takeRecords(): IntersectionObserverEntry[] {
        return [];
      }
    }
    window.IntersectionObserver = FakeIntersectionObserver;

    await TestBed.configureTestingModule({
      imports: [
        TestHostComponent,
        MasterDetailLayout,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    compiled = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    window.IntersectionObserver = originalIntersectionObserver;
  });

  it('renders section pills as links with aria-current on the active pill', () => {
    const links = getLinks();

    expect(links.length).toBe(6);
    expect(links[0].getAttribute('href')).toBe('#section-health');
    expect(links[0].classList.contains('active')).toBeTrue();
    expect(links[0].getAttribute('aria-current')).toBe('location');
    expect(links[1].classList.contains('active')).toBeFalse();
    expect(links[1].getAttribute('aria-current')).toBeNull();
  });

  it('scrolls to the target section and updates active pill on click', () => {
    const scrollSpy = spyOn(getSection('section-dimensions'), 'scrollIntoView');
    const links = getLinks();

    links[4].click();
    fixture.detectChanges();

    expect(scrollSpy).toHaveBeenCalledWith({
      behavior: 'instant',
      block: 'start',
    });
    expect(links[0].classList.contains('active')).toBeFalse();
    expect(links[0].getAttribute('aria-current')).toBeNull();
    expect(links[4].classList.contains('active')).toBeTrue();
    expect(links[4].getAttribute('aria-current')).toBe('location');
  });

  it('updates active pill when scrollspy observes a new intersecting section', () => {
    const scroller = compiled.querySelector<HTMLElement>('.test-scroller')!;
    const scrollspy = getScrollspy();
    expect(scrollspy.options?.root).toBe(scroller);

    emitIntersection(scrollspy, getSection('section-capabilities'), true);
    fixture.detectChanges();

    const links = getLinks();
    expect(links[3].classList.contains('active')).toBeTrue();
    expect(links[3].getAttribute('aria-current')).toBe('location');
  });

  describe('after a pill click', () => {
    let frameCallbacks: FrameRequestCallback[];
    let requestFrame: jasmine.Spy<typeof window.requestAnimationFrame>;

    /** Runs the callbacks requested for the next animation frame. */
    function runFrame(): void {
      const callbacks = frameCallbacks;
      frameCallbacks = [];
      for (const callback of callbacks) {
        callback(performance.now());
      }
    }

    beforeEach(() => {
      frameCallbacks = [];
      // The karma unit_tests page renders no frames, so tests run them.
      requestFrame = spyOn(window, 'requestAnimationFrame').and.callFake(
        (callback) => {
          frameCallbacks.push(callback);
          return frameCallbacks.length;
        },
      );
      spyOn(getSection('section-dimensions'), 'scrollIntoView');
    });

    it('keeps the clicked pill active through the scrollspy update of its jump', () => {
      const scrollspy = getScrollspy();
      const links = getLinks();

      links[4].click();
      // Dimensions cannot reach the reading band at the bottom of the page,
      // so the jump puts Capabilities there instead.
      emitIntersection(scrollspy, getSection('section-capabilities'), true);
      fixture.detectChanges();
      runFrame();
      runFrame();
      fixture.detectChanges();

      expect(links[4].classList.contains('active')).toBeTrue();
      expect(links[3].classList.contains('active')).toBeFalse();
    });

    it('follows the scrollspy again from the second frame after the jump', () => {
      const scrollspy = getScrollspy();
      const links = getLinks();

      links[4].click();
      emitIntersection(scrollspy, getSection('section-dimensions'), true);
      runFrame();
      emitIntersection(scrollspy, getSection('section-capabilities'), true);
      fixture.detectChanges();
      expect(links[4].classList.contains('active')).toBeTrue();

      runFrame();
      // Scrolling up moves Dimensions out of the reading band.
      emitIntersection(scrollspy, getSection('section-dimensions'), false);
      fixture.detectChanges();

      expect(links[3].classList.contains('active')).toBeTrue();
      expect(links[4].classList.contains('active')).toBeFalse();
    });

    it('waits for the last of several quick clicks', () => {
      spyOn(getSection('section-capabilities'), 'scrollIntoView');
      const scrollspy = getScrollspy();
      const links = getLinks();

      links[3].click();
      runFrame();
      links[4].click();
      runFrame();
      // The first jump is done, but the second one is still pending.
      emitIntersection(scrollspy, getSection('section-capabilities'), true);
      fixture.detectChanges();
      expect(links[4].classList.contains('active')).toBeTrue();

      runFrame();
      emitIntersection(scrollspy, getSection('section-properties'), true);
      fixture.detectChanges();

      expect(links[3].classList.contains('active')).toBeTrue();
    });

    it('cancels its pending frame when the component is destroyed', () => {
      const cancelFrame = spyOn(window, 'cancelAnimationFrame');

      getLinks()[4].click();
      const frameId = requestFrame.calls.mostRecent().returnValue;
      fixture.destroy();

      expect(cancelFrame).toHaveBeenCalledWith(frameId);
    });
  });

  it('scrolls the active pill into view inside the pill strip', () => {
    host.containerWidth.set(400);
    fixture.detectChanges();
    const nav = compiled.querySelector<HTMLElement>(
      '.master-detail-layout-nav',
    )!;
    expect(nav.scrollWidth).toBeGreaterThan(nav.clientWidth);
    const scrollTo = spyOn(nav, 'scrollTo') as jasmine.Spy<
      (options?: ScrollToOptions) => void
    >;

    emitIntersection(getScrollspy(), getSection('section-properties'), true);
    fixture.detectChanges();

    expect(getLinks()[5].classList.contains('active')).toBeTrue();
    expect(scrollTo).toHaveBeenCalledOnceWith({
      left: jasmine.any(Number),
      behavior: 'smooth',
    });
    expect(scrollTo.calls.mostRecent().args[0]?.left ?? 0).toBeGreaterThan(0);

    // The strip does not move again while the active pill stays the same.
    emitIntersection(getScrollspy(), getSection('section-properties'), true);
    fixture.detectChanges();
    expect(scrollTo).toHaveBeenCalledTimes(1);
  });

  it('falls back to the first pill when the reading band is above the first section', () => {
    const scrollspy = getScrollspy();
    const capabilities = getSection('section-capabilities');
    const links = getLinks();
    emitIntersection(scrollspy, capabilities, true);
    fixture.detectChanges();
    expect(links[3].classList.contains('active')).toBeTrue();

    // A fast scroll back to the top leaves no section in the reading band.
    emitIntersection(scrollspy, capabilities, false);
    fixture.detectChanges();

    expect(links[0].classList.contains('active')).toBeTrue();
  });

  it('keeps the current pill when the reading band falls between two sections', () => {
    const scroller = compiled.querySelector<HTMLElement>('.test-scroller')!;
    const scrollspy = getScrollspy();
    const capabilities = getSection('section-capabilities');
    const links = getLinks();
    scroller.scrollTop = 600;

    emitIntersection(scrollspy, capabilities, true);
    emitIntersection(scrollspy, capabilities, false);
    fixture.detectChanges();

    expect(links[3].classList.contains('active')).toBeTrue();
  });

  it('stacks the sticky pill bar above table layers and below shell/overlays', () => {
    const navContainer = compiled.querySelector<HTMLElement>(
      '.master-detail-layout-nav-container',
    )!;
    const zIndex = Number(getComputedStyle(navContainer).zIndex);

    expect(zIndex).toBeGreaterThan(200);
    expect(zIndex).toBeLessThan(250);
  });

  it('reconciles active section and reconnects scrollspy when navList changes', () => {
    spyOn(getSection('section-dimensions'), 'scrollIntoView');
    const initialScrollspy = getScrollspy();
    getLinks()[4].click();
    fixture.detectChanges();
    expect(getLinks()[4].classList.contains('active')).toBeTrue();

    // Keep the active section when it remains in the updated navList.
    host.navList.set([
      {id: 'section-health', label: 'Health & Activity'},
      {id: 'section-dimensions', label: 'Dimensions'},
      {id: 'section-extra', label: 'Extra'},
    ]);
    fixture.detectChanges();

    expect(initialScrollspy.disconnected).toBeTrue();
    expect(getLinks()[1].classList.contains('active')).toBeTrue();
    const updatedScrollspy = capturedObservers.find(
      (o) => !o.disconnected && o.targets.includes(getSection('section-extra')),
    );
    expect(updatedScrollspy).toBeDefined();

    // Fall back to the first section when the active section is removed.
    host.navList.set([
      {id: 'section-health', label: 'Health & Activity'},
      {id: 'section-extra', label: 'Extra'},
    ]);
    fixture.detectChanges();

    expect(getLinks()[0].classList.contains('active')).toBeTrue();

    // Clearing navList removes the pill strip and disconnects observers.
    host.navList.set([]);
    fixture.detectChanges();

    expect(
      compiled.querySelector('.master-detail-layout-nav-container'),
    ).toBeNull();
    expect(capturedObservers.every((o) => o.disconnected)).toBeTrue();
  });

  it('disconnects observers when the component is destroyed', () => {
    expect(capturedObservers.some((o) => !o.disconnected)).toBeTrue();

    fixture.destroy();

    expect(capturedObservers.every((o) => o.disconnected)).toBeTrue();
  });
});
