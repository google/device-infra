import {DOCUMENT} from '@angular/common';
import {
  Component,
  inject,
  input,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import {Router} from '@angular/router';
import {ReplaySubject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

import {CommonParamsService} from '@deviceinfra/app/core/services/common_params_service';
import {UrlService} from '@deviceinfra/app/core/services/url_service';
import {
  ExternalNavPayload,
  NavigationStrategyRegistry,
  NavLinkConfig,
} from '@deviceinfra/app/core/utils/navigation';
import {buildQueryString} from '@deviceinfra/app/core/utils/url_utils';

export type {NavLinkConfig};

/**
 * A customized link component to centralize navigation behavior.
 * Uses OOP Strategy Pattern via `NavigationStrategyRegistry` to resolve
 * For detailed design doc, including the behavior matrix and resolution logic,
 * please see java/com/google/devtools/mobileharness/fe/v6/knowledge/link_in_iframe_behavior.md
 * We have 3 parts of parameter to merge for the final link:
 *   1. query params in current browser window. Could contain `host_name`, `universe`, etc.
 *     Note: during the merge process, `host_name` will be cleared.
 *   2. custom query params in NavLinkConfig. Any additional parameter we want to pass to the link.
 * route paths, destination-specific query parameters, and external payloads.
 */
@Component({
  selector: 'a[app-nav-link]',
  template: '<ng-content></ng-content>',
  standalone: true,
  host: {
    '[attr.href]': 'fullPageLink()',
    '(click)': 'handleClick($event)',
  },
})
export class NavLink implements OnInit, OnDestroy {
  /** Configuration for the target link (host, device, job, test, session). */
  readonly config = input.required<NavLinkConfig>();

  /** Standard target attribute for the 'a' element (e.g., '_blank'). */
  readonly target = input<string>();

  /** How to handle current URL query parameters upon navigation. */
  readonly queryParamsHandling = input<'merge' | 'preserve' | ''>('');

  /** Custom query parameters to append to the navigation. */
  readonly customQueryParams = input<Record<string, string>>({});

  private readonly router = inject(Router);
  private readonly urlService = inject(UrlService);
  private readonly commonParamsService = inject(CommonParamsService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyed = new ReplaySubject<void>(1);

  /**
   * Local route for client-side navigation within the V6 application.
   * Dynamically resolved from the target strategy.
   * Note: routerLink does NOT include query params.
   */
  get routerLink(): string {
    const cfg = this.config();
    const strategy = NavigationStrategyRegistry.getRequired(cfg.type);
    return strategy.buildRoutePath(cfg);
  }

  /**
   * Take effect when render link in a new browser tab.
   * External URL for server-side navigation (e.g., when running embedded in
   * Arsenal). This is bound to the 'href' attribute to allow native behavior
   * (right-click, ctrl-click) to work correctly.
   * User scenario:
   *   - when running in standalone mode, fullPageLink is the same as routerLink.
   *   - when running in embedded mode, fullPageLink is the external URL(the
   * parent window URL). Say in MTT, I clicked a device detail link in a host
   * detail page with ctrl key pressed, then it will open a new tab and visit
   * the MTT URL of the device detail page, which will embed the V6 device
   * detail page in it.
   *
   */
  readonly fullPageLink = signal<string>('');

  ngOnInit() {
    const navQueryParams = this.getCsnQueryParams();
    const newSearch = buildQueryString(navQueryParams);

    // Default fullPageLink to the internal route path with clean query params.
    this.fullPageLink.set(`${this.routerLink}${newSearch}`);
    console.log('init fullPageLink: ', this.fullPageLink());

    const cfg = this.config();
    const strategy = NavigationStrategyRegistry.getRequired(cfg.type);
    if (
      strategy.supportsExternalNavigation &&
      this.commonParamsService.isEmbeddedMode()
    ) {
      this.fetchFullPageLink();
    }
  }

  ngOnDestroy() {
    this.destroyed.next();
    this.destroyed.complete();
  }

  private getCurrentUrlQueryParams(): Record<string, string> {
    const search = this.document.location?.search || '';
    const params = new URLSearchParams(search);
    const result: Record<string, string> = {};
    params.forEach((value, key) => {
      result[key] = value;
    });
    return result;
  }

  private getInputParameters(): Record<string, unknown> {
    return {
      ...(this.queryParamsHandling() ? this.getCurrentUrlQueryParams() : {}),
      ...(this.config() as unknown as Record<string, unknown>),
    };
  }

  /**
   * Gets the authoritative query parameters for the destination page during
   * client-side navigation (CSN).
   * Resolves common parameters and destination-allowed parameters via the strategy.
   */
  private getCsnQueryParams(): Record<string, string> {
    const cfg = this.config();
    const strategy = NavigationStrategyRegistry.getRequired(cfg.type);
    return strategy.toCsnQueryParams(
      this.getInputParameters(),
      this.commonParamsService.getCommonParams(),
      this.customQueryParams(),
    );
  }

  /**
   * Prepares the payload for external parent window integration (UrlService).
   */
  private toExternalPayload(): ExternalNavPayload {
    const cfg = this.config();
    const strategy = NavigationStrategyRegistry.getRequired(cfg.type);
    return strategy.toExternalPayload(
      this.getInputParameters(),
      this.commonParamsService.getCommonParams(),
      this.customQueryParams(),
    );
  }

  /**
   * Fetches the full page link from the parent window when running in embedded mode.
   */
  private fetchFullPageLink() {
    if (!this.commonParamsService.isEmbeddedMode()) {
      return;
    }

    const {page, params} = this.toExternalPayload();

    this.urlService
      .getExternalUrl(page, params)
      .pipe(takeUntil(this.destroyed))
      .subscribe({
        next: (url: string) => {
          this.fullPageLink.set(url);
          console.log('receivedfullPageLink', this.fullPageLink());
        },
        error: () => {
          console.warn(
            'failed to fetch fullPageLink, fallback to routerLink',
            this.routerLink,
          );
          // Fallback to local URL (already set in ngOnInit).
        },
      });
  }

  /**
   * Handles click events to coordinate between Client-Side Navigation (CSN)
   * and Server-Side Navigation (SSN).
   *
   * Input: event (MouseEvent) triggered by clicking the link.
   * Output: None.
   * Explanation: Determines whether to use native browser navigation (SSN, e.g. for
   * Ctrl+Click or target="_blank") or Angular Router navigation (CSN). In CSN mode,
   * it also notifies the parent window of navigation events via `UrlService`.
   */
  handleClick(event: MouseEvent) {
    // If it's a special click (Ctrl/Cmd/Middle) or the caller explicitly requested a
    // new tab via target="_blank", we allow the default browser behavior.
    // The browser will follow the [href] (which is fullPageLink), resulting in SSN.
    if (
      event.ctrlKey ||
      event.metaKey ||
      event.button === 1 ||
      this.target() === '_blank'
    ) {
      // in this case, the fullPageLink will be used by the browser.
      console.log(
        'handleClick executed, returning early to keep the default behavior',
      );
      // REQUIREMENT: always open a new tab to render the `the fullPage reload link`.
      // Let native behavior happen for Ctrl+Click, Cmd+Click (Mac), and Middle-Click,
      // as well as when the caller specifies target="_blank".
      // The browser will follow the [href], which is bound to fullPageLink (SSN).
      return;
    }

    event.preventDefault();
    // in this case, the routerLink will be used by the router.
    console.log(
      'handleClick executed, client-side navigating to',
      this.routerLink,
    );
    // since the routerLink itself does not contain query params, we will use
    // the router.navigate() method to navigate to the routerLink with query
    // params.

    const cfg = this.config();
    const strategy = NavigationStrategyRegistry.getRequired(cfg.type);

    if (strategy.supportsExternalNavigation) {
      const {page, params} = this.toExternalPayload();
      this.urlService.notifyNavigated(
        page as 'host_details' | 'device_details',
        params,
      );
    }

    const queryParams = this.getCsnQueryParams();

    this.router.navigate([this.routerLink], {
      ...(Object.keys(queryParams).length > 0 ? {queryParams} : {}),
    });
  }
}
