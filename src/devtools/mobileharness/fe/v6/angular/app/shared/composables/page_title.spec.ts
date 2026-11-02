import {provideLocationMocks} from '@angular/common/testing';
import {Component, DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {TestBed} from '@angular/core/testing';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute, provideRouter, TitleStrategy} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';

import {
  DEFAULT_PAGE_TITLE,
  formatPageTitle,
  PageTitleStrategy,
  usePageTitle,
} from './page_title';

/** A page whose route declares the page-specific part of the title. */
@Component({
  standalone: true,
  template: '',
})
class RouteTitledPage {}

/**
 * A page that names the entity of its route parameter in the title, as the
 * device detail page does.
 */
@Component({
  standalone: true,
  template: '',
})
class SelfTitledPage {
  constructor() {
    const title = inject(Title);
    const destroyRef = inject(DestroyRef);
    inject(ActivatedRoute)
      .paramMap.pipe(takeUntilDestroyed(destroyRef))
      .subscribe((params) => {
        title.setTitle(formatPageTitle(`Device ${params.get('id')}`));
      });
  }
}

/** A page that keeps the page-specific part of its title in a signal. */
@Component({
  standalone: true,
  template: '',
})
class SignalTitledPage {
  readonly pageTitle = signal<string | null>(null);

  constructor() {
    usePageTitle(this.pageTitle);
  }
}

describe('formatPageTitle', () => {
  it('puts the page-specific part before the product name', () => {
    expect(formatPageTitle('Devices')).toBe('Devices - OmniLab Console');
  });

  it('returns the product name alone without a page-specific part', () => {
    expect(formatPageTitle()).toBe(DEFAULT_PAGE_TITLE);
    expect(formatPageTitle(null)).toBe(DEFAULT_PAGE_TITLE);
    expect(formatPageTitle('')).toBe(DEFAULT_PAGE_TITLE);
  });
});

describe('PageTitleStrategy', () => {
  let harness: RouterTestingHarness;
  let title: Title;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {path: 'home', component: RouteTitledPage, title: 'Home'},
          {path: 'devices', component: RouteTitledPage, title: 'Devices'},
          {path: 'devices/:id', component: SelfTitledPage},
          {path: 'overview', component: RouteTitledPage, title: ''},
        ]),
        provideLocationMocks(),
        {provide: TitleStrategy, useClass: PageTitleStrategy},
      ],
    });
    title = TestBed.inject(Title);
    harness = await RouterTestingHarness.create();
  });

  it('formats the title declared by the route', async () => {
    await harness.navigateByUrl('/home');

    expect(title.getTitle()).toBe('Home - OmniLab Console');
  });

  it('replaces the title of the previous page', async () => {
    await harness.navigateByUrl('/home');
    await harness.navigateByUrl('/devices');

    expect(title.getTitle()).toBe('Devices - OmniLab Console');
  });

  it('shows the product name alone for a route that declares an empty title', async () => {
    await harness.navigateByUrl('/home');
    await harness.navigateByUrl('/overview');

    expect(title.getTitle()).toBe(DEFAULT_PAGE_TITLE);
  });

  it('keeps the title set by a page whose route declares none', async () => {
    await harness.navigateByUrl('/home');
    await harness.navigateByUrl('/devices/device-1');

    expect(title.getTitle()).toBe('Device device-1 - OmniLab Console');
  });

  it('keeps the title set by a reused page whose route declares none', async () => {
    const page = await harness.navigateByUrl(
      '/devices/device-1',
      SelfTitledPage,
    );
    // The router reuses the page, which sets its title from the new route
    // parameter before the router applies the title strategy.
    const reusedPage = await harness.navigateByUrl(
      '/devices/device-2',
      SelfTitledPage,
    );

    expect(reusedPage).toBe(page);
    expect(title.getTitle()).toBe('Device device-2 - OmniLab Console');
  });
});

describe('usePageTitle', () => {
  let title: Title;

  beforeEach(() => {
    title = TestBed.inject(Title);
    title.setTitle('Previous page - OmniLab Console');
  });

  it('formats the page-specific part once the signal holds it', async () => {
    const fixture = TestBed.createComponent(SignalTitledPage);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(title.getTitle()).toBe(DEFAULT_PAGE_TITLE);

    // The page-specific part usually arrives with data that loads after the
    // page renders.
    fixture.componentInstance.pageTitle.set('Job 12345678...');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(title.getTitle()).toBe('Job 12345678... - OmniLab Console');
  });

  it('restores the product name alone when the page is destroyed', async () => {
    const fixture = TestBed.createComponent(SignalTitledPage);
    fixture.componentInstance.pageTitle.set('Job 12345678...');
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.destroy();

    expect(title.getTitle()).toBe(DEFAULT_PAGE_TITLE);
  });
});
