import {DestroyRef, inject, Type} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';

import {EnvUniverseService} from '../../../../core/services/env_universe_service';
import {BulkConfigDialogBaseData, BulkConfigDialogResult} from '../../models';
import {FleetSearchStore} from '../../services/fleet_search_store';
import {toFleetProto} from '../../utils/search_filter_utils';
import {BulkConfigWifiDialog} from './wifi_dialog/bulk_config_wifi_dialog';

/**
 * Composable encapsulating bulk device configuration actions in FleetSearchResultsComponent.
 * Decouples bulk configuration dialog orchestration and menu state from the table view.
 */
export function useBulkConfigActions() {
  const store = inject(FleetSearchStore);
  const dialog = inject(MatDialog);
  const destroyRef = inject(DestroyRef);
  const envUniverseService = inject(EnvUniverseService);

  /** Opens a bulk configuration dialog for currently selected devices and refreshes search if updated. */
  const openBulkConfigDialog = <
    T,
    D extends BulkConfigDialogBaseData = BulkConfigDialogBaseData,
  >(
    dialogComponent: Type<T>,
    extraData?: Omit<D, keyof BulkConfigDialogBaseData>,
    panelClass?: string,
  ): void => {
    const selected = Array.from(store.selectedItems());
    if (selected.length === 0) return;

    dialog
      .open<T, D, BulkConfigDialogResult>(dialogComponent, {
        ...(panelClass ? {panelClass} : {}),
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

  return {
    openBulkConfigDialog,
    openBulkConfigWifiDialog,
  };
}
