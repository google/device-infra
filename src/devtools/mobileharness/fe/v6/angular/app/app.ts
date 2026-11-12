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

import {PortalModule} from '@angular/cdk/portal';
import {CommonModule, DOCUMENT} from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  ViewEncapsulation,
  computed,
  effect,
  inject,
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
  getFaqsUrl,
  getLegacyFeUrl,
  getOmniLabUrl,
  getUserGuideUrl,
  stayOnLegacyFe,
} from '@deviceinfra/app/core/models/app_data';
import {CommonParamsService} from '@deviceinfra/app/core/services/common_params_service';
import {UrlService} from '@deviceinfra/app/core/services/url_service';
import {ENTITY_SEARCH_PATHS, isHeaderJumpBarVisibleOnRoute} from '@deviceinfra/app/features/all_site_search/all_site_search_scope';
import {HeaderJumpBar} from '@deviceinfra/app/features/all_site_search/components/header_jump_bar/header_jump_bar';
import {HeaderSearchDockService} from '@deviceinfra/app/shared/services/header_search_dock_service';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';
import {ReplaySubject} from 'rxjs';
import {filter, takeUntil} from 'rxjs/operators';
function isAssistantEnabled(
  queryParamMap?: unknown,
  appData?: unknown,
): boolean {
  return false;
}

/** Height of the embedded header band until the host page sends its slot. */
const DEFAULT_EMBEDDED_HEADER_BAND_HEIGHT = 64;
/**
 * Class set on <body> while the embedded header band is shown; app.scss then
 * keeps every layer that covers the whole viewport (dialogs, the loading
 * spinner) below the band.
 */
export const EMBEDDED_HEADER_BAND_CLASS = 'with-embedded-header-band';
/** CSS custom property on <body> carrying the band height for that offset. */
export const EMBEDDED_HEADER_BAND_HEIGHT_PROPERTY =
  '--embedded-header-band-height';

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
    HeaderJumpBar,
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
    PortalModule,
  ],
})
export class App implements OnDestroy {
  sideNavExpanded = true;
  private readonly destroy = new ReplaySubject<void>();
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly cdr: ChangeDetectorRef = inject(ChangeDetectorRef);
  private readonly router: Router = inject(Router);
  readonly appData: AppData = inject(APP_DATA);
  readonly currentYear = new Date().getFullYear();
  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');
  readonly stayOnLegacyFe = stayOnLegacyFe;
  readonly omniLabUrl = getOmniLabUrl(this.appData.applicationId ?? '');
  readonly userGuideUrl = getUserGuideUrl();
  readonly faqsUrl = getFaqsUrl();
  readonly loadingService = inject(LoadingService);
  private readonly urlService = inject(UrlService);
  readonly dockService = inject(HeaderSearchDockService);
  private readonly commonParamsService = inject(CommonParamsService);
  showVersionInfo = true;
  isEmbeddedMode = true;
  isFakeData = false;
  showContent = true;
  isAssistantEnabled = false;
  /**
   * Whether the embedded header band is shown: when embedded in the ATS host
   * (MTT UI2) on the entity search pages, the host lets the iframe paint into
   * a slot of its own header, and this band hosts the docked search input
   * there. Never shown in the Google-internal build (its embedded mode, e.g.
   * Arsenal, has no such host slot).
   */
  showEmbeddedHeaderBand = false;
  /** Header slot geometry provided by the host page (embedded mode only). */
  readonly embeddedHeaderSlot = this.urlService.embeddedHeaderSlot;
  /** Height of the embedded header band, following the host header. */
  readonly embeddedHeaderBandHeight = computed(
    () =>
      this.embeddedHeaderSlot()?.height || DEFAULT_EMBEDDED_HEADER_BAND_HEIGHT,
  );
  private readonly document = inject(DOCUMENT);
  private headerSlotRequested = false;

  get isStandaloneMode(): boolean {
    return !this.isEmbeddedMode;
  }

  isAssistantFeatureEnabled(): boolean {
    return this.isAssistantEnabled;
  }

  /**
   * Determines whether the global Header Jump Bar (`<app-header-jump-bar>`) should be shown.
   *
   * Input: None.
   * Output: boolean (`true` on Home, `/search`, and Detail pages; `false` on Entity Search pages).
   * Explanation:
   *   Shown in standalone mode on all routes except the 5 Entity Search pages
   *   (`/devices`, `/hosts`, `/tests`, `/jobs`, `/sessions`) and routes whose
   *   data sets `hideHeaderJumpBar` because the page has its own box for IDs.
   */
  isShowHeaderJumpBar(): boolean {
    return (
      this.isStandaloneMode &&
      isHeaderJumpBarVisibleOnRoute(this.getCurrentRoutePath()) &&
      !this.getCurrentRouteData()['hideHeaderJumpBar']
    );
  }

  isNavActive(
    section:
      | 'home'
      | 'summary'
      | 'devices'
      | 'hosts'
      | 'tests'
      | 'jobs'
      | 'sessions'
      | 'assistant',
  ): boolean {
    const path = this.getCurrentRoutePath();

    switch (section) {
      case 'home':
        return path === 'home' || !path;
      case 'summary':
        return path === 'summary';
      case 'devices':
        return path.startsWith('devices');
      case 'hosts':
        return path.startsWith('hosts');
      case 'tests':
        return path.includes('tests');
      case 'jobs':
        return path.startsWith('jobs') && !path.includes('tests');
      case 'sessions':
        return path.startsWith('sessions');
      case 'assistant':
        return path.startsWith('assistant');
      default:
        return false;
    }
  }

