import {HttpClient, provideHttpClient, withInterceptors} from '@angular/common/http';
import {ApplicationConfig, provideZonelessChangeDetection} from '@angular/core';
import {provideAnimations} from '@angular/platform-browser/animations';
import {ActivatedRoute, provideRouter} from '@angular/router';

import {routes} from './app_routes';
import {authInterceptor} from './core/interceptors/auth_interceptor';
import {APP_DATA, getAppData} from './core/models/app_data';
import {CONFIG_SERVICE} from './core/services/config/config_service';
import {FakeConfigService} from './core/services/config/fake_config_service';
import {HttpConfigService} from './core/services/config/http_config_service';
import {DEVICE_SERVICE} from './core/services/device/device_service';
import {FakeDeviceService} from './core/services/device/fake_device_service';
import {HttpDeviceService} from './core/services/device/http_device_service';


/**
 * The application configuration.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    {
      provide: DEVICE_SERVICE,
      useFactory: (route: ActivatedRoute, http: HttpClient) => {
        const useFakeData = route.snapshot.queryParams['fake_data'] === 'true';
        return useFakeData ? new FakeDeviceService() :
                             new HttpDeviceService(http);
      },
      deps: [ActivatedRoute, HttpClient],
    },
    {
      provide: CONFIG_SERVICE,
      useFactory: (route: ActivatedRoute, http: HttpClient) => {
        const useFakeData = route.snapshot.queryParams['fake_data'] === 'true';
        return useFakeData ? new FakeConfigService() :
                             new HttpConfigService(http);
      },
      deps: [ActivatedRoute, HttpClient],
    },
    provideAnimations(),
    {
      provide: APP_DATA,
      // Accessing a global window object.
      useValue: getAppData(),
    },
  ],
};
