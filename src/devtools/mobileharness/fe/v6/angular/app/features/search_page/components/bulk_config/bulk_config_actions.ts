import {computed, DestroyRef, inject, linkedSignal, Type} from '@angular/core';
import {rxResource, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {of} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {
  ConfigurableDimension,
  DimensionList,
  GetConfigurableDimensionsResponse,
} from '../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {EnvUniverseService} from '../../../../core/services/env_universe_service';
import {BulkConfigDialogData, BulkConfigDialogResult} from '../../models';
import {FleetSearchStore} from '../../services/fleet_search_store';
import {extractDeviceTarget, toFleetProto} from '../../utils';
import {BulkConfigDimensionDialog} from './dimension_dialog/bulk_config_dimension_dialog';
import {BulkConfigWifiDialog} from './wifi_dialog/bulk_config_wifi_dialog';

/**
 * Composable encapsulating bulk device configuration menu state, reactive
 * dimension loading, and dialog orchestration in FleetSearchResultsComponent.
 */
export function useBulkConfigActions() {
  const store = inject(FleetSearchStore);
  const dialog = inject(MatDialog);
  const destroyRef = inject(DestroyRef);
  const universe = inject(EnvUniverseService).getUniverseString();
  const configService = inject(CONFIG_SERVICE);

  /**
   * Latches the active fleet once at least one device is selected, retaining
   * the loaded dimension config across selection clear/re-select within the
   * same fleet while invalidating when the fleet changes.
   */
  const activeFleet = linkedSignal<
    {fleet: string; hasSelection: boolean},
    string | undefined
  >({
    source: () => ({
      fleet: toFleetProto(store.fleet()),
      hasSelection:
        store.entity() === 'devices' && store.selectedItems().size > 0,
    }),
    computation: ({fleet, hasSelection}, previous) =>
      hasSelection || previous?.value === fleet ? fleet : undefined,
  });

  const dimensionsResource = rxResource<
    GetConfigurableDimensionsResponse | null,
    string | undefined
  >({
    params: activeFleet,
    stream: ({params: fleet}) =>
      fleet
        ? configService
            .getConfigurableDimensions({universe, fleet})
            .pipe(catchError(() => of(null)))
        : of(null),
  });

  /** Consolidated dimension configuration state for the active fleet. */
  const dimensionConfig = computed(() => {
    const res = dimensionsResource.value();
    return {
      isLoading: dimensionsResource.isLoading(),
      enabled: res?.enabled !== false,
      dimensions: res?.dimensions ?? [],
      allowCustom: Boolean(res?.allowCustomDimensions),
    };
  });

  /** Opens a bulk configuration dialog for currently selected devices and refreshes search if updated. */
  const openDialog = <T>(
    dialogComponent: Type<T>,
    extraData?: Partial<BulkConfigDialogData>,
  ): void => {
    if (store.entity() !== 'devices') return;
    const selected = Array.from(
      store.selectedItems().values(),
      extractDeviceTarget,
    );
    if (selected.length === 0) return;

    dialog
      .open<T, BulkConfigDialogData, BulkConfigDialogResult>(dialogComponent, {
        autoFocus: false,
        data: {
          devices: selected,
          ...extraData,
          universe,
          fleet: toFleetProto(store.fleet()),
        },
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
  const openWifiDialog = (): void => {
    openDialog(BulkConfigWifiDialog);
  };

  /** Opens the Bulk Dimension configuration dialog for a curated dimension or custom dimension scope. */
  const openDimensionDialog = (
    target: ConfigurableDimension | 'supported' | 'required',
  ): void => {
    openDialog(
      BulkConfigDimensionDialog,
      typeof target === 'object'
        ? {dimension: target}
        : {
            dimensionList:
              target === 'required'
                ? DimensionList.DIMENSION_LIST_REQUIRED
                : DimensionList.DIMENSION_LIST_SUPPORTED,
          },
    );
  };

  return {
    dimensionConfig,
    openWifiDialog,
    openDimensionDialog,
  };
}
