import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ActivatedRoute, convertToParamMap, provideRouter} from '@angular/router';
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
            NoopAnimationsModule,
            App,
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
            }
          ],
        })
        .compileComponents();

    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the app', () => {
    expect(component).toBeTruthy();
  });

});
