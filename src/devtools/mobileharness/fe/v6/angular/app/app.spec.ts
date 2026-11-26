import {ESCAPE} from '@angular/cdk/keycodes';
import {BreakpointObserver, type BreakpointState} from '@angular/cdk/layout';
import {TestbedHarnessEnvironment} from '@angular/cdk/testing/testbed';
import {signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatSidenav} from '@angular/material/sidenav';
import {MatSidenavHarness} from '@angular/material/sidenav/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import {
  BehaviorSubject,
  firstValueFrom,
  type Observable,
  of,
  Subject,
} from 'rxjs';
import {filter, map} from 'rxjs/operators';

import {
  App,
  EMBEDDED_HEADER_BAND_CLASS,
  EMBEDDED_HEADER_BAND_HEIGHT_PROPERTY,
} from './app';
import {APP_DATA, type AppData} from './core/models/app_data';
import {CommonParamsService} from './core/services/common_params_service';
import {type EmbeddedHeaderSlot, UrlService} from './core/services/url_service';
import {EmbeddedBackdropService} from './shared/services/embedded_backdrop_service';

/**
 * Stands in for BreakpointObserver: resolves `(min-width: Npx)` queries against
 * a window width set by the test (other queries never match), and reports
 * width changes at once.
 */
class FakeBreakpointObserver {
  private readonly width = new BehaviorSubject(1440);

  setWidth(width: number) {
    this.width.next(width);
  }

  isMatched(value: string | readonly string[]): boolean {
    return toQueryList(value).some((query) =>
      matchesWidth(query, this.width.value),
    );
  }

  observe(value: string | readonly string[]): Observable<BreakpointState> {
    const queries = toQueryList(value);
    return this.width.pipe(
      map((width) => {
        const breakpoints = Object.fromEntries(
          queries.map((query) => [query, matchesWidth(query, width)]),
        );
        return {
          matches: Object.values(breakpoints).some((matches) => matches),
          breakpoints,
        };
      }),
    );
  }
}

function toQueryList(value: string | readonly string[]): readonly string[] {
  return typeof value === 'string' ? [value] : value;
}

/** Returns whether a window of the given width matches a `(min-width: Npx)` query. */
function matchesWidth(query: string, width: number): boolean {
  const minWidth = /^\(min-width: (\d+)px\)$/.exec(query);
  return minWidth !== null && width >= Number(minWidth[1]);
}

