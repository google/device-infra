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

import {CommonModule} from '@angular/common';
import {HttpClientModule} from '@angular/common/http';
import {Component, ElementRef, EventEmitter, Inject, OnDestroy, ViewChild, ViewEncapsulation} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatDividerModule} from '@angular/material/divider';
import {MatIconModule} from '@angular/material/icon';
import {MatListModule} from '@angular/material/list';
import {MatSidenavModule} from '@angular/material/sidenav';
import {MatToolbarModule} from '@angular/material/toolbar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterModule, Routes} from '@angular/router';
import {ReplaySubject} from 'rxjs';

import {DeviceDetailsPage} from './devices/device_details_page';
import {DeviceListPage} from './devices/device_list_page';
import {Home} from './home/home';
import {HostDetailsPage} from './hosts/host_details_page';
import {HostListPage} from './hosts/host_list_page';
import {APP_DATA, type AppData} from './services/app_data';

/** Routing paths for the sidenav */
export const routes: Routes = [
  {path: 'hosts', component: HostListPage},
  {path: 'hosts/:id', component: HostDetailsPage},
  {path: 'devices', component: DeviceListPage},
  {path: 'devices/:id', component: DeviceDetailsPage},
  {path: 'home', component: Home},
  {path: '**', redirectTo: '/home', pathMatch: 'full'},
];

/** Homepage */
@Component({
  standalone: true,
  selector: 'app-root',
  encapsulation: ViewEncapsulation.None,
  styleUrls: ['./app.scss'],
  templateUrl: './app.ng.html',
  imports: [
    CommonModule,
    HttpClientModule,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    MatTooltipModule,
    RouterModule,
  ],
})
export class App implements OnDestroy {
  @ViewChild('toggleSidenavButton', {read: ElementRef})
  toggleSidenavButton!: ElementRef<HTMLButtonElement>;
  sideNavExpanded = false;
  atsMajorVersionStr = '';
  netdataUrl = '';
  private readonly destroy = new ReplaySubject<void>();

  constructor(
      @Inject(APP_DATA) readonly appData: AppData,
  ) {
    if (appData.isOmniLabBased) {
      this.atsMajorVersionStr = 'ATS 2.0';
    } else {
      this.atsMajorVersionStr = 'ATS 1.0';
    }
    // this.initRouteTrackingForAnalytics();
    // this.checkUserPermission();

    // Accessing a global window object.
    const globalEventEmitter =
        (window as unknown as {[key: string]: unknown})['globalEventEmitter'] as
        EventEmitter<string>;

    if (globalEventEmitter) {
      globalEventEmitter['on']('ogb-menu-clicked', () => {
        console.info('menu option button clicked');
        this.toggleSidenavButton.nativeElement.click();
      });
      console.info('globalEventEmitter listened');
    } else {
      console.info('globalEventEmitter not found');
    }
  }

  ngOnDestroy() {
    this.destroy.next();
    this.destroy.complete();
  }
}
