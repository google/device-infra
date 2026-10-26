import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ActivatedRoute, convertToParamMap, ParamMap} from '@angular/router';
import {BehaviorSubject} from 'rxjs';
import {stayOnLegacyFe} from '../../../core/models/app_data';
import {EnvUniverseService} from '../../../core/services/env_universe_service';
import {LegacyConsoleBanner} from './legacy_console_banner';

describe('stayOnLegacyFe', () => {
  it('adds the opt-out as the only query parameter', () => {
    expect(stayOnLegacyFe('https://mhfe/labdetailview/h/1.2.3.4')).toBe(
      'https://mhfe/labdetailview/h/1.2.3.4?redirect_to_v6=0',
    );
  });

  it('appends the opt-out to an existing query', () => {
    expect(stayOnLegacyFe('https://mhfe/jobdetailview/j1?tab=2')).toBe(
      'https://mhfe/jobdetailview/j1?tab=2&redirect_to_v6=0',
    );
  });

  it('keeps the fragment at the end', () => {
    expect(stayOnLegacyFe('https://mhfe/homeview#subpage=x')).toBe(
      'https://mhfe/homeview?redirect_to_v6=0#subpage=x',
    );
  });

  it('keeps an empty URL empty', () => {
    expect(stayOnLegacyFe('')).toBe('');
  });
});

describe('LegacyConsoleBanner', () => {
  let component: LegacyConsoleBanner;
  let fixture: ComponentFixture<LegacyConsoleBanner>;
  let queryParamMapSubject: BehaviorSubject<ParamMap>;
  let mockActivatedRoute: Partial<ActivatedRoute>;
  let mockEnvUniverseService: jasmine.SpyObj<EnvUniverseService>;

  beforeEach(async () => {
    queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));
    mockActivatedRoute = {
      queryParamMap: queryParamMapSubject.asObservable(),
    };

    mockEnvUniverseService = jasmine.createSpyObj('EnvUniverseService', [
      'isGoogle1P',
    ]);
    mockEnvUniverseService.isGoogle1P.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [LegacyConsoleBanner, NoopAnimationsModule],
      providers: [
        {provide: ActivatedRoute, useValue: mockActivatedRoute},
        {provide: EnvUniverseService, useValue: mockEnvUniverseService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LegacyConsoleBanner);
    component = fixture.componentInstance;
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should show by default when standalone and google1p', () => {
    fixture.componentRef.setInput('legacyUrl', 'http://legacy');
    fixture.componentRef.setInput('bannerType', 'host');
    fixture.detectChanges();

    expect(component.shouldShow()).toBeTrue();
  });

  it('links to the legacy page with the FE v5 redirect opt-out', () => {
    fixture.componentRef.setInput('legacyUrl', 'http://legacy/labdetailview/h');
    fixture.componentRef.setInput('bannerType', 'host');
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a.legacy-link');
    expect(link.getAttribute('href')).toBe(
      'http://legacy/labdetailview/h?redirect_to_v6=0',
    );
  });

  it('should not show when embedded', () => {
    queryParamMapSubject.next(convertToParamMap({'is_embedded_mode': 'true'}));

    fixture.componentRef.setInput('legacyUrl', 'http://legacy');
    fixture.componentRef.setInput('bannerType', 'host');
    fixture.detectChanges();

    expect(component.shouldShow()).toBeFalse();
  });

  it('should not show when not google1p', () => {
    mockEnvUniverseService.isGoogle1P.and.returnValue(false);

    fixture.componentRef.setInput('legacyUrl', 'http://legacy');
    fixture.componentRef.setInput('bannerType', 'host');
    fixture.detectChanges();

    expect(component.shouldShow()).toBeFalse();
  });

  it('should not show when legacyUrl is empty', () => {
    fixture.componentRef.setInput('legacyUrl', '');
    fixture.componentRef.setInput('bannerType', 'host');
    fixture.detectChanges();

    expect(component.shouldShow()).toBeFalse();
  });
});
