import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {
  ApplicationConfig,
  importProvidersFrom,
  provideZonelessChangeDetection,
} from '@angular/core';
import {MatSnackBarModule} from '@angular/material/snack-bar';
import {provideAnimations} from '@angular/platform-browser/animations';
import {ActivatedRoute, provideRouter} from '@angular/router';

import {routes} from './app_routes';
import {authInterceptor} from './core/interceptors/auth_interceptor';
import {forceReadyInterceptor} from './core/interceptors/force_ready_interceptor';
import {universeInterceptor} from './core/interceptors/universe_interceptor';
import {APP_DATA, getAppData} from './core/models/app_data';
import {CONFIG_SERVICE} from './core/services/config/config_service';
import {FakeConfigService} from './core/services/config/fake_config_service';
import {HttpConfigService} from './core/services/config/http_config_service';
import {DEVICE_SERVICE} from './core/services/device/device_service';
import {HttpDeviceService} from './core/services/device/http_device_service';
import {InterceptedFakeDeviceService} from './core/services/device/intercepted_fake_device_service';
import {HOST_SERVICE} from './core/services/host/host_service';
import {HttpHostService} from './core/services/host/http_host_service';
import {InterceptedFakeHostService} from './core/services/host/intercepted_fake_host_service';

/**
 * The application configuration.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    importProvidersFrom(MatSnackBarModule),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([
        authInterceptor,
        universeInterceptor,
        forceReadyInterceptor,
      ]),
    ),
    {
      provide: DEVICE_SERVICE,
      useFactory: (route: ActivatedRoute) => {
        const useFakeData = route.snapshot.queryParams['fake_data'] === 'true';
        return useFakeData
          ? new InterceptedFakeDeviceService()
          : new HttpDeviceService();
      },
      deps: [ActivatedRoute],
    },
    {
      provide: HOST_SERVICE,
      useFactory: (route: ActivatedRoute) => {
        const useFakeData = route.snapshot.queryParams['fake_data'] === 'true';
        return useFakeData
          ? new InterceptedFakeHostService()
          : new HttpHostService();
      },
      deps: [ActivatedRoute],
    },
    {
      provide: CONFIG_SERVICE,
      useFactory: (route: ActivatedRoute) => {
        const useFakeData = route.snapshot.queryParams['fake_data'] === 'true';
        return useFakeData ? new FakeConfigService() : new HttpConfigService();
      },
      deps: [ActivatedRoute],
    },
    provideAnimations(),
    {
      provide: APP_DATA,
      // Accessing a global window object.
      useValue: getAppData(),
    },
  ],
};
