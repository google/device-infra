import {computed, DestroyRef, inject, linkedSignal, Type} from '@angular/core';
import {rxResource, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {of} from 'rxjs';

import {
  ConfigurableDimension,
  DimensionList,
  GetConfigurableDimensionsResponse,
} from '../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {getErrorMessage} from '../../../../shared/utils/error_utils';
import {BulkConfigDialogData, BulkConfigDialogResult} from '../../models';
import {FleetSearchStore} from '../../services/fleet_search_store';
import {extractDeviceTarget} from '../../utils';
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
  const configService = inject(CONFIG_SERVICE);

  /**
   * Batch device configuration applies only to devices on the deployment's own
   * fleet (`google_1p` in 1P and the local fleet in OSS, both represented as
   * `store.fleet() === 'internal'`). Host views and Partner ATS views (`'ats'`)
   * hide bulk configuration actions.
   */
  const isOwnFleetDevices = computed(
    () => store.entity() === 'devices' && store.fleet() === 'internal',
  );

  /**
   * Latches once at least one device is selected on the deployment's own fleet,
   * retaining the loaded dimension metadata across selection clear/re-select
   * and tab switches.
   */
  const shouldLoadDimensions = linkedSignal<boolean, boolean>({
    source: () => isOwnFleetDevices() && store.selectedItems().size > 0,
    computation: (active, previous) => active || Boolean(previous?.value),
  });

  const dimensionsResource = rxResource<
    GetConfigurableDimensionsResponse | null,
    boolean
  >({
    params: shouldLoadDimensions,
    stream: ({params: shouldLoad}) =>
      shouldLoad ? configService.getConfigurableDimensions() : of(null),
  });

  /** Consolidated dimension configuration state for the active view. */
  const dimensionConfig = computed(() => {
    if (!isOwnFleetDevices()) {
      return {
        isLoading: false,
        error: null,
        enabled: false,
        dimensions: [],
        allowCustom: false,
      };
    }
    const res = dimensionsResource.hasValue()
      ? dimensionsResource.value()
      : null;
    const err = dimensionsResource.error();
    return {
      isLoading: dimensionsResource.isLoading(),
      error: err ? getErrorMessage(err) : null,
      enabled: res === null ? true : Boolean(res.enabled),
      dimensions: res?.dimensions ?? [],
      allowCustom: Boolean(res?.allowCustomDimensions),
    };
  });

  /** Retries fetching configurable dimensions after an RPC failure. */
  const retryDimensions = (): void => {
    dimensionsResource.reload();
  };

  /** Opens a bulk configuration dialog for currently selected devices and refreshes search if updated. */
  const openDialog = <T>(
    dialogComponent: Type<T>,
    extraData?: Partial<BulkConfigDialogData>,
  ): void => {
    const dimConfig = dimensionConfig();
    if (!dimConfig.enabled || dimConfig.isLoading || dimConfig.error) {
      return;
    }
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
    retryDimensions,
    openWifiDialog,
    openDimensionDialog,
  };
}
