/**
 * @fileoverview Composable store slice managing Fleet table row selection state and bulk toggle/deselect actions.
 */

import {computed, linkedSignal, Signal} from '@angular/core';

import {Row} from '../../../../core/models/search';
import {
  getDeviceTargetKey,
  getRowsSelectionState,
  isRowSelected,
} from '../../utils';

/** Composable managing row selection state and bulk toggle/deselect actions. */
export function useTableSelection(
  selectionScopeKey: Signal<string>,
  rows: Signal<readonly Row[]>,
) {
  /**
   * Map of currently selected composite item keys (`deviceId@@hostName` or `deviceId`) to their
   * search result Row. Automatically resets when the query scope, pagination, or sorting changes.
   */
  const selectedItems = linkedSignal<string, Map<string, Row>>({
    source: () => selectionScopeKey(),
    computation: () => new Map(),
  });

  /** Tri-state checkbox selection state ({all, some}) for the current flat results page. */
  const selectionState = computed(() =>
    getRowsSelectionState(rows(), selectedItems()),
  );

  /** Clears the current selection state. */
  const clearSelection = (): void => {
    if (selectedItems().size === 0) return;
    selectedItems.set(new Map());
  };

  /** Removes the specified rows from the current selection if any are selected. */
  const deselectRows = (targetRows: readonly Row[] | undefined): void => {
    const current = selectedItems();
    if (!targetRows?.length || current.size === 0) return;
    const nextMap = new Map(current);
    for (const row of targetRows) {
      nextMap.delete(getDeviceTargetKey(row));
    }
    if (nextMap.size !== current.size) {
      selectedItems.set(nextMap);
    }
  };

  /** Toggles selection for one or more rows (e.g. a single row, current flat page, or an expanded group card page). */
  const toggleSelectRows = (targetRows: readonly Row[] | undefined): void => {
    if (!targetRows?.length) return;
    const current = selectedItems();
    if (targetRows.every((r) => isRowSelected(r, current))) {
      deselectRows(targetRows);
      return;
    }
    const nextMap = new Map(current);
    for (const row of targetRows) {
      nextMap.set(getDeviceTargetKey(row), row);
    }
    selectedItems.set(nextMap);
  };

  return {
    selectedItems,
    selectionState,
    clearSelection,
    deselectRows,
    toggleSelectRows,
  };
}
