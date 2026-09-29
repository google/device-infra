import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ActivatedRoute, Router} from '@angular/router';
import {BehaviorSubject, combineLatest, EMPTY} from 'rxjs';
import {catchError, distinctUntilChanged, map, switchMap} from 'rxjs/operators';

import {
  AllSiteMatch,
  ResolveAllSiteQueryRequest,
} from '../../core/models/search';
import {CommonParamsService} from '../../core/services/common_params_service';
import {SEARCH_SERVICE} from '../../core/services/search/search_service';
import {UrlService} from '../../core/services/url_service';
import {
  NavigationStrategyRegistry,
  NavLinkConfig,
} from '../../core/utils/navigation';
import {NavLink} from '../../shared/components/nav_link/nav_link';
import {LoadingService} from '../../shared/services/loading_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {getErrorMessage} from '../../shared/utils/error_utils';
import {
  FALLBACK_ENTITY_CHIPS,
  FallbackEntityChip,
  JUMP_SCOPES,
  JumpScopeDefinition,
  JumpScopeKey,
  normalizeJumpScope,
} from './all_site_search_scope';

/**
 * Resolution view state on the dedicated All-Site Search page (`/search`).
 */
export type AllSiteResolutionState =
  | 'loading'
  | 'not_found'
  | 'ambiguous'
  | 'error';

/**
 * View model representing a single disambiguation candidate card on `/search`.
 */
export interface AmbiguousCandidateItem {
  /** Unique key for Angular `@for` tracking. */
  readonly trackKey: string;
  /** Entity type badge text ('Device' | 'Host' | 'Test' | 'Job' | 'Session'). */
  readonly typeBadge: string;
  /** Material Symbols icon name for the entity type. */
  readonly icon: string;
  /** Primary identifier title displayed on Line 1. */
  readonly title: string;
  /** Whether the card renders as a compact single-line entry (true for Test/Job/Session). */
  readonly isSingleLine: boolean;
  /** Kind of candidate for template rendering of Line 2. */
  readonly entityKind: 'device' | 'host' | 'tjs';
  /** Host name for Device candidates (rendered in bold on Line 2). */
  readonly hostName?: string;
  /** Device model label for Device candidates (rendered after Host on Line 2). */
  readonly modelLabel?: string;
  /** Formatted Line 2 text for Host candidates (`"<N> attached devices · IP: <ip>"`). */
  readonly subtitleText?: string;
  /** Target `NavLinkConfig` used by `<a app-nav-link>` to open the entity detail page. */
  readonly navConfig: NavLinkConfig;
}

/**
 * Dedicated All-Site Search page component (`/search`).
 *
 * Resolves an exact identifier query (`q`) with an optional identifier type scope (`scope`)
 * via `SearchService.resolveAllSiteQuery()`.
 * - While resolving: renders the Material 3 Loading ("Resolving...") card.
 * - On 1 match: immediately redirects (`replaceUrl: true`) to the matched Entity Detail page,
 *   stripping `q` and `scope` while preserving Common Query Parameters.
 * - On 0 matches: stays on `/search` and renders the 404 ("No exact match") card with fallback
 *   Assist Chips to the Entity Search pages (`/devices?q=<id>`, etc.).
 * - On >1 matches: stays on `/search` and renders the Ambiguous ("Multiple matches") card
 *   listing candidate cards alongside fallback Assist Chips.
 * - On RPC error: shows an error snackbar and renders the Error card with a single Retry button.
 */
