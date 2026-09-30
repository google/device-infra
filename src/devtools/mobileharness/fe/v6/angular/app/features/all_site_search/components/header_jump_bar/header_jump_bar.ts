import {CommonModule, DOCUMENT} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {NavigationEnd, Router} from '@angular/router';
import {filter} from 'rxjs/operators';

import {CommonParamsService} from '../../../../core/services/common_params_service';
import {NavigationStrategyRegistry} from '../../../../core/utils/navigation';
import {
  JUMP_SCOPE_LIST,
  JUMP_SCOPES,
  JumpScopeDefinition,
  JumpScopeKey,
  normalizeJumpScope,
} from '../../all_site_search_scope';

/**
 * Global Header Jump Bar component (`<app-header-jump-bar>`).
 *
 * Displayed in the top application toolbar on the Home page (`/`), the dedicated
 * All-Site Search page (`/search`), and Entity Detail pages. Allows users to
 * resolve an exact identifier across Devices, Hosts, Tests, Jobs, and Sessions,
 * or narrow resolution to a single identifier type via the leading scope selector.
 */
@Component({
  selector: 'app-header-jump-bar',
  standalone: true,
  templateUrl: './header_jump_bar.ng.html',
  styleUrl: './header_jump_bar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'onDocumentEscape()',
  },
})
export class HeaderJumpBar implements OnInit {
  private readonly router = inject(Router);
  private readonly commonParamsService = inject(CommonParamsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly hostElement = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);

  /** Reference to the native `<input>` element inside the Jump Bar. */
  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  /** Ordered list of identifier scope options rendered in the dropdown menu. */
  readonly scopeOptions: readonly JumpScopeDefinition[] = JUMP_SCOPE_LIST;

  /** Currently selected identifier scope ('any' by default). */
  readonly scope = signal<JumpScopeKey>('any');

  /** Current text entered in the Jump Bar input. */
  readonly queryText = signal<string>('');

  /** Whether the identifier scope dropdown menu is currently open. */
  readonly isMenuOpen = signal<boolean>(false);

  /** Metadata definition for the currently active `scope`. */
  readonly currentScope = computed<JumpScopeDefinition>(
    () => JUMP_SCOPES[this.scope()] || JUMP_SCOPES.any,
  );

  /** True when the active scope is narrowed to a specific entity type (not `'any'`). */
  readonly isScopeSpecific = computed<boolean>(() => this.scope() !== 'any');

  /** True when the input contains non-whitespace text. */
  readonly hasValue = computed<boolean>(
    () => this.queryText().trim().length > 0,
  );

