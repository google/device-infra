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
import {ChangeDetectionStrategy, Component, inject, OnDestroy, ViewEncapsulation} from '@angular/core';
import {MatButtonModule, MatIconButton} from '@angular/material/button';
import {MatDividerModule} from '@angular/material/divider';
import {MatIconModule} from '@angular/material/icon';
import {MatListModule} from '@angular/material/list';
import {MatMenuModule} from '@angular/material/menu';
import {MatSidenavModule} from '@angular/material/sidenav';
import {MatToolbarModule} from '@angular/material/toolbar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterModule} from '@angular/router';
// an example of using absolute path for source code import.
import {APP_DATA, type AppData} from 'app/core/models/app_data';
import {ReplaySubject} from 'rxjs';


/** Homepage */
@Component({
  standalone: true,
  selector: 'app-root',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./app.scss'],
  templateUrl: './app.ng.html',
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconButton,
    MatDividerModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatSidenavModule,
    MatToolbarModule,
    MatTooltipModule,
    RouterModule,
  ],
})
export class App implements OnDestroy {
  sideNavExpanded = false;
  atsMajorVersionStr = '';
  private readonly destroy = new ReplaySubject<void>();
  readonly appData: AppData = inject(APP_DATA);
  showVersionInfo = true;

  constructor() {
  }

  logout() {
    // TODO: Implement this.
    console.log('logout');
  }

  ngOnDestroy() {
    this.destroy.next();
    this.destroy.complete();
  }
}
