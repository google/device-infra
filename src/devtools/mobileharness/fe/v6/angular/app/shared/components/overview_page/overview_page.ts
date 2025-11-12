import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  OnDestroy,
  OnInit,
  signal,
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
      rootMargin: '-20% 0px -75% 0px',
      threshold: 0.1,
    };

    this.observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          this.activeSection.set(entry.target.id);
        }
      });
    }, options);

    sections.forEach((section) => {
      this.observer?.observe(section);
    });
  }
}
