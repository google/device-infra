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
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  OnDestroy,
  ViewEncapsulation,
} from '@angular/core';
import {MatButtonModule, MatIconButton} from '@angular/material/button';
import {MatDividerModule} from '@angular/material/divider';
import {MatIconModule} from '@angular/material/icon';
import {MatListModule} from '@angular/material/list';
import {MatMenuModule} from '@angular/material/menu';
import {MatSidenavModule} from '@angular/material/sidenav';
import {MatToolbarModule} from '@angular/material/toolbar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterModule,
} from '@angular/router';
// an example of using absolute path for source code import.
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {
  APP_DATA,
  type AppData,
} from 'app/core/models/app_data';
import {UrlService} from 'app/core/services/url_service';
import {navigateWithPreservedParams} from 'app/core/utils/url_utils';
import {LoadingService} from 'app/shared/services/loading_service';
import {ReplaySubject} from 'rxjs';
import {filter, takeUntil} from 'rxjs/operators';

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
    MatProgressSpinnerModule,
  ],
})
export class App implements OnDestroy {
  sideNavExpanded = false;
  private readonly destroy = new ReplaySubject<void>();
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly cdr: ChangeDetectorRef = inject(ChangeDetectorRef);
  private readonly router: Router = inject(Router);
  readonly appData: AppData = inject(APP_DATA);
  readonly loadingService = inject(LoadingService);
  private readonly urlService = inject(UrlService);
  showVersionInfo = true;
  isEmbeddedMode = true;
  isFakeData = false;
  showContent = true;

  get isStandaloneMode(): boolean {
    return !this.isEmbeddedMode;
  }

  constructor() {
    this.route.queryParamMap
      .pipe(takeUntil(this.destroy))
      .subscribe((params) => {
        this.isEmbeddedMode = params.get('is_embedded_mode') === 'true';
        this.isFakeData = params.get('fake_data') === 'true';
        this.updateShowContent();
        this.cdr.markForCheck();
      });

    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntil(this.destroy),
      )
      .subscribe(() => {
        this.updateShowContent();
      });

    this.urlService.navigate$.pipe(takeUntil(this.destroy)).subscribe((url) => {
      navigateWithPreservedParams(url, this.router, this.route);
    });
  }

  updateShowContent() {
    if (this.isFakeData) {
      this.showContent = true;
      this.cdr.markForCheck();
      return;
    }

    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) {
      route = route.firstChild;
    }

    const path = route.routeConfig?.path;
    const params = route.params;

    if (path === 'devices/:id' && params['id']) {
      this.showContent = true;
    } else if (path === 'hosts/:hostName' && params['hostName']) {
      this.showContent = true;
    } else {
      this.showContent = false;
    }
    this.cdr.markForCheck();
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
