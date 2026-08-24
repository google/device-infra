import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ActivatedRoute, convertToParamMap, ParamMap} from '@angular/router';
import {BehaviorSubject} from 'rxjs';
import {EnvUniverseService} from '../../../core/services/env_universe_service';
import {LegacyConsoleBanner} from './legacy_console_banner';

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
