import {DOCUMENT} from '@angular/common';
import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {ReplaySubject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

import {UrlService} from 'app/core/services/url_service';
/**
 * Configuration for NavLink component.
 * Enforces that deviceId is required for 'device' type and forbidden for 'host' type.
 */
export type NavLinkConfig =
  | {type: 'host'; hostName: string; hostIp: string; deviceId?: never}
  | {type: 'device'; hostName: string; hostIp: string; deviceId: string};

/**
 * A customized link component to centralize navigation behavior.
 * For detailed design doc, including the behavior matrix and resolution logic,
 * please see java/com/google/devtools/mobileharness/fe/v6/knowledge/link_in_iframe_behavior.md
 */
@Component({
  selector: 'a[app-nav-link]',
  template: '<ng-content></ng-content>',
  standalone: true,
  host: {
    '[attr.href]': 'fullPageLink',
    '(click)': 'handleClick($event)',
  },
})
export class NavLink implements OnInit, OnDestroy {
  /** Configuration for the target link (host or device). */
  @Input({required: true}) config!: NavLinkConfig;

  /** Standard target attribute for the 'a' element (e.g., '_blank'). */
  @Input() target?: string;

  /**
   * Standard Angular router option for handling query parameters during
   * client-side navigation.
   */
  @Input() queryParamsHandling: 'merge' | 'preserve' | '' = '';

  private readonly router = inject(Router);
  private readonly urlService = inject(UrlService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyed = new ReplaySubject<void>(1);

  /** Local route for client-side navigation within the V6 application. */
  routerLink = '';

  /**
   * External URL for server-side navigation (e.g., when running embedded in
   * Arsenal). This is bound to the 'href' attribute to allow native behavior
   * (right-click, ctrl-click) to work correctly.
   */
  fullPageLink = '';

  ngOnInit() {
    this.routerLink = this.getRouterLink();
    const search = this.document.defaultView?.location.search || '';
    // Set the fullPageLink to the routerLink by default, and update it to the
    // full page link from the parent window if available.
    this.fullPageLink = `${this.routerLink}${search}`;
    this.fetchFullPageLink();
  }

  ngOnDestroy() {
    this.destroyed.next();
    this.destroyed.complete();
  }

  private getRouterLink(): string {
    if (this.config.type === 'host') {
      return `/hosts/${this.config.hostName}`;
    } else {
      return `/devices/${this.config.deviceId}`;
    }
  }

  private fetchFullPageLink() {
    if (!this.urlService.isInEmbeddedMode()) {
      return;
    }

    const {page, params} = this.getNavParams();
    // Use device_uuid for external URL calculation to maintain compatibility.
    if (this.config.type === 'device') {
      params['device_uuid'] = this.config.deviceId;
    }

    this.urlService
      .getExternalUrl(page, params)
      .pipe(takeUntil(this.destroyed))
      .subscribe({
        next: (url: string) => {
          this.fullPageLink = url;
          console.log('fullPageLink', this.fullPageLink);
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
   */
  handleClick(event: MouseEvent) {
    // If it's a special click (Ctrl/Cmd/Middle) or the caller explicitly requested a
    // new tab via target="_blank", we allow the default browser behavior.
    // The browser will follow the [href] (which is fullPageLink), resulting in SSN.
    if (
      event.ctrlKey ||
      event.metaKey ||
      event.button === 1 ||
      this.target === '_blank'
    ) {
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
    console.log(
      'handleClick executed, client-side navigating to',
      this.routerLink,
    );

    const {page, params} = this.getNavParams();
    // Use uuid for navigation notification to match Arsenal's expectation.
    if (this.config.type === 'device') {
      params['uuid'] = this.config.deviceId;
    }
    // We notify immediately to speed up the synchronization with parent window.
    this.urlService.notifyNavigated(page, params);

    this.router.navigate([this.routerLink], {
      queryParamsHandling: this.queryParamsHandling,
    });
  }

  private getNavParams(): {
    page: 'host_details' | 'device_details';
    params: Record<string, string>;
  } {
    const page =
      this.config.type === 'host' ? 'host_details' : 'device_details';
    const params: Record<string, string> = {
      'host_name': this.config.hostName,
      'host_ip': this.config.hostIp,
    };
    return {page, params};
  }
}
