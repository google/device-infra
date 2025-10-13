/**
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {provideHttpClient} from '@angular/common/http';
import {provideZoneChangeDetection} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {bootstrapApplication} from '@angular/platform-browser';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
// need to remove in opensourced version begin
import {initModuleManager} from 'google3/javascript/modulesets/initmodulemanager';

// need to remove in opensourced version end
import {App, routes} from './app/app';
import {APP_DATA, AppData} from './app/services';

// need to remove in opensourced version begin
// Initialize the module manager.
initModuleManager();
// need to remove in opensourced version end

class EventEmitter {
  listeners: {[event: string]: Array<(data?: unknown) => void>} = {};

  on(event: string, listener: (data?: unknown) => void) {
    if (!this.listeners[event]) {
      this.listeners[event] = [];
    }
    this.listeners[event].push(listener);
  }

  emit(event: string, data?: unknown) {
    if (this.listeners[event]) {
      this.listeners[event].forEach((listener) => {
        listener(data);
      });
    }
  }
}

declare global {
  interface Window {
    globalEventEmitter: EventEmitter;
  }
}

function getAppData(): AppData {
  const appDataElement = document.getElementById('app-data');
  if (!appDataElement) {
    throw new Error('Init data: app-data element not found!');
  }
  const appData = JSON.parse(appDataElement.textContent || '{}');
  // use appData
  return appData as AppData;
}

// TODO: Remove this function after onegoogle bar is permission issue
// fixed.
function initGlobalEventEmitter() {
  /** The global event emitter instance. */
  window.globalEventEmitter = new EventEmitter();

  // OGB renders the menu button with the 'gb_Zc' class.
  // We can listen for clicks on elements with this class.
  document.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const parentElement = target && target.parentElement;
    const grandParentElement = parentElement && parentElement.parentElement;
    // console.log(parentElement.outerHTML);
    if ((target && target.classList && target.classList.contains('gb_Zc')) ||
        (parentElement && parentElement.classList &&
         parentElement.classList.contains('gb_Zc')) ||
        (grandParentElement && grandParentElement.classList &&
         grandParentElement.classList.contains('gb_Zc'))) {
      window.globalEventEmitter.emit('ogb-menu-clicked');
      event.preventDefault();
    }
  });
}

initGlobalEventEmitter();

// Bootstrap the application
bootstrapApplication(App, {
  providers: [
    provideAnimations(),
    provideHttpClient(),
    provideRouter(routes),
    provideZoneChangeDetection(),
    {
      provide: APP_DATA,
      // Accessing a global window object.
      // tslint:disable-next-line:no-any
      // useValue: (window as any)['APP_DATA'],
      useValue: getAppData(),
    },
    {
      provide: MAT_DIALOG_DATA,
      useValue: {},
    },
  ],
});
