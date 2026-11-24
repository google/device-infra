import {DOCUMENT} from '@angular/common';
import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  linkedSignal,
  viewChild,
  viewChildren,
} from '@angular/core';

/** The interface for navigation item. */
export interface NavItem {
  id: string;
  label: string;
}

const PILL_SCROLL_LEFT_PADDING_PX = 12;
const PILL_SCROLL_RIGHT_PADDING_PX = 32;

/**
 * The scrollspy reading band starts 56px below the top of the scrollport,
 * under the 48px sticky pill strip, and ends at 25% of the scrollport height.
 */
const SCROLLSPY_ROOT_MARGIN = '-56px 0px -75% 0px';

/**
 * Shared layout component with a sticky horizontal section pill strip and
 * scrollspy-tracked content cards. The stuck surface tint and the right-edge
 * overflow fade of the strip are pure CSS (see master_detail_layout.scss).
 */
@Component({
  selector: 'app-master-detail-layout',
  templateUrl: './master_detail_layout.ng.html',
  styleUrl: './master_detail_layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MasterDetailLayout {
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);

  readonly navList = input<readonly NavItem[]>([]);

  readonly activeSection = linkedSignal<readonly NavItem[], string>({
    source: this.navList,
    computation: (navItems, previous) => {
      if (navItems.length === 0) {
        return '';
      }
      const previousId = previous?.value ?? '';
      return navItems.some((item) => item.id === previousId)
        ? previousId
        : navItems[0].id;
    },
  });

  private readonly navListEl = viewChild<ElementRef<HTMLElement>>('navListEl');
  private readonly navLinks =
    viewChildren<ElementRef<HTMLAnchorElement>>('navLink');

  /**
   * How many more animation frames the scrollspy ignores the observer for
   * after a pill click. A jump is instant, and the observer reports it within
   * the next two frames, so ignoring it keeps the clicked pill active even
   * when its section cannot reach the reading band at the bottom of the page,
   * until the next scroll moves the pills again.
   */
  private scrollspyPausedFrames = 0;
  private pauseFrameId?: number;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.pauseFrameId !== undefined) {
        cancelAnimationFrame(this.pauseFrameId);
      }
    });

    afterRenderEffect({
      read: (onCleanup) => {
        const navItems = this.navList();
        if (navItems.length === 0) {
          return;
        }

        const host = this.elementRef.nativeElement;
        const sections: Element[] = [];
        for (const item of navItems) {
          const element = host.querySelector(`#${CSS.escape(item.id)}`);
          if (element) {
            sections.push(element);
          }
        }
        if (sections.length === 0) {
          return;
        }

        const scrollContainer = this.findScrollContainer();
        const intersectingSections = new Set<string>();
        const syncActiveSection = () => {
          const current = navItems.find((item) =>
            intersectingSections.has(item.id),
          );
          if (current) {
            this.activeSection.set(current.id);
            return;
          }
          // Nothing is in the reading band: it either falls between two
          // sections (keep the current pill) or above the first one, for
          // example after a fast scroll back to the top of a tall header.
          const scrollportTop =
            scrollContainer?.getBoundingClientRect().top ?? 0;
          if (sections[0].getBoundingClientRect().top >= scrollportTop) {
            this.activeSection.set(sections[0].id);
          }
        };

        const scrollspyObserver = new IntersectionObserver(
          (entries) => {
            for (const entry of entries) {
              if (entry.isIntersecting) {
                intersectingSections.add(entry.target.id);
              } else {
                intersectingSections.delete(entry.target.id);
              }
            }
            if (this.scrollspyPausedFrames === 0) {
              syncActiveSection();
            }
          },
          {
            // Use the nearest scrolling ancestor when present, or the document
            // instead of null so rootMargin is honored in cross-origin iframes.
            root: scrollContainer ?? this.document,
            rootMargin: SCROLLSPY_ROOT_MARGIN,
            threshold: 0,
          },
        );
        for (const section of sections) {
          scrollspyObserver.observe(section);
        }
        onCleanup(() => {
          scrollspyObserver.disconnect();
        });
      },
    });

    // Keeps the active pill in view inside the horizontally scrolling strip
    // without moving the vertical scroller.
    afterRenderEffect({
      earlyRead: () => {
        const navEl = this.navListEl()?.nativeElement;
        const activeId = this.activeSection();
        const index = this.navList().findIndex((item) => item.id === activeId);
        const activeLink =
          index < 0 ? undefined : this.navLinks().at(index)?.nativeElement;
        if (!navEl || !activeLink) {
          return undefined;
        }
        const left = pillStripScrollLeft(navEl, activeLink);
        return left === undefined ? undefined : {navEl, left};
      },
      write: (target) => {
        const scroll = target();
        scroll?.navEl.scrollTo({left: scroll.left, behavior: 'smooth'});
      },
    });
  }

  scrollToSection(event: Event, sectionId: string): void {
    event.preventDefault();
    const sectionElement = this.elementRef.nativeElement.querySelector(
      `#${CSS.escape(sectionId)}`,
    );
    if (!sectionElement) {
      return;
    }
    this.scrollspyPausedFrames = 2;
    this.pauseFrameId ??= requestAnimationFrame(() => {
      this.countPausedFrame();
    });
    sectionElement.scrollIntoView({behavior: 'instant', block: 'start'});
    this.activeSection.set(sectionId);
  }

  /** Counts down one paused scrollspy frame after a pill click. */
  private countPausedFrame(): void {
    this.scrollspyPausedFrames--;
    this.pauseFrameId =
      this.scrollspyPausedFrames > 0
        ? requestAnimationFrame(() => {
            this.countPausedFrame();
          })
        : undefined;
  }

  private findScrollContainer(): HTMLElement | null {
    let current = this.elementRef.nativeElement.parentElement;
    while (
      current &&
      current !== this.document.body &&
      current !== this.document.documentElement
    ) {
      const overflowY = getComputedStyle(current).overflowY;
      if (overflowY === 'auto' || overflowY === 'scroll') {
        return current;
      }
      current = current.parentElement;
    }
    return null;
  }
}

/**
 * Returns the strip scroll position that shows the active pill with some
 * padding on both sides, or undefined when the pill is already in view.
 */
function pillStripScrollLeft(
  navEl: HTMLElement,
  activeLink: HTMLElement,
): number | undefined {
  const navRect = navEl.getBoundingClientRect();
  const linkRect = activeLink.getBoundingClientRect();
  let delta = 0;
  if (linkRect.left < navRect.left + PILL_SCROLL_LEFT_PADDING_PX) {
    delta = linkRect.left - (navRect.left + PILL_SCROLL_LEFT_PADDING_PX);
  } else if (linkRect.right > navRect.right - PILL_SCROLL_RIGHT_PADDING_PX) {
    delta = linkRect.right - (navRect.right - PILL_SCROLL_RIGHT_PADDING_PX);
  }
  const left = Math.max(0, navEl.scrollLeft + delta);
  return Math.abs(left - navEl.scrollLeft) < 1 ? undefined : left;
}
