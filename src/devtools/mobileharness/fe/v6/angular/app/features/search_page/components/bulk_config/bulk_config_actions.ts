import {
  computed,
  DestroyRef,
  effect,
  inject,
  signal,
  Type,
  untracked,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {Subscription} from 'rxjs';
import {finalize} from 'rxjs/operators';

import {
  ConfigurableDimension,
  DimensionList,
  GetConfigurableDimensionsResponse,
} from '../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {EnvUniverseService} from '../../../../core/services/env_universe_service';
import {
  BulkConfigDialogBaseData,
  BulkConfigDialogResult,
  BulkConfigDimensionDialogData,
} from '../../models';
import {FleetSearchStore} from '../../services/fleet_search_store';
import {toFleetProto} from '../../utils/search_filter_utils';
import {BulkConfigDimensionDialog} from './dimension_dialog/bulk_config_dimension_dialog';
import {BulkConfigWifiDialog} from './wifi_dialog/bulk_config_wifi_dialog';

/**
 * Composable encapsulating bulk device configuration menu state, context-keyed
 * dimension caching, and dialog orchestration in FleetSearchResultsComponent.
 */
export function useBulkConfigActions() {
  const store = inject(FleetSearchStore);
  const dialog = inject(MatDialog);
  const destroyRef = inject(DestroyRef);
  const envUniverseService = inject(EnvUniverseService);
  const configService = inject(CONFIG_SERVICE);

  /** Cached configurable dimensions response keyed by (universe + fleet). */
  const cachedDimensions = signal<{
    contextKey: string;
    response: GetConfigurableDimensionsResponse;
  } | null>(null);

  /** Loading state for configurable dimensions button. */
  const isLoadingDimensions = signal<boolean>(false);

  /** Context key currently being fetched in-flight, used to deduplicate requests. */
  let loadingDimensionsContextKey: string | null = null;
  let loadDimensionsSub: Subscription | null = null;

  /** Active context key combining current universe and fleet partition. */
  const currentDimensionsContextKey = computed<string>(
    () =>
      `${envUniverseService.getUniverseString()}::${toFleetProto(store.fleet())}`,
  );

  /** Cached response matching the active (universe, fleet) context, or null if not yet loaded. */
  const activeDimensionsResponse =
    computed<GetConfigurableDimensionsResponse | null>(() => {
      const cached = cachedDimensions();
      return cached?.contextKey === currentDimensionsContextKey()
        ? cached.response
        : null;
    });

  /** Whether configurable dimensions have already been loaded for the active universe and fleet. */
  const hasLoadedDimensions = computed<boolean>(
    () => activeDimensionsResponse() !== null,
  );

  /** Whether batch dimension configuration is enabled for the active universe and fleet. */
  const isDimensionConfigEnabled = computed<boolean>(
    () => activeDimensionsResponse()?.enabled !== false,
  );

  /** Configurable dimensions loaded from backend for the Configuration dropdown. */
  const configurableDimensions = computed<ConfigurableDimension[]>(
    () => activeDimensionsResponse()?.dimensions ?? [],
  );

  /** Whether custom dimensions are allowed by backend. */
  const allowCustomDimensions = computed<boolean>(() =>
    Boolean(activeDimensionsResponse()?.allowCustomDimensions),
  );

  /** Loads configurable dimensions for the active universe and fleet if not already cached. */
  const loadConfigurableDimensions = (): void => {
    const universe = envUniverseService.getUniverseString();
    const fleet = toFleetProto(store.fleet());
    const contextKey = `${universe}::${fleet}`;

    if (hasLoadedDimensions()) return;
    if (isLoadingDimensions() && loadingDimensionsContextKey === contextKey) {
      return;
    }

    loadDimensionsSub?.unsubscribe();
    loadingDimensionsContextKey = contextKey;
    isLoadingDimensions.set(true);
    loadDimensionsSub = configService
      .getConfigurableDimensions({universe, fleet})
      .pipe(
        takeUntilDestroyed(destroyRef),
        finalize(() => {
          loadingDimensionsContextKey = null;
          isLoadingDimensions.set(false);
        }),
      )
      .subscribe({
        next: (response) => {
          cachedDimensions.set({contextKey, response});
        },
        error: () => {},
      });
  };

  // Reactively pre-fetch configurable dimensions when devices are selected in the toolbar
  // for the active (universe, fleet) context.
  effect(() => {
    const isDevices = store.entity() === 'devices';
    const hasSelection =
      store.selectedCount() > 0 || store.selectedItems().size > 0;
    const isSelectAll = store.selectAllMatching();
    const alreadyLoaded = hasLoadedDimensions();

    if (isDevices && hasSelection && !isSelectAll && !alreadyLoaded) {
      untracked(() => {
        loadConfigurableDimensions();
      });
    }
  });

  /** Opens a bulk configuration dialog for currently selected devices and refreshes search if updated. */
  const openBulkConfigDialog = <
    T,
    D extends BulkConfigDialogBaseData = BulkConfigDialogBaseData,
  >(
    dialogComponent: Type<T>,
    extraData?: Omit<D, keyof BulkConfigDialogBaseData>,
  ): void => {
    const selected = Array.from(store.selectedItems());
    if (selected.length === 0) return;

    dialog
      .open<T, D, BulkConfigDialogResult>(dialogComponent, {
        autoFocus: false,
        data: {
          deviceIds: selected,
          ...extraData,
          universe: envUniverseService.getUniverseString(),
          fleet: toFleetProto(store.fleet()),
        } as D,
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe((res?: BulkConfigDialogResult) => {
        if (res?.updated) {
          store.clearSelection();
          store.executeSearch();
        }
      });
  };

  /** Opens the Bulk Wi-Fi configuration dialog for currently selected devices. */
  const openBulkConfigWifiDialog = (): void => {
    openBulkConfigDialog(BulkConfigWifiDialog);
  };

  /** Opens the Bulk Dimension configuration dialog for the selected curated dimension. */
  const openBulkConfigDimensionDialog = (
    dimension: ConfigurableDimension,
  ): void => {
    openBulkConfigDialog<
      BulkConfigDimensionDialog,
      BulkConfigDimensionDialogData
    >(BulkConfigDimensionDialog, {dimension});
  };

  /** Opens the Bulk Dimension configuration dialog for a custom dimension key. */
  const openCustomDimensionDialog = (dimensionList: DimensionList): void => {
    openBulkConfigDialog<
      BulkConfigDimensionDialog,
      BulkConfigDimensionDialogData
    >(BulkConfigDimensionDialog, {dimensionList});
  };

  return {
    isDimensionConfigEnabled,
    configurableDimensions,
    allowCustomDimensions,
    isLoadingDimensions,
    hasLoadedDimensions,
    loadConfigurableDimensions,
    openBulkConfigWifiDialog,
    openBulkConfigDimensionDialog,
    openCustomDimensionDialog,
  };
}
