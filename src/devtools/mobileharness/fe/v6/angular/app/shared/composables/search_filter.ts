import {computed, signal, Signal} from '@angular/core';

/** Represents a single key-value entry that can be searched or filtered. */
export interface FilterEntry<V = string> {
  key: string;
  value: V;
}

/**
 * Composable function to encapsulate search-filtering logic of any key-value dictionary.
 *
 * @param dataInput A reactive signal holding the raw key-value dictionary.
 */
export function createSearchFilter<V = string>(
  dataInput: Signal<Record<string, V> | undefined>
) {
  const searchTerm = signal<string>('');

  const hasData = computed(() => {
    const data = dataInput();
    return data && Object.keys(data).length > 0;
  });

  const filteredData = computed<Array<FilterEntry<V>>>(() => {
    const data = dataInput();
    const term = searchTerm().toLowerCase().trim();
    if (!data) return [];
    const entries = Object.entries(data).map(([key, value]) => ({
      key,
      value,
    }));
    if (!term) return entries;
    return entries.filter(
      (item) =>
        item.key.toLowerCase().includes(term) ||
        String(item.value).toLowerCase().includes(term),
    );
  });

  return {
    searchTerm,
    hasData,
    filteredData,
  };
}