describe('App Component', () => {
  let component: App;
  let fixture: ComponentFixture<App>;
  let mockUrlService: jasmine.SpyObj<UrlService>;
  let mockEmbeddedBackdropService: jasmine.SpyObj<EmbeddedBackdropService>;
  let mockActivatedRoute: Partial<ActivatedRoute>;
  let commonParamsService: CommonParamsService;

  const appData: AppData = {
    adbVersion: '1.0',
    mttVersion: '1.0',
    email: 'tianch@google.com',
    userDisplayName: 'Chen Tian',
  };

  beforeEach(async () => {
    mockUrlService = jasmine.createSpyObj(
      'UrlService',
      ['isInEmbeddedMode', 'isInAtsEmbeddedMode', 'requestEmbeddedHeaderSlot'],
      {
        navigate$: new Subject<string>(),
        embeddedHeaderSlot: signal<EmbeddedHeaderSlot | null>(null),
      },
    );
    mockEmbeddedBackdropService = jasmine.createSpyObj(
      'EmbeddedBackdropService',
      ['start'],
    );

    mockActivatedRoute = {
      snapshot: {
        queryParams: {},
        queryParamMap: convertToParamMap({'is_embedded_mode': 'true'}),
      } as unknown as ActivatedRoute['snapshot'],
      queryParamMap: of(convertToParamMap({'is_embedded_mode': 'true'})),
    };

    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, App],
      providers: [
        provideRouter([]),
        {
          provide: APP_DATA,
          useValue: appData,
        },
        {
          provide: UrlService,
          useValue: mockUrlService,
        },
        {
          provide: EmbeddedBackdropService,
          useValue: mockEmbeddedBackdropService,
        },
        {
          provide: ActivatedRoute,
          useValue: mockActivatedRoute,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
    commonParamsService = TestBed.inject(CommonParamsService);
    fixture.detectChanges();
  });

  it('should create the app', () => {
    expect(component).toBeTruthy();
  });

  describe('embedded backdrop', () => {
    // The app starts the backdrop service (or not) when it is created.
    function createApp(isInAtsEmbeddedMode: boolean) {
      mockUrlService.isInAtsEmbeddedMode.and.returnValue(isInAtsEmbeddedMode);
      mockEmbeddedBackdropService.start.calls.reset();
      TestBed.createComponent(App);
    }

    it('should start mirroring the dialog backdrop to the host page in ATS embedded mode', () => {
      createApp(true);
      expect(mockEmbeddedBackdropService.start).toHaveBeenCalledTimes(1);
    });

    it('should not start mirroring the dialog backdrop outside the ATS embedded mode', () => {
      createApp(false);
      expect(mockEmbeddedBackdropService.start).not.toHaveBeenCalled();
    });
  });

  it('should not render the sidenav in embedded mode', () => {
    expect(fixture.nativeElement.querySelector('mat-sidenav')).toBeNull();
  });

  describe('Standalone mode', () => {
    let standaloneComponent: App;
    let standaloneFixture: ComponentFixture<App>;

    beforeEach(async () => {
      const standaloneMockActivatedRoute = {
        snapshot: {
          queryParams: {},
          queryParamMap: convertToParamMap({'is_embedded_mode': 'false'}),
        } as unknown as ActivatedRoute['snapshot'],
        queryParamMap: of(convertToParamMap({'is_embedded_mode': 'false'})),
      };

      await TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [NoopAnimationsModule, App],
        providers: [
          provideRouter([]),
          {provide: APP_DATA, useValue: appData},
          {provide: UrlService, useValue: mockUrlService},
          {provide: ActivatedRoute, useValue: standaloneMockActivatedRoute},
        ],
      }).compileComponents();

      standaloneFixture = TestBed.createComponent(App);
      standaloneComponent = standaloneFixture.componentInstance;
      standaloneFixture.detectChanges();
    });

    it('should be in standalone mode with the sidenav', () => {
      expect(standaloneComponent.isStandaloneMode).toBeTrue();
      const sidenav =
        standaloneFixture.nativeElement.querySelector('mat-sidenav');
      expect(sidenav).toBeTruthy();
    });

    it('should expose and render User Guide and FAQs URLs', () => {
      expect(
        standaloneComponent.userGuideUrl.startsWith('https://'),
      ).toBeTrue();
      expect(standaloneComponent.faqsUrl.startsWith('https://')).toBeTrue();

      const navLinks = Array.from<HTMLAnchorElement>(
        standaloneFixture.nativeElement.querySelectorAll(
          'mat-nav-list a[mat-list-item]',
        ),
      );
      const linkMap = new Map(
        navLinks.map((a) => [
          a.querySelector('span')?.textContent?.trim(),
          a.getAttribute('href'),
        ]),
      );

      expect(linkMap.get('User Guide')).toBe(standaloneComponent.userGuideUrl);
      expect(linkMap.get('FAQs')).toBe(standaloneComponent.faqsUrl);
    });

    it('should render header toolbar logo and title inside a link to /home', () => {
      const brandLink: HTMLAnchorElement | null =
        standaloneFixture.nativeElement.querySelector(
          '.app-toolbar a.toolbar-brand-link',
        );
      expect(brandLink).toBeTruthy();
      expect(brandLink?.getAttribute('href')).toContain('/home');
      expect(brandLink?.querySelector('img')).toBeTruthy();
      expect(brandLink?.querySelector('.title')?.textContent?.trim()).toBe(
        'OmniLab Console',
      );
    });

    it('should render current year in footer', () => {
      const footer: HTMLElement =
        standaloneFixture.nativeElement.querySelector('footer');
      expect(footer).toBeTruthy();
      expect(footer.querySelector('.version-info')).toBeNull();
      expect(
        footer.querySelector('.copy-right-info')?.textContent?.trim(),
      ).toBe(`© ${new Date().getFullYear()} Google`);
    });
  });

  describe('main navigation', () => {
    let breakpointObserver: FakeBreakpointObserver;
    let navFixture: ComponentFixture<App>;

    beforeEach(async () => {
      breakpointObserver = new FakeBreakpointObserver();
      const standaloneMockActivatedRoute = {
        snapshot: {
          queryParams: {},
          queryParamMap: convertToParamMap({'is_embedded_mode': 'false'}),
        } as unknown as ActivatedRoute['snapshot'],
        queryParamMap: of(convertToParamMap({'is_embedded_mode': 'false'})),
      };

      await TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [NoopAnimationsModule, App],
        providers: [
          // A page to navigate to that keeps the shell shown.
          provideRouter([{path: 'devices', children: []}]),
          {provide: APP_DATA, useValue: appData},
          {provide: UrlService, useValue: mockUrlService},
          {provide: ActivatedRoute, useValue: standaloneMockActivatedRoute},
          {provide: BreakpointObserver, useValue: breakpointObserver},
        ],
      }).compileComponents();
    });

    /** Creates the standalone app in a window of the given width. */
    async function createApp(width: number): Promise<MatSidenavHarness> {
      breakpointObserver.setWidth(width);
      navFixture = TestBed.createComponent(App);
      navFixture.detectChanges();
      return TestbedHarnessEnvironment.loader(navFixture).getHarness(
        MatSidenavHarness,
      );
    }

    function resizeWindow(width: number) {
      breakpointObserver.setWidth(width);
      navFixture.detectChanges();
    }

    function getMenuButton(): HTMLButtonElement {
      return navFixture.nativeElement.querySelector('.toggleSidenavButton');
    }

    function clickMenuButton() {
      getMenuButton().click();
      navFixture.detectChanges();
    }

    /** Opens the menu with the menu button focused, as a click on it does. */
    function focusAndClickMenuButton() {
      getMenuButton().focus();
      clickMenuButton();
    }

    function pressEscape(target: Element) {
      target.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: 'Escape',
          keyCode: ESCAPE,
          bubbles: true,
        }),
      );
    }

    function getDrawerElement(): HTMLElement {
      return navFixture.nativeElement.querySelector('mat-sidenav');
    }

    function isRail(): boolean {
      return navFixture.nativeElement
        .querySelector('.app-sidenav-container')
        .classList.contains('nav-rail');
    }

    function getDrawer(): MatSidenav {
      return navFixture.debugElement
        .query(By.directive(MatSidenav))
        .injector.get(MatSidenav);
    }

    /**
     * Resolves when the drawer has opened, which Material reports in a timeout
     * (after its animation); the drawer moves the focus at that point.
     */
    async function whenDrawerOpens(): Promise<void> {
      await firstValueFrom(getDrawer().openedChange.pipe(filter(Boolean)));
    }

    /**
     * Resolves when the drawer starts to close by itself, which Material
     * reports in a timeout after a backdrop click or the Escape key.
     */
    function whenDrawerCloses(): Promise<void> {
      return firstValueFrom(getDrawer().closedStart);
    }

    it('shows the drawer beside the page, open, at 1280px', async () => {
      const sidenav = await createApp(1280);

      expect(await sidenav.getMode()).toBe('side');
      expect(await sidenav.isOpen()).toBeTrue();
      expect(isRail()).toBeFalse();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('true');
    });

    it('hides and shows the drawer with the menu button at 1280px and wider', async () => {
      const sidenav = await createApp(1440);

      clickMenuButton();
      expect(await sidenav.isOpen()).toBeFalse();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('false');

      clickMenuButton();
      expect(await sidenav.isOpen()).toBeTrue();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('true');
    });

    it('keeps the drawer beside the page open on Escape', async () => {
      const sidenav = await createApp(1440);

      pressEscape(getDrawerElement());

      expect(await sidenav.isOpen()).toBeTrue();
    });

    it('shows the icon rail at 1279px', async () => {
      const sidenav = await createApp(1279);

      expect(await sidenav.getMode()).toBe('side');
      expect(await sidenav.isOpen()).toBeTrue();
      expect(isRail()).toBeTrue();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('false');
    });

    it('shows the icon rail at 768px', async () => {
      const sidenav = await createApp(768);

      expect(await sidenav.getMode()).toBe('side');
      expect(await sidenav.isOpen()).toBeTrue();
      expect(isRail()).toBeTrue();
    });

    it('expands the icon rail into the full drawer with the menu button', async () => {
      const sidenav = await createApp(1024);

      clickMenuButton();
      expect(isRail()).toBeFalse();
      expect(await sidenav.getMode()).toBe('side');
      expect(await sidenav.isOpen()).toBeTrue();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('true');

      clickMenuButton();
      expect(isRail()).toBeTrue();
      expect(await sidenav.isOpen()).toBeTrue();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('false');
    });

    it('shows a closed drawer over the page at 767px', async () => {
      const sidenav = await createApp(767);

      expect(await sidenav.getMode()).toBe('over');
      expect(await sidenav.isOpen()).toBeFalse();
      expect(isRail()).toBeFalse();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('false');
    });

    it('opens the overlay drawer with the menu button and closes it after a navigation', async () => {
      const sidenav = await createApp(640);

      clickMenuButton();
      expect(await sidenav.isOpen()).toBeTrue();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('true');

      await TestBed.inject(Router).navigateByUrl('/devices');
      navFixture.detectChanges();

      expect(await sidenav.isOpen()).toBeFalse();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('false');
    });

    it('focuses the overlay drawer, not its first link, when it opens', async () => {
      await createApp(640);
      const drawerOpens = whenDrawerOpens();

      focusAndClickMenuButton();
      await drawerOpens;

      expect(document.activeElement).toBe(
        getDrawerElement().querySelector('.sidenav-container'),
      );
    });

    it('closes the overlay drawer on a backdrop click and focuses the menu button', async () => {
      const sidenav = await createApp(640);
      const drawerOpens = whenDrawerOpens();
      focusAndClickMenuButton();
      await drawerOpens;
      const drawerCloses = whenDrawerCloses();

      navFixture.nativeElement.querySelector('.mat-drawer-backdrop').click();
      await drawerCloses;
      navFixture.detectChanges();

      expect(await sidenav.isOpen()).toBeFalse();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(getMenuButton());
      // The state stays in sync: one click on the menu button opens it again.
      clickMenuButton();
      expect(await sidenav.isOpen()).toBeTrue();
    });

    it('closes the overlay drawer on Escape and focuses the menu button', async () => {
      const sidenav = await createApp(640);
      const drawerOpens = whenDrawerOpens();
      focusAndClickMenuButton();
      await drawerOpens;
      const drawerCloses = whenDrawerCloses();

      pressEscape(document.activeElement!);
      await drawerCloses;
      navFixture.detectChanges();

      expect(await sidenav.isOpen()).toBeFalse();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(getMenuButton());
    });

    it('starts each mode from its default when the window crosses a breakpoint', async () => {
      const sidenav = await createApp(1440);
      clickMenuButton();

      resizeWindow(1024);
      expect(isRail()).toBeTrue();
      expect(await sidenav.isOpen()).toBeTrue();

      resizeWindow(640);
      expect(await sidenav.getMode()).toBe('over');
      expect(await sidenav.isOpen()).toBeFalse();

      resizeWindow(1440);
      expect(await sidenav.getMode()).toBe('side');
      expect(await sidenav.isOpen()).toBeTrue();
      expect(getMenuButton().getAttribute('aria-expanded')).toBe('true');
    });
  });

  describe('isNavActive', () => {
    it('should correctly identify active navigation sections', () => {
      expect(component.isNavActive('home')).toBeTrue();
      expect(component.isNavActive('devices')).toBeFalse();

      const routeSpy = spyOn(component, 'getCurrentRoutePath');

      routeSpy.and.returnValue('home');
      expect(component.isNavActive('home')).toBeTrue();
      expect(component.isNavActive('summary')).toBeFalse();

      routeSpy.and.returnValue('summary');
      expect(component.isNavActive('summary')).toBeTrue();
      expect(component.isNavActive('home')).toBeFalse();

      routeSpy.and.returnValue('devices');
      expect(component.isNavActive('devices')).toBeTrue();
      expect(component.isNavActive('home')).toBeFalse();

      routeSpy.and.returnValue('hosts');
      expect(component.isNavActive('hosts')).toBeTrue();

      routeSpy.and.returnValue('tests');
      expect(component.isNavActive('tests')).toBeTrue();

      routeSpy.and.returnValue('jobs');
      expect(component.isNavActive('jobs')).toBeTrue();

      routeSpy.and.returnValue('jobs/test_job/tests/1');
      expect(component.isNavActive('jobs')).toBeFalse();

      routeSpy.and.returnValue('sessions');
      expect(component.isNavActive('sessions')).toBeTrue();

      routeSpy.and.returnValue('assistant/conv-1');
      expect(component.isNavActive('assistant')).toBeTrue();
      routeSpy.and.returnValue('home/assistant');
      expect(component.isNavActive('assistant')).toBeFalse();

      routeSpy.and.returnValue('search');
      expect(component.isNavActive('home')).toBeFalse();
      expect(component.isNavActive('devices')).toBeFalse();
      expect(component.isNavActive('hosts')).toBeFalse();
      expect(component.isNavActive('tests')).toBeFalse();
      expect(component.isNavActive('jobs')).toBeFalse();
      expect(component.isNavActive('sessions')).toBeFalse();
      expect(component.isNavActive('assistant')).toBeFalse();

      routeSpy.and.returnValue('unknown');
      expect(component.isNavActive('home')).toBeFalse();
      expect(component.isNavActive('assistant')).toBeFalse();
    });

    it('should set showContent=true when route path is assistant and false when unknown', () => {
      const router = TestBed.inject(Router);
      component.isFakeData = false;
      component.showContent = false;
      spyOnProperty(router, 'routerState', 'get').and.returnValue({
        snapshot: {
          root: {
            firstChild: {
              firstChild: null,
              routeConfig: {path: 'assistant'},
              params: {},
            },
          },
        },
      } as unknown as Router['routerState']);

      component.updateShowContent();
      expect(component.showContent).toBeTrue();
    });

    it('should set showContent=true when route path is summary', () => {
      const router = TestBed.inject(Router);
      component.isFakeData = false;
      component.showContent = false;
      spyOnProperty(router, 'routerState', 'get').and.returnValue({
        snapshot: {
          root: {
            firstChild: {
              firstChild: null,
              routeConfig: {path: 'summary'},
              params: {},
            },
          },
        },
      } as unknown as Router['routerState']);

      component.updateShowContent();
      expect(component.showContent).toBeTrue();
    });
  });

  describe('isShowHeaderJumpBar', () => {
    it('should return false on a route whose data hides the jump bar', () => {
      component.isEmbeddedMode = false;
      spyOn(component, 'getCurrentRoutePath').and.returnValue('home');
      const dataSpy = spyOn(component, 'getCurrentRouteData');

      dataSpy.and.returnValue({'hideHeaderJumpBar': true});
      expect(component.isShowHeaderJumpBar()).toBeFalse();

      dataSpy.and.returnValue({});
      expect(component.isShowHeaderJumpBar()).toBeTrue();
    });

    it('should return false in embedded mode regardless of route', () => {
      component.isEmbeddedMode = true;
      spyOn(component, 'getCurrentRoutePath').and.returnValue('home');
      expect(component.isShowHeaderJumpBar()).toBeFalse();
    });

    it('should return true on Home, /search, and Detail routes in standalone mode', () => {
      component.isEmbeddedMode = false;
      const routeSpy = spyOn(component, 'getCurrentRoutePath');

      routeSpy.and.returnValue('home');
      expect(component.isShowHeaderJumpBar()).toBeTrue();

      routeSpy.and.returnValue('search');
      expect(component.isShowHeaderJumpBar()).toBeTrue();

      routeSpy.and.returnValue('devices/:id');
      expect(component.isShowHeaderJumpBar()).toBeTrue();

      routeSpy.and.returnValue('hosts/:hostName');
      expect(component.isShowHeaderJumpBar()).toBeTrue();

      routeSpy.and.returnValue('jobs/:jobId/tests/:id');
      expect(component.isShowHeaderJumpBar()).toBeTrue();

      routeSpy.and.returnValue('jobs/:id');
      expect(component.isShowHeaderJumpBar()).toBeTrue();

      routeSpy.and.returnValue('sessions/:id');
      expect(component.isShowHeaderJumpBar()).toBeTrue();
    });

    it('should return false on the 5 Entity Search routes in standalone mode', () => {
      component.isEmbeddedMode = false;
      const routeSpy = spyOn(component, 'getCurrentRoutePath');

      for (const searchPath of [
        'devices',
        'hosts',
        'tests',
        'jobs',
        'sessions',
      ]) {
        routeSpy.and.returnValue(searchPath);
        expect(component.isShowHeaderJumpBar()).toBeFalse();
      }
    });
  });

  describe('embedded header band', () => {
    let routeSpy: jasmine.Spy;
    let slot: WritableSignal<EmbeddedHeaderSlot | null>;

    beforeEach(() => {
      routeSpy = spyOn(component, 'getCurrentRoutePath');
      slot =
        mockUrlService.embeddedHeaderSlot as WritableSignal<EmbeddedHeaderSlot | null>;
      // Embedded in the ATS host (OSS build) unless a test says otherwise.
      mockUrlService.isInAtsEmbeddedMode.and.returnValue(true);
    });

    it('shows the band on entity search routes in ATS embedded mode and requests the host slot once', () => {
      component.isEmbeddedMode = true;
      routeSpy.and.returnValue('devices');

      component.updateShowContent();
      fixture.detectChanges();

      expect(component.showEmbeddedHeaderBand).toBeTrue();
      expect(mockUrlService.requestEmbeddedHeaderSlot).toHaveBeenCalledTimes(1);
      const band = fixture.nativeElement.querySelector(
        '.embedded-header-band',
      ) as HTMLElement;
      expect(band).not.toBeNull();
      expect(band.style.height).toBe('64px');

      // Navigating to the hosts search page keeps the band without a new request.
      routeSpy.and.returnValue('hosts');
      component.updateShowContent();
      expect(component.showEmbeddedHeaderBand).toBeTrue();
      expect(mockUrlService.requestEmbeddedHeaderSlot).toHaveBeenCalledTimes(1);
    });

    it('positions the search dock from the slot geometry sent by the host', () => {
      component.isEmbeddedMode = true;
      routeSpy.and.returnValue('devices');
      component.updateShowContent();
      slot.set({
        left: 240,
        width: 800,
        height: 56,
        borderColor: 'rgb(1, 2, 3)',
      });
      fixture.detectChanges();

      const band = fixture.nativeElement.querySelector(
        '.embedded-header-band',
      ) as HTMLElement;
      const dock = fixture.nativeElement.querySelector(
        '.embedded-header-search-dock',
      ) as HTMLElement;
      expect(band.style.height).toBe('56px');
      expect(band.style.borderBottomColor).toBe('rgb(1, 2, 3)');
      expect(dock.style.marginLeft).toBe('240px');
      expect(dock.style.width).toBe('800px');
    });

    it('hides the band on detail routes and in standalone mode', () => {
      component.isEmbeddedMode = true;
      routeSpy.and.returnValue('devices/:id');
      component.updateShowContent();
      expect(component.showEmbeddedHeaderBand).toBeFalse();

      component.isEmbeddedMode = false;
      mockUrlService.isInAtsEmbeddedMode.and.returnValue(false);
      routeSpy.and.returnValue('devices');
      component.updateShowContent();
      expect(component.showEmbeddedHeaderBand).toBeFalse();
      expect(mockUrlService.requestEmbeddedHeaderSlot).not.toHaveBeenCalled();
      fixture.detectChanges();
      expect(
        fixture.nativeElement.querySelector('.embedded-header-band'),
      ).toBeNull();
    });

    it('hides the band in the Google-internal embedded mode (e.g. Arsenal)', () => {
      component.isEmbeddedMode = true;
      mockUrlService.isInAtsEmbeddedMode.and.returnValue(false);
      routeSpy.and.returnValue('devices');

      component.updateShowContent();
      fixture.detectChanges();

      expect(component.showEmbeddedHeaderBand).toBeFalse();
      expect(mockUrlService.requestEmbeddedHeaderSlot).not.toHaveBeenCalled();
      expect(
        fixture.nativeElement.querySelector('.embedded-header-band'),
      ).toBeNull();
    });

    it('marks <body> with the band height so full-viewport layers (dialogs) stay below the band', () => {
      const container = document.body;
      component.isEmbeddedMode = true;
      routeSpy.and.returnValue('devices');

      // Band shown: <body> carries the band height for the offset of the
      // full-viewport layers (see app.scss).
      component.updateShowContent();
      fixture.detectChanges();
      expect(container.classList).toContain(EMBEDDED_HEADER_BAND_CLASS);
      expect(
        container.style.getPropertyValue(EMBEDDED_HEADER_BAND_HEIGHT_PROPERTY),
      ).toBe('64px');

      // The offset follows the geometry sent by the host.
      slot.set({left: 240, width: 800, height: 56});
      fixture.detectChanges();
      expect(
        container.style.getPropertyValue(EMBEDDED_HEADER_BAND_HEIGHT_PROPERTY),
      ).toBe('56px');

      // No band (e.g. a detail page): no offset.
      routeSpy.and.returnValue('devices/:id');
      component.updateShowContent();
      expect(container.classList).not.toContain(EMBEDDED_HEADER_BAND_CLASS);
      expect(
        container.style.getPropertyValue(EMBEDDED_HEADER_BAND_HEIGHT_PROPERTY),
      ).toBe('');
    });
  });

  describe('getPreservedQueryParams', () => {
    it('should return preserved query params from commonParamsService', () => {
      spyOn(commonParamsService, 'getCommonParams').and.returnValue({
        'is_embedded_mode': 'true',
        'debug': 'true',
        'fake_data': 'true',
      });
      const params = component.getPreservedQueryParams();
      expect(params['fake_data']).toBe('true');
      expect(params['is_embedded_mode']).toBe('true');
      expect(params['debug']).toBe('true');
    });

    it('should not include universe even if present in route queryParams', () => {
      if (mockActivatedRoute.snapshot) {
        mockActivatedRoute.snapshot.queryParams['universe'] = 'test-universe';
      }
      spyOn(commonParamsService, 'getCommonParams').and.returnValue({});
      const params = component.getPreservedQueryParams();
      expect(params['universe']).toBeUndefined();
    });

    it('should not add enable_assistant when on, and preserve enable_assistant=false when in the URL', () => {
      component.isAssistantEnabled = true;
      expect(
        component.getPreservedQueryParams()['enable_assistant'],
      ).toBeUndefined();

      mockActivatedRoute.snapshot = {
        queryParams: {'enable_assistant': 'false'},
        queryParamMap: convertToParamMap({'enable_assistant': 'false'}),
      } as unknown as ActivatedRoute['snapshot'];
      expect(component.getPreservedQueryParams()['enable_assistant']).toBe(
        'false',
      );
    });
  });
});