  ngOnInit() {
    this.syncFromCurrentUrl();

    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.syncFromCurrentUrl();
      });
  }

  /**
   * Synchronizes the Jump Bar input text and scope chip when on the `/search` route.
   *
   * Input: None.
   * Output: None.
   * Explanation:
   *   Parses the current router URL (or window location search) when on `/search` so
   *   that refreshing `/search?q=...&scope=...` or clicking "Try across Any ID" keeps
   *   the Header Jump Bar synchronized with the URL state.
   */
  private syncFromCurrentUrl() {
    if (this.isCurrentlyOnSearchRoute()) {
      const qp = this.router.parseUrl(this.router.url).queryParams;
      this.applyUrlSearchParams(qp['q'], qp['scope'], true);
      return;
    }

    // Fallback check against window location search on initial cold load before router settles
    const pathname = this.document.location?.pathname || '';
    if (pathname.endsWith('/search')) {
      const searchParams = new URLSearchParams(
        this.document.location?.search || '',
      );
      this.applyUrlSearchParams(
        searchParams.get('q'),
        searchParams.get('scope'),
        false,
      );
    }
  }

  /**
   * Applies parsed `q` and `scope` query parameters to the Jump Bar signals.
   *
   * Input:
   *   - rawQ: Raw `q` parameter string from router or window location.
   *   - rawScope: Raw `scope` parameter string from router or window location.
   *   - alwaysSetQuery: When true, updates `queryText` even if `urlQ` is empty.
   * Output: None.
   * Explanation:
   *   Shared DRY helper used by both router URL synchronization and initial window location fallback.
   */
  private applyUrlSearchParams(
    rawQ: string | null | undefined,
    rawScope: string | null | undefined,
    alwaysSetQuery: boolean,
  ) {
    const urlQ = (rawQ ?? '').trim();
    if (alwaysSetQuery || urlQ) {
      this.queryText.set(urlQ);
    }
    this.scope.set(normalizeJumpScope(rawScope));
  }

  /**
   * Focuses the text input when clicking directly on the outer search bar background.
   *
   * Input:
   *   - event: MouseEvent from clicking `.header-search-bar`.
   * Output: None.
   * Explanation:
   *   Matches prototype behavior where clicking the pill background focuses the input.
   */
  onBarClick(event: MouseEvent) {
    if (event.target === event.currentTarget) {
      this.searchInput()?.nativeElement.focus();
    }
  }

  /**
   * Toggles the visibility of the identifier scope dropdown menu.
   *
   * Input:
   *   - event: MouseEvent from clicking the scope chip button.
   * Output: None.
   * Explanation:
   *   Stops event propagation so the document click listener does not immediately close the menu.
   */
  toggleScopeMenu(event: MouseEvent) {
    event.stopPropagation();
    this.isMenuOpen.update((open) => !open);
  }

  /**
   * Selects a new identifier scope from the dropdown menu.
   *
   * Input:
   *   - nextScope: JumpScopeKey selected by the user.
   *   - event: Optional MouseEvent from the menu item click.
   * Output: None.
   * Explanation:
   *   Updates the active scope, closes the menu, and if currently on `/search` with a
   *   non-empty query, updates the URL (`q` and `scope`) to re-resolve under the new scope;
   *   otherwise focuses the search input for the user to continue typing.
   */
  selectScope(nextScope: JumpScopeKey, event?: MouseEvent) {
    event?.stopPropagation();
    this.scope.set(nextScope);
    this.isMenuOpen.set(false);

    const trimmed = this.queryText().trim();
    if (this.isCurrentlyOnSearchRoute() && trimmed) {
      // If input is not empty, navigate to the search page with the new scope
      // and query immediately.
      this.navigateToAllSiteSearch(trimmed, nextScope);
    } else {
      this.searchInput()?.nativeElement.focus();
    }
  }

  /**
   * Updates `queryText` signal when the user types in the Jump Bar input.
   *
   * Input:
   *   - value: Current string value of the input element.
   * Output: None.
   * Explanation:
   *   Keeps `queryText` in sync with user typing so the `Open` and `Clear` buttons reactively toggle.
   */
  onInput(value: string) {
    this.queryText.set(value);
  }

  /**
   * Handles keyboard events inside the Jump Bar `<input>`.
   *
   * Input:
   *   - event: KeyboardEvent from the input element.
   * Output: None.
   * Explanation:
   *   - `Enter`: Submits the search and navigates to `/search`.
   *   - `Escape`: Closes the scope menu if open, or clears/blurs the input.
   */
  onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.submitSearch();
    } else if (event.key === 'Escape') {
      if (this.isMenuOpen()) {
        this.isMenuOpen.set(false);
        return;
      }
      // no need to trigger below actions.
      // if (this.queryText()) {
      //   this.queryText.set('');
      // } else {
      //   this.searchInput()?.nativeElement.blur();
      // }
    }
  }

  /**
   * Submits the current query and navigates to the dedicated All-Site Search page (`/search`).
   *
   * Input:
   *   - event: Optional MouseEvent when triggered by clicking the `Open` button.
   * Output: None.
   * Explanation:
   *   Navigates to `/search` with `q` and optional `scope` (omitted when `'any'`),
   *   preserving Common Query Parameters via `NavigationStrategyRegistry`.
   */
  submitSearch(event?: MouseEvent) {
    event?.stopPropagation();
    const trimmed = this.queryText().trim();
    if (!trimmed) {
      console.log('submitSearch: no trimmed query text, return early');
      return;
    }

    this.isMenuOpen.set(false);
    this.searchInput()?.nativeElement.blur();
    this.navigateToAllSiteSearch(trimmed, this.scope());
  }

  /**
   * Clears the Jump Bar input text and focuses the `<input>`.
   *
   * Input:
   *   - event: MouseEvent from clicking the clear (`X`) button.
   * Output: None.
   * Explanation:
   *   Resets `queryText` to empty string and returns focus to the input element.
   */
  clearSearch(event: MouseEvent) {
    event.stopPropagation();
    this.queryText.set('');
    this.searchInput()?.nativeElement.focus();
  }

  /**
   * Closes the scope dropdown menu when clicking outside the Jump Bar component.
   *
   * Input:
   *   - event: Document-level MouseEvent.
   * Output: None.
   * Explanation:
   *   Checks whether the click target is outside this component's host element.
   */
  onDocumentClick(event: MouseEvent) {
    if (!this.isMenuOpen()) return;
    const target = event.target as Node | null;
    if (target && !this.hostElement.nativeElement.contains(target)) {
      this.isMenuOpen.set(false);
    }
  }

  /**
   * Closes the scope dropdown menu when the user presses the Escape key anywhere
   * (including when focus is on the scope chip button or scope menu rather than the input).
   *
   * Input: None.
   * Output: None.
   * Explanation:
   *   Listens for `document:keydown.escape` so the scope menu closes regardless of focus location.
   */
  onDocumentEscape() {
    if (this.isMenuOpen()) {
      this.isMenuOpen.set(false);
    }
  }

  /**
   * Checks whether the router is currently on the `/search` route.
   *
   * Input: None.
   * Output: boolean (`true` if on `/search`).
   * Explanation:
   *   Inspects primary route segments of the current router URL.
   */
  private isCurrentlyOnSearchRoute(): boolean {
    const urlTree = this.router.parseUrl(this.router.url);
    const primarySegments =
      urlTree.root.children['primary']?.segments.map((s) => s.path) || [];
    return primarySegments.length === 1 && primarySegments[0] === 'search';
  }

  /**
   * Executes client-side navigation to `/search` with `q` and optional `scope`.
   *
   * Input:
   *   - query: Trimmed identifier query string.
   *   - scope: Active `JumpScopeKey`.
   * Output: None.
   * Explanation:
   *   Uses `NavigationStrategyRegistry.getRequired('all_site_search')` to build clean
   *   CSN query parameters preserving Common Query Parameters (`fake_data`, `debug`, etc.).
   */
  private navigateToAllSiteSearch(query: string, scope: JumpScopeKey) {
    const strategy = NavigationStrategyRegistry.getRequired('all_site_search');
    const inputParams: Record<string, unknown> = {'q': query};
    if (scope !== 'any') {
      inputParams['scope'] = scope;
    }
    const queryParams = strategy.toCsnQueryParams(
      inputParams,
      this.commonParamsService.getCommonParams(),
    );
    this.router.navigate([strategy.buildRoutePath(inputParams)], {
      queryParams,
    });
  }
}
