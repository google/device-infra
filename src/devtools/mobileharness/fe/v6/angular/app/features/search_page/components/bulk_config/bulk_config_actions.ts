import {DestroyRef, inject, Type} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';

import {EnvUniverseService} from '../../../../core/services/env_universe_service';
import {BulkConfigDialogData, BulkConfigDialogResult} from '../../models';
import {FleetSearchStore} from '../../services/fleet_search_store';
import {extractDeviceTarget, toFleetProto} from '../../utils';
import {BulkConfigWifiDialog} from './wifi_dialog/bulk_config_wifi_dialog';

/**
 * Composable encapsulating bulk device configuration actions in FleetSearchResultsComponent.
 * Decouples bulk configuration dialog orchestration and menu state from the table view.
 */
export function useBulkConfigActions() {
  const store = inject(FleetSearchStore);
  const dialog = inject(MatDialog);
  const destroyRef = inject(DestroyRef);
  const universe = inject(EnvUniverseService).getUniverseString();

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

  return {
    openWifiDialog,
  };
}
