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

import {UrlService} from '@deviceinfra/app/core/services/url_service';
/**
 * Configuration for NavLink component.
 * Enforces that deviceId is required for 'device' type and forbidden for 'host' type.
 */
export type NavLinkConfig =
  | {type: 'host'; hostName: string; hostIp: string; universe?: string}
  | {
      type: 'device';
      hostName: string;
      hostIp: string;
      deviceId: string;
      universe?: string;
    }
  // we don't have universe for job/test/session, but since TJS only exists
  // for google_1p, so we can know that the universe is google_1p. And when
  // navigating back from TJS to device/host detail page, we can still know
  // the universe is google_1p, as only google_1p has TJS.
  | {type: 'job'; jobId: string}
  | {type: 'test'; jobId: string; testId: string}
  | {type: 'session'; sessionId: string};

/**
 * A customized link component to centralize navigation behavior.
 * For detailed design doc, including the behavior matrix and resolution logic,
 * please see java/com/google/devtools/mobileharness/fe/v6/knowledge/link_in_iframe_behavior.md
 * We have 3 parts of parameter to merge for the final link:
 *   1. query params in current browser window. Could contain `host_name`, `universe`, etc.
 *     Note: during the merge process, `host_name` will be cleared.
 *   2. custom query params in NavLinkConfig. Any additional parameter we want to pass to the link.
 *     Note: this part is of higher priority than the first part.
 *   3. NavLinkConfig could contain the hostName in host/device detail page.
 *     Note: the hostName will be kept in the final URL if the link is for device detail page.
 *
 * Rules are as follows:
 *
 *   - if the link is for device detail page, we will always keep the `host_name` in the URL.
 *   - for other pages, we will always remove the `host_name`.
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
  /** Configuration for the target link (host or device). */
  readonly config = input.required<NavLinkConfig>();

  /** Standard target attribute for the 'a' element (e.g., '_blank'). */
  readonly target = input<string>();

  /** Custom query parameters to append to the navigation. */
  readonly customQueryParams = input<Record<string, string>>({});

  /**
   * Standard Angular router option for handling query parameters during
   * client-side navigation.
   */
  readonly queryParamsHandling = input<'merge' | 'preserve' | ''>('');

  private readonly router = inject(Router);
  private readonly urlService = inject(UrlService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyed = new ReplaySubject<void>(1);

  /**
   *  Local route for client-side navigation within the V6 application.
   *  take effect when render link in current page.
   * Note: routerLink does NOT include query params.
   */
  routerLink = '';

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
  fullPageLink = signal<string>('');

  /**
   * Angular lifecycle hook called after data-bound properties are initialized.
   *
   * Input: None.
   * Output: None.
   * Explanation: Initializes `routerLink` and `fullPageLink`. It merges custom
   * query parameters and system params (like host_name, universe) with current
   * browser parameters. Also triggers `fetchFullPageLink` if running in embedded
   * mode for host or device pages.
   */
  ngOnInit() {
    this.routerLink = this.getRouterLink();
    // query params in current browser window.
    const search = this.document.defaultView?.location.search || '';

    const urlParams = new URLSearchParams(search);
    // Merge unified query parameters with existing query parameters in current
    // browser window.
    const navQueryParams = this.getNavQueryParams();
    for (const [key, value] of Object.entries(navQueryParams)) {
      if (value === null) {
        urlParams.delete(key);
      } else {
        urlParams.set(key, value);
      }
    }

    const cfg = this.config();

    const newSearch = urlParams.toString();
    // Set the fullPageLink to the routerLink by default, and the `fetchFullPageLink` will update it to the
    // full page link from the parent window if available.
    this.fullPageLink.set(
      `${this.routerLink}${newSearch ? '?' + newSearch : ''}`,
    );
    console.log('init fullPageLink: ', this.fullPageLink());

    // only host and device detail pages will run in embedded mode, so
    // only fetch the full page link for host and device detail pages.
    if (cfg.type === 'host' || cfg.type === 'device') {
      this.fetchFullPageLink();
    }
  }

  ngOnDestroy() {
    this.destroyed.next();
    this.destroyed.complete();
  }

  /**
   * Returns the router link in V6.
   *
   * Input: None (uses component's `config` input signal).
   * Output: string representing the internal route path (e.g., `/hosts/my-host`).
   * Explanation: Determines the correct internal URL path based on the type of
   * navigation target specified in the `config` input.
   */
  private getRouterLink(): string {
    const cfg = this.config();
    if (cfg.type === 'host') {
      return `/hosts/${cfg.hostName}`;
    } else if (cfg.type === 'device') {
      return `/devices/${cfg.deviceId}`;
    } else if (cfg.type === 'job') {
      return `/jobs/${cfg.jobId}`;
    } else if (cfg.type === 'session') {
      return `/sessions/${cfg.sessionId}`;
    } else {
      // for test
      return `/jobs/${cfg.jobId}/tests/${cfg.testId}`;
    }
  }

  /**
   * Gets the query parameters to be merged with current browser URL parameters.
   *
   * Input: None (uses component inputs).
   * Output: Record<string, string | null> mapping parameter keys to values or null (to delete).
   * Explanation: Collects custom query params and injects/removes system params
   * like `universe` and `host_name` based on page type. Specifically, `host_name`
   * is kept only for device pages and removed for others.
   */
  private getNavQueryParams(): Record<string, string | null> {
    const cfg = this.config();
    const queryParams: Record<string, string | null> = {
      ...this.customQueryParams(),
    };

    if ((cfg.type === 'host' || cfg.type === 'device') && cfg.universe) {
      queryParams['universe'] = cfg.universe;
    }

    if (cfg.type === 'device') {
      queryParams['host_name'] = cfg.hostName;
    } else {
      queryParams['host_name'] = null;
    }

    return queryParams;
  }

  /**
   * Fetches the full page link from the parent window.
   * Only work in embedded mode with host or device type
   *
   * Input: None.
   * Output: None (updates `fullPageLink` signal).
   * Explanation: Requests the external URL from `UrlService` based on current
   * navigation parameters. This URL includes parent window context (like MTT)
   * if running in embedded mode.
   */
  private fetchFullPageLink() {
    // no need to fetch if running in standalone mode, in which case the
    // fullPageLink is already set to the routerLink, and there is no parent
    // window.
    if (this.urlService.isStandalone()) {
      return;
    }

    const {page, params} = this.getNavParams();
    const cfg = this.config();
    // Use device_uuid for external URL calculation to maintain compatibility.
    if (cfg.type === 'device') {
      params['device_uuid'] = cfg.deviceId;
    }

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

    // REQUIREMENT: always render in current tab using Client-Side Navigation (CSN).
    //- if NOT running in embedded mode, always choose routerLink using CSN.
    //- if running in embedded mode, choose routerLink too, using CSN.
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

    // Only notify parent window for host and device pages, as currently
    // no parent page support other types of pages.
    if (cfg.type === 'host' || cfg.type === 'device') {
      const {page, params} = this.getNavParams();
      // Use uuid for navigation notification to match Arsenal's expectation.
      if (cfg.type === 'device') {
        params['uuid'] = cfg.deviceId;
      }
      this.urlService.notifyNavigated(
        page as 'host_details' | 'device_details',
        params,
      );
    }

    const queryParams = this.getNavQueryParams();

    this.router.navigate([this.routerLink], {
      ...(Object.keys(queryParams).length > 0 ? {queryParams} : {}),
      queryParamsHandling: this.queryParamsHandling(),
    });
  }

  /**
   * Translates NavLinkConfig into generic navigation parameters for UrlService.
   *
   * Input: None (uses `config` input).
   * Output: Object containing `page` identifier and `params` dictionary.
   * Explanation: Maps component configuration to the format expected by `UrlService`
   * for calculating external URLs or notifying navigation events.
   */
  private getNavParams(): {
    page:
      | 'host_details'
      | 'device_details'
      | 'job_details'
      | 'test_details'
      | 'session_details';
    params: Record<string, string>;
  } {
    const cfg = this.config();
    if (cfg.type === 'job') {
      return {
        page: 'job_details',
        params: {
          'job_id': cfg.jobId,
          ...this.customQueryParams(),
        },
      };
    } else if (cfg.type === 'test') {
      return {
        page: 'test_details',
        params: {
          'job_id': cfg.jobId,
          'test_id': cfg.testId,
          ...this.customQueryParams(),
        },
      };
    } else if (cfg.type === 'session') {
      return {
        page: 'session_details',
        params: {
          'session_id': cfg.sessionId,
          ...this.customQueryParams(),
        },
      };
    }

    const page = cfg.type === 'host' ? 'host_details' : 'device_details';
    const params: Record<string, string> = {
      'host_name': cfg.hostName,
      'host_ip': cfg.hostIp,
      ...this.customQueryParams(),
    };
    if (cfg.universe) {
      params['universe'] = cfg.universe;
    }
    return {page, params};
  }
}
