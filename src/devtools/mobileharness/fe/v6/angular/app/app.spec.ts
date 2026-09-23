import {BreakpointObserver, BreakpointState} from '@angular/cdk/layout';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatTooltip} from '@angular/material/tooltip';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {BehaviorSubject, Subject, of} from 'rxjs';

import {App} from './app';
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
  });

  describe('Navigation tooltips in icon-only mode', () => {
    let breakpointSubject: BehaviorSubject<BreakpointState>;
    let mockBreakpointObserver: jasmine.SpyObj<BreakpointObserver>;

    beforeEach(async () => {
      breakpointSubject = new BehaviorSubject<BreakpointState>({
        matches: false,
        breakpoints: {'(max-width: 1100px)': false},
      });

      mockBreakpointObserver = jasmine.createSpyObj<BreakpointObserver>(
        'BreakpointObserver',
        ['observe', 'isMatched'],
      );
      mockBreakpointObserver.observe.and.returnValue(breakpointSubject);
      mockBreakpointObserver.isMatched.and.returnValue(false);

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
          {provide: BreakpointObserver, useValue: mockBreakpointObserver},
        ],
      }).compileComponents();
    });

    it('should disable nav item tooltips when not in icon-only mode and enable them in icon-only mode', () => {
      const testFixture = TestBed.createComponent(App);
      testFixture.detectChanges();

      const homeLink = testFixture.debugElement.query(
        By.css('mat-nav-list a[routerLink="/home"]'),
      );
      const tooltip = homeLink.injector.get(MatTooltip);

      expect(testFixture.componentInstance.isIconOnly()).toBeFalse();
      expect(tooltip.disabled).toBeTrue();

      breakpointSubject.next({
        matches: true,
        breakpoints: {'(max-width: 1100px)': true},
      });
      testFixture.detectChanges();

      expect(testFixture.componentInstance.isIconOnly()).toBeTrue();
      expect(tooltip.disabled).toBeFalse();
    });
  });

  it('should call logout without error', () => {
    expect(() => {
      component.logout();
    }).not.toThrow();
  });
});
