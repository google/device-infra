import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {Subject, of} from 'rxjs';

import {App} from './app';
import {assistantFeatureGuard, isAssistantEnabled} from './app_routes';
import {APP_DATA, type AppData} from './core/models/app_data';
import {UrlService} from './core/services/url_service';

describe('App Component', () => {
  let component: App;
  let fixture: ComponentFixture<App>;
  let mockUrlService: jasmine.SpyObj<UrlService>;
  let mockActivatedRoute: Partial<ActivatedRoute>;

  const appData: AppData = {
    adbVersion: '1.0',
    mttVersion: '1.0',
  };

  beforeEach(async () => {
    mockUrlService = jasmine.createSpyObj('UrlService', ['isInEmbeddedMode'], {
      navigate$: new Subject<string>(),
    });

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
    fixture.detectChanges();
  });

  it('should create the app', () => {
    expect(component).toBeTruthy();
  });

  it('should have sideNavExpanded true by default', () => {
    expect(component.sideNavExpanded).toBeTrue();
  });

  it('should initialize and update isAssistantEnabled from route queryParamMap', () => {
    const queryParamsSubject = new Subject<
      ReturnType<typeof convertToParamMap>
    >();
    const routeWithAssistantFlag = {
      snapshot: {
        queryParams: {'enable_assistant': 'true'},
        queryParamMap: convertToParamMap({'enable_assistant': 'true'}),
      } as unknown as ActivatedRoute['snapshot'],
      queryParamMap: queryParamsSubject.asObservable(),
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, App],
      providers: [
        provideRouter([]),
        {provide: APP_DATA, useValue: appData},
        {provide: UrlService, useValue: mockUrlService},
        {provide: ActivatedRoute, useValue: routeWithAssistantFlag},
      ],
    });

    const flagFixture = TestBed.createComponent(App);
    const flagComponent = flagFixture.componentInstance;
    flagFixture.detectChanges();

    expect(flagComponent.isAssistantEnabled).toBeTrue();

    queryParamsSubject.next(convertToParamMap({'enable_assistant': 'false'}));
    expect(flagComponent.isAssistantEnabled).toBeFalse();
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

    it('should not render assistant navigation link when feature is disabled', () => {
      const assistantLink = standaloneFixture.nativeElement.querySelector(
        'a[routerLink="/assistant"]',
      );
      expect(assistantLink).toBeNull();
    });

    it('should render assistant navigation link when feature is enabled', async () => {
      const enabledMockActivatedRoute = {
        snapshot: {
          queryParams: {'enable_assistant': 'true'},
          queryParamMap: convertToParamMap({
            'is_embedded_mode': 'false',
            'enable_assistant': 'true',
          }),
        } as unknown as ActivatedRoute['snapshot'],
        queryParamMap: of(
          convertToParamMap({
            'is_embedded_mode': 'false',
            'enable_assistant': 'true',
          }),
        ),
      };

      await TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [NoopAnimationsModule, App],
        providers: [
          provideRouter([]),
          {provide: APP_DATA, useValue: appData},
          {provide: UrlService, useValue: mockUrlService},
          {provide: ActivatedRoute, useValue: enabledMockActivatedRoute},
        ],
      }).compileComponents();

      const enabledFixture = TestBed.createComponent(App);
      enabledFixture.detectChanges();

      const assistantLink = enabledFixture.nativeElement.querySelector(
        'a[routerLink="/assistant"]',
      );
      expect(assistantLink).toBeTruthy();
      expect(assistantLink.textContent).toContain('Assistant');
      const icon = assistantLink.querySelector('mat-icon');
      expect(icon?.textContent?.trim()).toBe('smart_toy');
    });
  });

  describe('isNavActive', () => {
    it('should correctly identify active navigation sections', () => {
      expect(component.isNavActive('home')).toBeTrue();
      expect(component.isNavActive('devices')).toBeFalse();

      const routeSpy = spyOn(component, 'getCurrentRoutePath');

      routeSpy.and.returnValue('home');
      expect(component.isNavActive('home')).toBeTrue();

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

      routeSpy.and.returnValue('assistant');
      expect(component.isNavActive('assistant')).toBeTrue();

      routeSpy.and.returnValue('unknown');
      expect(component.isNavActive('home')).toBeFalse();
    });
  });

  describe('getPreservedQueryParams', () => {
    it('should return preserved query params including fake_data and is_embedded_mode', () => {
      component.isFakeData = true;
      component.isEmbeddedMode = true;
      const params = component.getPreservedQueryParams();
      expect(params['fake_data']).toBe('true');
      expect(params['is_embedded_mode']).toBe('true');
    });

    it('should include universe when present in route queryParams', () => {
      if (mockActivatedRoute.snapshot) {
        mockActivatedRoute.snapshot.queryParams['universe'] = 'test-universe';
      }
      const params = component.getPreservedQueryParams();
      expect(params['universe']).toBe('test-universe');
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

  it('should call logout without error', () => {
    expect(() => {
      component.logout();
    }).not.toThrow();
  });
});

describe('isAssistantEnabled', () => {
  it('should return true when enable_assistant query param is true', () => {
    const params = convertToParamMap({'enable_assistant': 'true'});
    expect(isAssistantEnabled(params, {isDevMode: false})).toBeTrue();
  });

  it('should return false when enable_assistant query param is false', () => {
    const params = convertToParamMap({'enable_assistant': 'false'});
    expect(isAssistantEnabled(params, {isDevMode: true})).toBeFalse();
  });

  it('should return true when localStorage has enable_assistant set to true', () => {
    spyOn(localStorage, 'getItem').and.callFake((key: string) => {
      return key === 'enable_assistant' ? 'true' : null;
    });
    const params = convertToParamMap({});
    expect(isAssistantEnabled(params, {isDevMode: false})).toBeTrue();
  });

  it('should return true when appData.isDevMode is true', () => {
    spyOn(localStorage, 'getItem').and.returnValue(null);
    const params = convertToParamMap({});
    expect(isAssistantEnabled(params, {isDevMode: true})).toBeTrue();
  });

  it('should return false when unflagged in production mode', () => {
    spyOn(localStorage, 'getItem').and.returnValue(null);
    const params = convertToParamMap({});
    expect(isAssistantEnabled(params, {isDevMode: false})).toBeFalse();
  });
});

describe('assistantFeatureGuard', () => {
  it('should allow access when enable_assistant query param is true', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {provide: APP_DATA, useValue: {isDevMode: false}},
      ],
    });
    const route = {
      queryParamMap: convertToParamMap({'enable_assistant': 'true'}),
    } as unknown as ActivatedRouteSnapshot;
    const state = {
      url: '/assistant?enable_assistant=true',
    } as unknown as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      assistantFeatureGuard(route, state),
    );
    expect(result).toBeTrue();
  });

  it('should allow access when appData.isDevMode is true', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {provide: APP_DATA, useValue: {isDevMode: true}},
      ],
    });
    const route = {
      queryParamMap: convertToParamMap({}),
    } as unknown as ActivatedRouteSnapshot;
    const state = {url: '/assistant'} as unknown as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      assistantFeatureGuard(route, state),
    );
    expect(result).toBeTrue();
  });

  it('should redirect to /home when unflagged in production', () => {
    spyOn(localStorage, 'getItem').and.returnValue(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {provide: APP_DATA, useValue: {isDevMode: false}},
      ],
    });
    const route = {
      queryParamMap: convertToParamMap({}),
    } as unknown as ActivatedRouteSnapshot;
    const state = {url: '/assistant'} as unknown as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      assistantFeatureGuard(route, state),
    );
    const router = TestBed.inject(Router);
    expect(result).toEqual(router.parseUrl('/home'));
  });

  it('should redirect to /home when enable_assistant query param is explicitly false', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {provide: APP_DATA, useValue: {isDevMode: true}},
      ],
    });
    const route = {
      queryParamMap: convertToParamMap({'enable_assistant': 'false'}),
    } as unknown as ActivatedRouteSnapshot;
    const state = {
      url: '/assistant?enable_assistant=false',
    } as unknown as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      assistantFeatureGuard(route, state),
    );
    const router = TestBed.inject(Router);
    expect(result).toEqual(router.parseUrl('/home'));
  });
});
