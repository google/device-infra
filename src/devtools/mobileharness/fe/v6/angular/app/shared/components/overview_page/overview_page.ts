import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  input,
  OnDestroy,
  OnInit,
  signal,
  untracked,
} from '@angular/core';

/** The interface for navigation item. */
export interface NavItem {
  id: string;
  label: string;
}

/**
 * A common component for overview page with left navigation and right content.
 */
@Component({
  selector: 'app-overview-page',
  standalone: true,
  templateUrl: './overview_page.ng.html',
  styleUrl: './overview_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewPage implements OnInit, AfterViewInit, OnDestroy {
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly navList = input<NavItem[]>([]);

  activeSection = signal<string>('');
  private observer?: IntersectionObserver;
  private readonly intersectingSections = new Set<string>();

  constructor() {
    effect(() => {
      this.navList();
      untracked(() => {
        setTimeout(() => {
          this.setupIntersectionObserver();
        });
      });
    });
  }

  ngOnInit() {
    if (this.navList().length > 0) {
      this.activeSection.set(this.navList()[0].id);
    }
  }

  ngAfterViewInit(): void {
    this.setupIntersectionObserver();
  }

  ngOnDestroy(): void {
    if (this.observer) {
      this.observer.disconnect();
    }
  }

  scrollToSection(event: Event, sectionId: string): void {
    event.preventDefault();
    const sectionElement =
      this.elementRef.nativeElement.querySelector(sectionId);
    if (sectionElement) {
      sectionElement.scrollIntoView({behavior: 'smooth', block: 'start'});
      this.activeSection.set(sectionId.substring(1));
    }
  }

  private setupIntersectionObserver(): void {
    if (this.observer) {
      this.observer.disconnect();
    }
    this.intersectingSections.clear();
    const sections: Element[] = [];
    this.navList().forEach((item) => {
      const element = this.elementRef.nativeElement.querySelector(
        `#${item.id}`,
      );
      if (element) {
        sections.push(element);
      }
    });

    if (sections.length === 0) return;

    const options = {
      root: null, // viewport
      rootMargin: '-10% 0px -85% 0px',
      threshold: 0,
    };

    this.observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          this.intersectingSections.add(entry.target.id);
        } else {
          this.intersectingSections.delete(entry.target.id);
        }
      });

      for (const navItem of this.navList()) {
        if (this.intersectingSections.has(navItem.id)) {
          this.activeSection.set(navItem.id);
          return;
        }
      }
    }, options);

    sections.forEach((section) => {
      this.observer?.observe(section);
    });
  }
}
