/**
 * @fileoverview Composable store slice managing Fleet visible table columns, localStorage persistence, header descriptions, and unknown-column recovery.
 */

import {computed, inject, linkedSignal} from '@angular/core';

import {
  Column,
  FleetColumnDescriptor,
  FleetSearchConfig,
} from '../../../../core/models/search';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {VisibleColumnsSliceOptions} from '../../models';
import {
  clearStoredVisibleColumns,
  getStoredVisibleColumns,
  getUnknownColumnKeyFromError,
  saveStoredVisibleColumns,
} from '../../utils';

/** Composable managing visible table columns, localStorage persistence, and header descriptions. */
export function useVisibleColumns(opts: VisibleColumnsSliceOptions) {
  const snackBarService = inject(SnackBarService);

  /** Current visible column descriptors (isomorphic support for localStorage and searchConfig defaults). */
  const visibleColumnDescriptors = linkedSignal<
    {cfg: FleetSearchConfig | null; entity: string; fleet: string},
    FleetColumnDescriptor[]
  >({
    source: () => ({
      cfg: opts.searchConfig(),
      entity: opts.entity(),
      fleet: opts.fleet(),
    }),
    computation: ({cfg, entity, fleet}) => {
      const stored = getStoredVisibleColumns(entity, fleet);
      return stored?.length ? stored : (cfg?.columns?.defaults ?? []);
    },
  });

  /** Set of column keys currently requested/visible in the data table for search query execution. */
  const visibleColumns = computed<Set<string>>(
    () => new Set(visibleColumnDescriptors().map((c) => c.key)),
    {
      equal: (a, b) => {
        if (a.size !== b.size) return false;
        const iterB = b.values();
        for (const key of a) {
          if (key !== iterB.next().value) return false;
        }
        return true;
      },
    },
  );

  /** Display columns to render in search results table. */
  const displayColumns = computed<Column[]>(() => {
    const cols = opts.searchResults()?.flat?.columns;
    if (cols?.length) {
      return cols;
    }
    return visibleColumnDescriptors().map((c) => ({
      key: c.key,
      displayName: c.displayName,
      ...(c.description ? {description: c.description} : {}),
    }));
  });

  /**
   * Descriptions of the column keys that have one, for the table header tooltips. Seeds saved
   * visible column descriptors first so live key metadata, BFF default column descriptors, and
   * flat or grouped result columns override any cached localStorage text for the same key.
   */
  const columnDescriptions = computed<ReadonlyMap<string, string>>(
    () => {
      const descriptions = new Map<string, string>();
      for (const {key, description} of visibleColumnDescriptors()) {
        if (description) descriptions.set(key, description);
      }
      for (const [key, meta] of opts.keyMetadataMap()) {
        if (meta.description) descriptions.set(key, meta.description);
      }
      const results = opts.searchResults();
      for (const list of [
        opts.searchConfig()?.columns?.defaults,
        results?.flat?.columns,
        results?.grouped?.groupByKeys,
      ]) {
        for (const item of list ?? []) {
          if (item.description) descriptions.set(item.key, item.description);
        }
      }
      return descriptions;
    },
    {
      equal: (a, b) =>
        a.size === b.size &&
        Array.from(a.entries()).every(([k, v]) => b.get(k) === v),
    },
  );

  /** Updates `visibleColumnDescriptors`, persists changes to storage, and notifies the store if the effective key set changed. */
  const applyVisibleColumnUpdate = (
    nextDescriptors: FleetColumnDescriptor[],
    persistFn: () => void,
  ): void => {
    const prev = visibleColumns();
    persistFn();
    visibleColumnDescriptors.set(nextDescriptors);
    if (visibleColumns() !== prev) {
      opts.onVisibleColumnsChanged();
    }
  };

  /** Resets visible table columns to searchConfig defaults and clears custom saved preferences. */
  const resetVisibleColumns = (): void => {
    applyVisibleColumnUpdate(
      opts.searchConfig()?.columns?.defaults ?? [],
      () => {
        clearStoredVisibleColumns(opts.entity(), opts.fleet());
      },
    );
  };

  /** Sets the visible table columns, persists them to localStorage, and updates table results if columns changed. */
  const setVisibleColumns = (
    columns: readonly FleetColumnDescriptor[],
  ): void => {
    const descriptors = [...columns];
    const defaults = opts.searchConfig()?.columns?.defaults ?? [];
    const matchesDefaults =
      defaults.length > 0 &&
      descriptors.length === defaults.length &&
      descriptors.every((d, idx) => d.key === defaults[idx].key);

    if (matchesDefaults) {
      resetVisibleColumns();
      return;
    }

    applyVisibleColumnUpdate(descriptors, () => {
      saveStoredVisibleColumns(opts.entity(), opts.fleet(), descriptors);
    });
  };

  /** Recovers from a BFF unknown-column error by clearing saved columns and retrying with defaults. */
  const recoverFromUnknownColumnKey = (err: unknown): boolean => {
    const unknownKey = getUnknownColumnKeyFromError(err);
    const entity = opts.entity();
    const fleet = opts.fleet();
    if (!unknownKey || !getStoredVisibleColumns(entity, fleet)) {
      return false;
    }
    clearStoredVisibleColumns(entity, fleet);
    visibleColumnDescriptors.set(opts.searchConfig()?.columns?.defaults ?? []);
    snackBarService.showWarning(
      `Saved column "${unknownKey}" is no longer supported. Cleared your saved columns and retrying with the default columns…`,
    );
    return true;
  };

  return {
    visibleColumnDescriptors,
    visibleColumns,
    displayColumns,
    columnDescriptions,
    setVisibleColumns,
    resetVisibleColumns,
    recoverFromUnknownColumnKey,
  };
}
