import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ActivatedRoute, convertToParamMap, provideRouter, Router} from '@angular/router';
import {Subject, of} from 'rxjs';

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
    isDevMode: false,
    labconsoleVersion: '1.0',
    overrideLabConsoleServerUrl: 'http://localhost:8080',
    applicationId: 'lab-console-oss',
    email: '',
    userDisplayName: '',
    startMode: 'ng-serve',
  };

  beforeEach(async () => {
    mockUrlService = jasmine.createSpyObj('UrlService', ['isInEmbeddedMode'], {
      navigate$: new Subject<string>(),
    });

    mockActivatedRoute = {
      snapshot: {
        queryParamMap: convertToParamMap({'is_embedded_mode': 'true'})
      } as unknown as ActivatedRoute['snapshot'],
      queryParamMap: of(convertToParamMap({'is_embedded_mode': 'true'}))
    };

    await TestBed
        .configureTestingModule({
          imports: [
            App,
            NoopAnimationsModule,
          ],
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
        })
        .compileComponents();

    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should merge query params on navigate', () => {
    const router = TestBed.inject(Router);
    const spy = spyOn(router, 'navigateByUrl');

    // Simulate receiving a navigate message
    (mockUrlService.navigate$ as Subject<string>).next('/devices/123');

    expect(spy).toHaveBeenCalledWith('/devices/123?is_embedded_mode=true');
  });

  it('should not override query params present in new URL', () => {
    const router = TestBed.inject(Router);
    const spy = spyOn(router, 'navigateByUrl');

    // Simulate receiving a navigate message with existing param
    (mockUrlService.navigate$ as Subject<string>).next('/devices/123?is_embedded_mode=false');

    expect(spy).toHaveBeenCalledWith('/devices/123?is_embedded_mode=false');
  });
});