  /**
   * Returns the preserved query parameters from the common params service.
   */
  getPreservedQueryParams(): Record<string, string> {
    const qParams: Record<string, string> = {
      // for thsoe left side nav menus, when clicked on it, we should
      // reset all query parameters except the common ones.
      ...this.commonParamsService.getCommonParams(),
    };
    if (this.isAssistantFeatureEnabled()) {
      qParams['enable_assistant'] = 'true';
    }
    return qParams;
  }

  getCurrentRoutePath(): string {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) {
      route = route.firstChild;
    }
    return route.routeConfig?.path || '';
  }

  /** Returns the static and resolved data of the innermost active route. */
  getCurrentRouteData(): Readonly<Record<string, unknown>> {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) {
      route = route.firstChild;
    }
    return route.data ?? {};
  }

  constructor() {
    this.route.queryParamMap
      .pipe(takeUntil(this.destroy))
      .subscribe((params) => {
        this.isEmbeddedMode = params.get('is_embedded_mode') === 'true';
        this.isFakeData = params.get('fake_data') === 'true';
        this.isAssistantEnabled = isAssistantEnabled(params, this.appData);
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
      const newUrl = new URL(url, window.location.origin);
      this.router.navigateByUrl(newUrl.pathname + newUrl.search);
    });

    // The host header can change its height (and so the band's) at any time.
    effect(() => {
      this.embeddedHeaderBandHeight();
      this.updateEmbeddedOverlayOffset();
    });
  }

  updateShowContent() {
    this.updateEmbeddedHeaderBand();
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

    if (
      !path ||
      path === 'home' ||
      path === 'summary' ||
      path === 'search' ||
      path === 'devices' ||
      path === 'hosts' ||
      path === 'tests' ||
      path === 'jobs' ||
      path === 'sessions' ||
      path === 'assistant'
    ) {
      this.showContent = true;
    } else if (path === 'devices/:id' && params['id']) {
      this.showContent = true;
    } else if (path === 'hosts/:hostName' && params['hostName']) {
      this.showContent = true;
    } else if (
      path === 'jobs/:jobId/tests/:id' &&
      params['id'] &&
      params['jobId']
    ) {
      this.showContent = true;
    } else if (path === 'jobs/:id' && params['id']) {
      this.showContent = true;
    } else if (path === 'sessions/:id' && params['id']) {
      this.showContent = true;
    } else {
      this.showContent = false;
    }
    this.cdr.markForCheck();
  }

  /**
   * Recomputes whether the embedded header band is shown and, the first time
   * it is needed, asks the host page for its header slot geometry.
   *
   * Explanation: The band only exists when embedded in the ATS host (OSS
   * build) on the entity search routes (`/devices`, `/hosts`, ...); detail
   * pages and the Google-internal embedded mode keep the plain embedded
   * layout. The host answers the request with an `EMBEDDED_HEADER_SLOT`
   * message and keeps sending updates when the slot moves or resizes.
   */
  private updateEmbeddedHeaderBand() {
    this.showEmbeddedHeaderBand =
      this.urlService.isInAtsEmbeddedMode() &&
      ENTITY_SEARCH_PATHS.has(this.getCurrentRoutePath());
    if (this.showEmbeddedHeaderBand && !this.headerSlotRequested) {
      this.headerSlotRequested = true;
      this.urlService.requestEmbeddedHeaderSlot();
    }
    this.updateEmbeddedOverlayOffset();
  }

  /**
   * Keeps the layers that cover the whole viewport (dialogs, the loading
   * spinner) below the embedded header band.
   *
   * Explanation: The host page only paints the slot column of the band, so
   * anything centered over the whole iframe (V6's `app-dialog` shell is a
   * fixed full-viewport container, the CDK global overlay wrapper centers
   * mat-dialog panes, the loading spinner overlay is fixed too) would have
   * its top edge land in the band and be cut off (and not clickable) outside
   * the slot. While the band is shown, <body> is marked with the band height
   * and app.scss offsets those layers by it, so they center in the area
   * below the band. Connected overlays (menus, autocomplete panels, tooltips)
   * are anchored to their trigger and need no offset.
   */
  private updateEmbeddedOverlayOffset() {
    const body = this.document.body;
    if (this.showEmbeddedHeaderBand) {
      body.classList.add(EMBEDDED_HEADER_BAND_CLASS);
      body.style.setProperty(
        EMBEDDED_HEADER_BAND_HEIGHT_PROPERTY,
        `${this.embeddedHeaderBandHeight()}px`,
      );
    } else {
      body.classList.remove(EMBEDDED_HEADER_BAND_CLASS);
      body.style.removeProperty(EMBEDDED_HEADER_BAND_HEIGHT_PROPERTY);
    }
  }

  ngOnDestroy() {
    this.showEmbeddedHeaderBand = false;
    this.updateEmbeddedOverlayOffset();
    this.destroy.next();
    this.destroy.complete();
  }
}
