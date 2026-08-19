import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  OnInit,
  untracked,
} from '@angular/core';
import {takeUntilDestroyed, toSignal} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatChipsModule} from '@angular/material/chips';
import {MatDividerModule} from '@angular/material/divider';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatSelectModule} from '@angular/material/select';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';

import {
  FleetChipResolverRequest,
  TjsResolveChipsRequest,
} from '../../core/models/search';
import {SEARCH_SERVICE} from '../../core/services/search/search_service';
import {LoadingService} from '../../shared/services/loading_service';
import {SearchBox} from './components/search_box/search_box';
import {FleetSearchResultsComponent} from './components/search_results/fleet_search_results';
import {TjsSearchResultsComponent} from './components/search_results/tjs_search_results';
import {SearchPageStore} from './services/search_page_store';
import {
  EntityType,
  FilterChip,
  ParsedQueryFilter,
  parseQueryFilterParam,
  serializeFilterChip,
} from './services/search_utils';

/** Search page containing search input, filters, presets, and result views. */
@Component({
  selector: 'app-search-page',
  standalone: true,
  templateUrl: './search_page.ng.html',
  styleUrl: './search_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatChipsModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatMenuModule,
    MatSelectModule,
    MatTooltipModule,
    FleetSearchResultsComponent,
    TjsSearchResultsComponent,
    SearchBox,
  ],
})
export class SearchPage implements OnInit {
  readonly store = inject(SearchPageStore);
  private readonly loadingService = inject(LoadingService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly searchService = inject(SEARCH_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  readonly routeData = toSignal(this.route.data);
  readonly queryParams = toSignal(this.route.queryParams);

  constructor() {
    this.router.events
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        if (event instanceof NavigationEnd) {
          const urlTree = this.router.parseUrl(this.router.url);
          const f = urlTree.queryParams['f'];
          const gb = urlTree.queryParams['gb'];
          if (!f && !gb) {
            this.store.resetSearchState();
          }
        }
      });

    effect(() => {
      const chips = this.store.activeChips();
      const fleetValue = this.store.fleet();

      const filters = chips.filter((c) => !c.isGroupBy);
      const groupBys = chips
        .filter((c) => c.isGroupBy)
        .map((c) => c.key?.replace(/^group_by_/, '') || c.pillKey);

      const serializedFilters = filters.map(serializeFilterChip);

      const currentParams = this.route.snapshot.queryParams;
      const currentf = currentParams['f'];
      const currentGb = currentParams['gb'] || null;
      const currentFleet = currentParams['fleet'] || null;

      const normalizedCurrentF = Array.isArray(currentf)
        ? (currentf as string[])
        : currentf
          ? [currentf as string]
          : [];

      const norm1 = serializedFilters.map((s) => s.toLowerCase()).sort();
      const norm2 = normalizedCurrentF.map((s) => s.toLowerCase()).sort();

      const f1 = JSON.stringify([
        norm1,
        groupBys.length > 0 ? [...groupBys].sort().join(',') : null,
        fleetValue && fleetValue !== 'internal' ? fleetValue : null,
      ]);
      const f2 = JSON.stringify([
        norm2,
        currentGb ? currentGb.split(',').sort().join(',') : null,
        currentFleet,
      ]);

      if (f1 !== f2) {
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: {
            f: serializedFilters.length > 0 ? serializedFilters : null,
            gb: groupBys.length > 0 ? groupBys.join(',') : null,
            fleet: fleetValue && fleetValue !== 'internal' ? fleetValue : null,
          },
          queryParamsHandling: 'merge',
        });
      }
    });

    effect(() => {
      const data = this.routeData();
      if (data && data['entity']) {
        const nextEntity = data['entity'] as EntityType;
        if (nextEntity !== this.store.entity()) {
          this.store.resetSearchState();
          this.store.entity.set(nextEntity);
        }
      }
      untracked(() => {
        this.store.loadSearchConfig();
        this.store.loadPromotedKeys();
      });
    });

    effect(() => {
      const params = this.queryParams();
      if (!params) return;

      const fleetParam = (params['fleet'] || 'internal') as 'internal' | 'ats';
      if (fleetParam !== untracked(() => this.store.fleet())) {
        untracked(() => this.store.fleet.set(fleetParam));
      }

      const fParamsRaw = params['f'];
      const fParams: string[] = Array.isArray(fParamsRaw)
        ? (fParamsRaw as string[])
        : fParamsRaw
          ? [fParamsRaw as string]
          : [];

      const gbParam = params['gb'] || '';
      const gbKeys = gbParam.split(',').filter(Boolean);

      const localChips = untracked(() => this.store.activeChips());
      const localFilters = localChips.filter((c) => !c.isGroupBy);
      const localGroupBys = localChips
        .filter((c) => c.isGroupBy)
        .map((c) => c.key?.replace(/^group_by_/, '') || c.pillKey);

      const localSerializedFilters = localFilters.map(serializeFilterChip);

      const localNorm = localSerializedFilters
        .map((s: string) => s.toLowerCase())
        .sort();
      const urlNorm = fParams.map((s: string) => s.toLowerCase()).sort();
      const filtersInSync =
        JSON.stringify(localNorm) === JSON.stringify(urlNorm);

      const localGbNorm = localGroupBys
        .map((s: string) => s.toLowerCase())
        .sort();
      const urlGbNorm = gbKeys.map((s: string) => s.toLowerCase()).sort();
      const groupBysInSync =
        JSON.stringify(localGbNorm) === JSON.stringify(urlGbNorm);

      if (!filtersInSync || !groupBysInSync) {
        const parsedFilters = fParams
          .map(parseQueryFilterParam)
          .filter(Boolean) as ParsedQueryFilter[];
        untracked(() => {
          this.resolveUrlStateChips(parsedFilters, gbKeys);
        });
      }
    });
  }

  ngOnInit() {
    this.loadingService.hide();
  }

  fillSearch(text: string) {
    this.store.searchQuery.set(text);
  }

  onBrowseAll() {
    this.store.browseAll.set(true);
    this.store.executeFleetSearch();
  }

  private resolveUrlStateChips(
    parsedFilters: ParsedQueryFilter[],
    gbKeys: string[],
  ) {
    if (parsedFilters.length === 0 && gbKeys.length === 0) {
      this.store.resetSearchState();
      return;
    }

    if (!this.store.isTjs()) {
      const req: FleetChipResolverRequest = {
        filters:
          parsedFilters.length > 0
            ? parsedFilters.map((pf) => pf.filter)
            : undefined,
        groupByKeys: gbKeys.length > 0 ? gbKeys : undefined,
      };
      this.searchService
        .resolveFleetChips(req)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (res) => {
            const updatedChips: FilterChip[] = [];
            if (res.filterChips && res.filterChips.length > 0) {
              res.filterChips.forEach((fc, idx) => {
                const pf = parsedFilters[idx];
                updatedChips.push({
                  key: pf?.key,
                  pillKey: fc.pillKey,
                  pillCondition: fc.pillCondition,
                  metadata: fc.metadata || pf?.metadata,
                  rawValues: pf?.rawValues,
                  negated: pf?.negated,
                  complex: pf?.complex,
                });
              });
            } else if (parsedFilters.length > 0) {
              for (const pf of parsedFilters) {
                const meta = this.store.keyMetadataMap.get(pf.key);
                updatedChips.push({
                  key: pf.key,
                  pillKey: meta?.keyDisplayName || pf.key,
                  pillCondition: pf.fallbackPillCondition,
                  rawValues: pf.rawValues,
                  metadata: meta,
                  negated: pf.negated,
                  complex: pf.complex,
                });
              }
            }

            for (let i = 0; i < gbKeys.length; i++) {
              const gb = gbKeys[i];
              const gbc = res.groupByChips?.[i];
              const meta = this.store.keyMetadataMap.get(gb);
              const display =
                gbc?.displayName || gbc?.pillKey || meta?.keyDisplayName || gb;
              updatedChips.push({
                key: 'group_by_' + gb,
                pillKey: display,
                pillCondition: display,
                isGroupBy: true,
              });
            }

            const currentSerialized = this.store
              .activeChips()
              .map(serializeFilterChip)
              .map((s) => s.toLowerCase())
              .sort()
              .join(';');
            const nextSerialized = updatedChips
              .map(serializeFilterChip)
              .map((s) => s.toLowerCase())
              .sort()
              .join(';');
            if (currentSerialized !== nextSerialized) {
              this.store.activeChips.set(updatedChips);
              this.store.loadPromotedKeys();
            }
          },
          error: () => {
            const fallbackChips: FilterChip[] = [];
            for (const pf of parsedFilters) {
              const meta = this.store.keyMetadataMap.get(pf.key);
              fallbackChips.push({
                key: pf.key,
                pillKey: meta?.keyDisplayName || pf.key,
                pillCondition: pf.fallbackPillCondition,
                rawValues: pf.rawValues,
                metadata: meta,
                negated: pf.negated,
                complex: pf.complex,
              });
            }
            for (const gb of gbKeys) {
              const meta = this.store.keyMetadataMap.get(gb);
              const display = meta?.keyDisplayName || gb;
              fallbackChips.push({
                key: 'group_by_' + gb,
                pillKey: display,
                pillCondition: display,
                isGroupBy: true,
              });
            }
            this.store.activeChips.set(fallbackChips);
          },
        });
    } else {
      const req: TjsResolveChipsRequest = {
        filters: parsedFilters.map((pf) => pf.tjsFilter),
      };
      this.searchService
        .resolveTjsChips(req)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (res) => {
            if (res.chips && res.chips.length > 0) {
              const updatedChips: FilterChip[] = [];
              res.chips.forEach((c, idx) => {
                const pf = parsedFilters[idx];
                updatedChips.push({
                  key: pf?.key,
                  pillKey: c.pillKey,
                  pillCondition: c.pillCondition,
                  rawValues: pf?.rawValues,
                  negated: pf?.negated,
                  complex: pf?.complex,
                });
              });
              const currentSerialized = this.store
                .activeChips()
                .map(serializeFilterChip)
                .map((s) => s.toLowerCase())
                .sort()
                .join(';');
              const nextSerialized = updatedChips
                .map(serializeFilterChip)
                .map((s) => s.toLowerCase())
                .sort()
                .join(';');
              if (currentSerialized !== nextSerialized) {
                this.store.activeChips.set(updatedChips);
              }
            }
          },
        });
    }
  }
}