@Component({
  selector: 'app-all-site-search-page',
  standalone: true,
  templateUrl: './all_site_search_page.ng.html',
  styleUrl: './all_site_search_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, NavLink],
})
export class AllSiteSearchPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly searchService = inject(SEARCH_SERVICE);
  private readonly commonParamsService = inject(CommonParamsService);
  private readonly urlService = inject(UrlService);
  private readonly loadingService = inject(LoadingService);
  private readonly snackBar = inject(SnackBarService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retryTrigger$ = new BehaviorSubject<number>(0);

  /** Current trimmed query string `q` from the URL. */
  readonly query = signal<string>('');

  /** Current normalized identifier scope `scope` from the URL (`'any'` by default). */
  readonly scope = signal<JumpScopeKey>('any');

  /** Current resolution state (`'loading'`, `'not_found'`, `'ambiguous'`, or `'error'`). */
  readonly viewState = signal<AllSiteResolutionState>('loading');

  /** Error message when `viewState() === 'error'`. */
  readonly errorMessage = signal<string>('');

  /** Whether the Error state card should show the Retry button. */
  readonly canRetry = signal<boolean>(true);

  /** Candidate items when `viewState() === 'ambiguous'`. */
  readonly candidates = signal<readonly AmbiguousCandidateItem[]>([]);

  /** Ordered list of 5 entity search fallback chips. */
  readonly fallbackChips: readonly FallbackEntityChip[] = FALLBACK_ENTITY_CHIPS;

  /** Metadata definition for the active `scope()`. */
  readonly currentScope = computed<JumpScopeDefinition>(
    () => JUMP_SCOPES[this.scope()] || JUMP_SCOPES.any,
  );

  /** True when the active scope is narrowed to a specific entity type (not `'any'`). */
  readonly isScopeSpecific = computed<boolean>(() => this.scope() !== 'any');

  /** Heading text shown in the Loading ("Resolving...") state card. */
  readonly loadingTitle = computed<string>(() => {
    const q = this.query();
    const scopeDef = this.currentScope();
    if (scopeDef.idType) {
      return `Resolving ${scopeDef.label.toLowerCase()} "${q}"...`;
    }
    return `Resolving "${q}"...`;
  });

  /** Heading text shown in the 404 ("No exact match") state card. */
  readonly notFoundTitle = computed<string>(() => {
    const q = this.query();
    if (!q) {
      return 'No exact match found';
    }
    const scopeDef = this.currentScope();
    if (scopeDef.idType) {
      return `No exact match for ${scopeDef.label.toLowerCase()} "${q}"`;
    }
    return `No exact match for "${q}"`;
  });

  /** Description text shown in the 404 ("No exact match") state card. */
  readonly notFoundDesc = computed<string>(() => {
    const q = this.query();
    if (!q) {
      return 'No device ID, host name, test ID, job ID, or session ID matched this identifier.';
    }
    const scopeDef = this.currentScope();
    if (scopeDef.idType) {
      return `No ${scopeDef.label.toLowerCase()} matched "${q}".`;
    }
    return `No device ID, host name, test ID, job ID, or session ID matched "${q}".`;
  });

  /** Heading text shown in the Error state card. */
  readonly errorTitle = computed<string>(() => {
    const q = this.query();
    if (!q) {
      return 'Failed to resolve query';
    }
    const scopeDef = this.currentScope();
    if (scopeDef.idType) {
      return `Failed to resolve ${scopeDef.label.toLowerCase()} "${q}"`;
    }
    return `Failed to resolve "${q}"`;
  });

  /** Heading text shown in the Ambiguous ("Multiple matches") state card. */
  readonly ambiguousTitle = computed<string>(() => {
    const q = this.query();
    const scopeDef = this.currentScope();
    if (scopeDef.key === 'device') {
      return `Multiple hosts have device ID "${q}"`;
    }
    return `Multiple matches for "${q}"`;
  });

  /** Subtitle description text shown in the Ambiguous ("Multiple matches") state card. */
  readonly ambiguousDesc = computed<string>(() => {
    const scopeDef = this.currentScope();
    if (scopeDef.key === 'device') {
      return `This device ID is attached to ${this.candidates().length} hosts. Select the host you want to view:`;
    }
    return 'More than one entity matched this identifier. Select the one you want to view:';
  });

  /** Label preceding the fallback chips row. */
  readonly fallbackLabel = computed<string>(() => {
    if (this.isScopeSpecific()) {
      return 'Or:';
    }
    const q = this.query();
    return q ? `Search "${q}" in:` : 'Search in:';
  });

  /** Custom query parameters (`{q: query}`) passed to fallback entity search links. */
  readonly fallbackQueryParams = computed<Record<string, string>>(() => {
    const q = this.query();
    const params: Record<string, string> = {};
    if (q) {
      params['q'] = q;
    }
    return params;
  });

  /** Target `NavLinkConfig` for the single-entity fallback chip when `isScopeSpecific()` is true. */
  readonly scopedFallbackNavConfig = computed<NavLinkConfig>(() => {
    const pageType = this.currentScope().searchPageType || 'device_search';
    return {type: pageType};
  });

  ngOnInit() {
    this.loadingService.hide();
    const routeQuery$ = this.route.queryParamMap.pipe(
      map((params) => ({
        q: (params.get('q') || '').trim(),
        scope: normalizeJumpScope(params.get('scope')),
      })),
      distinctUntilChanged(
        (prev, curr) => prev.q === curr.q && prev.scope === curr.scope,
      ),
    );

    combineLatest([routeQuery$, this.retryTrigger$])
      .pipe(
        switchMap(([{q, scope}]) => {
          this.query.set(q);
          this.scope.set(scope);
          this.errorMessage.set('');
          this.canRetry.set(true);

          if (!q) {
            this.candidates.set([]);
            this.viewState.set('not_found');
            return EMPTY;
          }

          this.viewState.set('loading');
          this.candidates.set([]);

          const scopeDef = JUMP_SCOPES[scope] || JUMP_SCOPES.any;
          const request: ResolveAllSiteQueryRequest = scopeDef.idType
            ? {query: q, idType: scopeDef.idType}
            : {query: q, anyId: {}};

          return this.searchService.resolveAllSiteQuery(request).pipe(
            catchError((err: unknown) => {
              const msg = getErrorMessage(err);
              this.candidates.set([]);
              this.canRetry.set(true);
              this.errorMessage.set(msg);
              this.viewState.set('error');
              this.snackBar.showError(msg);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((response) => {
        const matches = response.matches || [];
        if (matches.length === 0) {
          this.candidates.set([]);
          this.viewState.set('not_found');
          return;
        }

        const candidateItems: AmbiguousCandidateItem[] = [];
        for (let idx = 0; idx < matches.length; idx++) {
          const item = this.toCandidateItem(matches[idx], idx);
          if (!item) {
            const msg =
              'Unsupported or malformed entity match returned by server.';
            this.candidates.set([]);
            this.canRetry.set(false);
            this.errorMessage.set(msg);
            this.viewState.set('error');
            this.snackBar.showError(msg);
            return;
          }
          candidateItems.push(item);
        }

        if (candidateItems.length === 1) {
          this.redirectToSingleMatch(candidateItems[0]);
        } else {
          this.candidates.set(candidateItems);
          this.viewState.set('ambiguous');
        }
      });
  }

  /**
   * Re-runs the All-Site Search resolution for the current query and scope.
   */
  retry() {
    this.retryTrigger$.next(this.retryTrigger$.value + 1);
  }

  /**
   * Switches the scope from a specific identifier type back to `'any'` and re-runs resolution.
   *
   * Input: None.
   * Output: None.
   * Explanation:
   *   Navigates to `/search?q=<query>` (dropping `scope`) while preserving Common Query Parameters.
   */
  tryAcrossAnyId() {
    const strategy = NavigationStrategyRegistry.getRequired('all_site_search');
    const q = this.query();
    const inputParams: Record<string, unknown> = q ? {'q': q} : {};
    const queryParams = strategy.toCsnQueryParams(
      inputParams,
      this.commonParamsService.getCommonParams(),
    );
    this.router.navigate([strategy.buildRoutePath(inputParams)], {
      queryParams,
    });
  }

  /**
   * Redirects (`replaceUrl: true`) to the Entity Detail page for a unique 1-Hit match.
   *
   * Input:
   *   - candidate: The resolved `AmbiguousCandidateItem` for the single match.
   * Output: None.
   * Explanation:
   *   Uses `NavigationStrategyRegistry` for the matched entity type so that `q` and `scope`
   *   are stripped and only Common Query Parameters + Detail Page parameters (`host_name`,
   *   `universe`) are kept. Also notifies the parent frame when running in embedded mode.
   */
  private redirectToSingleMatch(candidate: AmbiguousCandidateItem) {
    const cfg = candidate.navConfig;
    const strategy = NavigationStrategyRegistry.getRequired(cfg.type);
    const routePath = strategy.buildRoutePath(cfg);
    const inputParams = cfg as unknown as Record<string, unknown>;
    const commonParams = this.commonParamsService.getCommonParams();
    const queryParams = strategy.toCsnQueryParams(inputParams, commonParams);

    if (
      strategy.supportsExternalNavigation &&
      this.commonParamsService.isEmbeddedMode()
    ) {
      const {page, params} = strategy.toExternalPayload(
        inputParams,
        commonParams,
      );
      this.urlService.notifyNavigated(
        page as 'host_details' | 'device_details',
        params,
      );
    }

    this.router.navigate([routePath], {
      ...(Object.keys(queryParams).length > 0 ? {queryParams} : {}),
      replaceUrl: true,
    });
  }

  /**
   * Converts a backend `AllSiteMatch` into an `AmbiguousCandidateItem` view model.
   *
   * Input:
   *   - match: `AllSiteMatch` from `ResolveAllSiteQueryResponse`.
   *   - index: Fallback index for tracking.
   * Output:
   *   - `AmbiguousCandidateItem` with display metadata and `NavLinkConfig`, or `null` if the
   *     match does not contain a supported entity payload.
   * Explanation:
   *   Maps `device`, `host`, `test`, `job`, and `session` variants to their corresponding
   *   Material 3 badge, icon, two-line or single-line layout, and `NavLinkConfig`.
   */
  private toCandidateItem(
    match: AllSiteMatch,
    index: number,
  ): AmbiguousCandidateItem | null {
    if (match.device) {
      const dev = match.device;
      const modelLabel = (dev.model || '').trim();
      return {
        trackKey: `device-${dev.deviceId}-${dev.hostName}-${index}`,
        typeBadge: 'Device',
        icon: 'devices',
        title: dev.deviceId,
        isSingleLine: !dev.hostName && !modelLabel,
        entityKind: 'device',
        hostName: dev.hostName,
        modelLabel: modelLabel || undefined,
        navConfig: {
          'type': 'device',
          'deviceId': dev.deviceId,
          'hostName': dev.hostName,
          'universe': dev.universe,
        },
      };
    }

    if (match.host) {
      const host = match.host;
      const parts: string[] = [];
      if (host.attachedDeviceCount !== undefined) {
        const noun = host.attachedDeviceCount === 1 ? 'device' : 'devices';
        parts.push(`${host.attachedDeviceCount} attached ${noun}`);
      }
      if (host.hostIp) {
        parts.push(`IP: ${host.hostIp}`);
      }
      const subtitleText = parts.join(' · ');
      return {
        trackKey: `host-${host.hostName}-${index}`,
        typeBadge: 'Host',
        icon: 'dns',
        title: host.hostName,
        isSingleLine: !subtitleText,
        entityKind: 'host',
        subtitleText: subtitleText || undefined,
        navConfig: {
          'type': 'host',
          'hostName': host.hostName,
          'hostIp': host.hostIp,
          'universe': host.universe,
        },
      };
    }

    if (match.test) {
      const test = match.test;
      return {
        trackKey: `test-${test.jobId}-${test.testId}-${index}`,
        typeBadge: 'Test',
        icon: 'fact_check',
        title: test.testId,
        isSingleLine: true,
        entityKind: 'tjs',
        navConfig: {
          'type': 'test',
          'testId': test.testId,
          'jobId': test.jobId,
        },
      };
    }

    if (match.job) {
      const job = match.job;
      return {
        trackKey: `job-${job.jobId}-${index}`,
        typeBadge: 'Job',
        icon: 'task',
        title: job.jobId,
        isSingleLine: true,
        entityKind: 'tjs',
        navConfig: {
          'type': 'job',
          'jobId': job.jobId,
        },
      };
    }

    if (match.session?.sessionId) {
      const sessionId = match.session.sessionId;
      return {
        trackKey: `session-${sessionId}-${index}`,
        typeBadge: 'Session',
        icon: 'view_timeline',
        title: sessionId,
        isSingleLine: true,
        entityKind: 'tjs',
        navConfig: {
          'type': 'session',
          'sessionId': sessionId,
        },
      };
    }

    return null;
  }
}
