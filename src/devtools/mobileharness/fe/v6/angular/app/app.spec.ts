import {signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import {of, Subject} from 'rxjs';

import {
  App,
  EMBEDDED_HEADER_BAND_CLASS,
  EMBEDDED_HEADER_BAND_HEIGHT_PROPERTY,
} from './app';
import {APP_DATA, type AppData} from './core/models/app_data';
import {CommonParamsService} from './core/services/common_params_service';
import {type EmbeddedHeaderSlot, UrlService} from './core/services/url_service';

describe('App Component', () => {
  let component: App;
  let fixture: ComponentFixture<App>;
  let mockUrlService: jasmine.SpyObj<UrlService>;
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

  it('should have sideNavExpanded true by default', () => {
    expect(component.sideNavExpanded).toBeTrue();
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

    it('should be in standalone mode with expanded sideNav by default', () => {
      expect(standaloneComponent.isStandaloneMode).toBeTrue();
      expect(standaloneComponent.sideNavExpanded).toBeTrue();
      const sidenav =
        standaloneFixture.nativeElement.querySelector('mat-sidenav');
      expect(sidenav).toBeTruthy();
    });

    it('should toggle sideNavExpanded when menu button is clicked', () => {
      const toggleButton: HTMLButtonElement =
        standaloneFixture.nativeElement.querySelector('.toggleSidenavButton');
      expect(toggleButton).toBeTruthy();

      toggleButton.click();
      standaloneFixture.detectChanges();
      expect(standaloneComponent.sideNavExpanded).toBeFalse();

      toggleButton.click();
      standaloneFixture.detectChanges();
      expect(standaloneComponent.sideNavExpanded).toBeTrue();
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
      slot = mockUrlService.embeddedHeaderSlot as WritableSignal<
        EmbeddedHeaderSlot | null
      >;
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
      slot.set({left: 240, width: 800, height: 56, borderColor: 'rgb(1, 2, 3)'});
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
      expect(container.classList).not.toContain(
        EMBEDDED_HEADER_BAND_CLASS,
      );
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

    it('should include enable_assistant when assistant feature is enabled', () => {
      component.isAssistantEnabled = true;
      const params = component.getPreservedQueryParams();
      expect(params['enable_assistant']).toBe('true');
    });

    it('should not include enable_assistant when assistant feature is disabled', () => {
      component.isAssistantEnabled = false;
      const params = component.getPreservedQueryParams();
      expect(params['enable_assistant']).toBeUndefined();
    });
  });
});
