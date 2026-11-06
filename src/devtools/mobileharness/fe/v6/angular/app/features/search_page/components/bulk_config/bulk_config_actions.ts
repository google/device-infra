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
import {EnvUniverseService} from '../../../../core/services/env_universe_service';
import {getErrorMessage} from '../../../../shared/utils/error_utils';
import {
  BulkConfigDialogData,
  BulkConfigDialogResult,
  DeviceConfigTemplateDialogData,
} from '../../models';
import {FleetSearchStore} from '../../services/fleet_search_store';
import {extractDeviceTarget, toFleetProto} from '../../utils';
import {BulkConfigDimensionDialog} from './dimension_dialog/bulk_config_dimension_dialog';
import {DeviceConfigTemplateDialog} from './template_dialog/device_config_template_dialog';
import {BulkConfigWifiDialog} from './wifi_dialog/bulk_config_wifi_dialog';

/**
 * Composable encapsulating bulk device and host configuration menu state,
 * ATS controller template access, reactive dimension loading, and dialog
 * orchestration in FleetSearchResultsComponent.
 */
export function useBulkConfigActions() {
  const store = inject(FleetSearchStore);
  const dialog = inject(MatDialog);
  const destroyRef = inject(DestroyRef);
  const envUniverse = inject(EnvUniverseService);
  const universe = envUniverse.getUniverseString();
  const isAts = Boolean(envUniverse.isAts?.());
  const configService = inject(CONFIG_SERVICE);

  /** Device-level Wi-Fi is supported in Internal 1P and hidden in ATS. */
  const canConfigureDeviceWifi = !isAts;

  /** Host-level default Wi-Fi is configured from Host Search in ATS. */
  const canConfigureHostWifi = isAts;

  /** Controller-level Device Config Template is exposed on Device Search in ATS. */
  const canEditDeviceConfigTemplate = isAts;

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
        ? configService.getConfigurableDimensions({universe, fleet})
        : of(null),
  });

  /** Consolidated dimension configuration state for the active fleet. */
  const dimensionConfig = computed(() => {
    const res = dimensionsResource.hasValue()
      ? dimensionsResource.value()
      : null;
    const err = dimensionsResource.error();
    return {
      isLoading: dimensionsResource.isLoading(),
      error: err ? getErrorMessage(err) : null,
      enabled: res?.enabled !== false,
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

  /** Opens the Bulk Wi-Fi configuration dialog for currently selected devices in 1P. */
  const openWifiDialog = (): void => {
    if (!canConfigureDeviceWifi) return;
    openDialog(BulkConfigWifiDialog);
  };

  /** Opens the Bulk Wi-Fi configuration dialog for currently selected hosts in ATS. */
  const openHostWifiDialog = (): void => {
    if (!canConfigureHostWifi || store.entity() !== 'hosts') return;
    const hostNames = Array.from(
      store.selectedItems().values(),
      (row) => row.id,
    ).filter(Boolean);
    if (hostNames.length === 0) return;

    dialog
      .open<BulkConfigWifiDialog, BulkConfigDialogData, BulkConfigDialogResult>(
        BulkConfigWifiDialog,
        {
          autoFocus: false,
          data: {
            devices: [],
            hostNames,
            universe,
            fleet: toFleetProto(store.fleet()),
          },
        },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe((res?: BulkConfigDialogResult) => {
        if (res?.updated) {
          store.clearSelection();
          store.executeSearch();
        }
      });
  };

  /** Opens the controller-level Device Config Template dialog on ATS Device Search. */
  const openTemplateDialog = (): void => {
    if (!canEditDeviceConfigTemplate || store.entity() !== 'devices') return;
    dialog
      .open<
        DeviceConfigTemplateDialog,
        DeviceConfigTemplateDialogData,
        BulkConfigDialogResult
      >(DeviceConfigTemplateDialog, {
        autoFocus: false,
        data: {
          universe,
          fleet: toFleetProto(store.fleet()),
        },
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe((res?: BulkConfigDialogResult) => {
        if (res?.updated) {
          store.executeSearch();
        }
      });
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
    isAts,
    canConfigureDeviceWifi,
    canConfigureHostWifi,
    canEditDeviceConfigTemplate,
    dimensionConfig,
    retryDimensions,
    openWifiDialog,
    openHostWifiDialog,
    openTemplateDialog,
    openDimensionDialog,
  };
}
