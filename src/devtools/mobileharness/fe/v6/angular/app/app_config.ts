import {ApplicationConfig, provideZonelessChangeDetection} from '@angular/core';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {routes} from './app_routes';
import {APP_DATA, getAppData} from './core/models/app_data';
import {CONFIG_SERVICE} from './core/services/config/config_service';
import {FakeConfigService} from './core/services/config/fake_config_service';
import {DEVICE_SERVICE} from './core/services/device/device_service';
import {FakeDeviceService} from './core/services/device/fake_device_service';


/**
 * The application configuration.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
    {provide: CONFIG_SERVICE, useClass: FakeConfigService},
    provideAnimations(),
    {
      provide: APP_DATA,
      // Accessing a global window object.
      useValue: getAppData(),
    },
  ],
};
