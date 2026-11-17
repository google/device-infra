/**
 * @fileoverview Pure helpers for search suggestion tracking, count badge formatting, popover chip focus, KeyPicker entries, and UI preference storage.
 */

import {
  FleetFilterKeyEntry,
  FleetGroupByKeyEntry,
} from '../../../core/models/search';
import {
  FilterChip,
  FocusChipType,
  KeyPickerMode,
  SearchBoxSuggestion,
} from '../models';
import {getChipKey} from './search_filter_utils';
import {readStorage, writeStorage} from './search_storage_utils';

const STORAGE_AUTO_COLLAPSE_PREFIX = 'mh_fe_v6_auto_collapse_after_apply_';
const STORAGE_FILTER_COLLAPSED_PREFIX = 'mh_fe_v6_filter_collapsed_';

/**
 * Retrieves the saved auto-collapse preference from localStorage.
 * Scoped per entity and fleet partition.
 */
export function getStoredAutoCollapseAfterApply(
  entity: string,
  fleet: string,
): boolean | null {
  const parsed = readStorage(
    `${STORAGE_AUTO_COLLAPSE_PREFIX}${entity}_${fleet}`,
  );
  if (typeof parsed === 'boolean') {
    return parsed;
  }
  if (parsed === 'true') return true;
  if (parsed === 'false') return false;
  return null;
}

/**
 * Persists the auto-collapse preference to localStorage.
 * Scoped per entity and fleet partition.
 */
export function saveStoredAutoCollapseAfterApply(
  entity: string,
  fleet: string,
  enabled: boolean,
): void {
  writeStorage(`${STORAGE_AUTO_COLLAPSE_PREFIX}${entity}_${fleet}`, enabled);
}

/**
 * Retrieves the saved collapsed state of the quick filter / group-by panel
 * ("Hide" / "Suggested filters") from localStorage. Scoped per entity so that
 * the device and host search pages remember it independently.
 *
 * @param entity The search entity (e.g. 'devices', 'hosts').
 * @return True if the panel was hidden, false if shown, or null if never saved.
 */
export function getStoredFilterCollapsed(entity: string): boolean | null {
  const parsed = readStorage(`${STORAGE_FILTER_COLLAPSED_PREFIX}${entity}`);
  return typeof parsed === 'boolean' ? parsed : null;
}

/**
 * Persists the collapsed state of the quick filter / group-by panel to
 * localStorage. Scoped per entity.
 *
 * @param entity The search entity (e.g. 'devices', 'hosts').
 * @param collapsed Whether the panel is hidden.
 */
export function saveStoredFilterCollapsed(
  entity: string,
  collapsed: boolean,
): void {
  writeStorage(`${STORAGE_FILTER_COLLAPSED_PREFIX}${entity}`, collapsed);
}

/** Locates the rendered HTMLElement for an active filter chip matching key or title and scrolls it into view. */
export function findChipAnchorElement(
  elements: ReadonlyArray<{nativeElement: HTMLElement}>,
  key: string,
  title?: string,
): HTMLElement | null {
  const k = key.toLowerCase();
  const t = title ? title.toLowerCase() : null;
  for (const item of elements) {
    const el = item.nativeElement;
    if (!el || !el.getAttribute) continue;
    const chipKey = el.getAttribute('data-chip-key')?.toLowerCase() || '';
    const pillKey = el.getAttribute('data-chip-pill-key')?.toLowerCase() || '';
    if (
      chipKey === k ||
      pillKey === k ||
      (t && (chipKey === t || pillKey === t))
    ) {
      el.scrollIntoView?.({block: 'nearest'});
      return el;
    }
  }
  return null;
}

/** Formats the multi-line summary tooltip for a collapsed composite `N filters` chip. */
export function formatFilterSummaryTooltip(
  chips: readonly FilterChip[],
): string {
  const lines = chips.map((c) => `${c.pillKey} (${c.pillCondition})`);
  return `${lines.join('\n')}\n\nClick to view or edit.`;
}

/** Formats the multi-line summary tooltip for a collapsed composite `M group-by` chip. */
export function formatGroupBySummaryTooltip(
  chips: readonly FilterChip[],
): string {
  const keys = chips.map((c) => c.pillKey);
  return `Group by: ${keys.join(', ')}\n\nClick to view or edit.`;
}

/** Checks whether the given chip matches the currently focused chip key and type in the popover. */
export function isChipFocusedInPopover(
  chip: FilterChip,
  expectedType: FocusChipType,
  focusType: FocusChipType | null,
  focusKey: string | null,
): boolean {
  if (focusType !== expectedType || !focusKey) return false;
  const normFocus = focusKey.toLowerCase();
  const chipKey = getChipKey(chip).toLowerCase();
  const pillKey = (chip.pillKey || '').toLowerCase();
  return normFocus === chipKey || normFocus === pillKey;
}

/** Checks whether the pending chip matches the currently focused filter key in the popover. */
export function isPendingChipFocusedInPopover(
  pKey: string,
  focusType: FocusChipType | null,
  focusKey: string | null,
): boolean {
  if (focusType !== 'filter' || !focusKey) return false;
  return focusKey.toLowerCase() === pKey.toLowerCase();
}

/** Stable tracking function for suggestion items in `@for` loop to avoid DOM churn. */
export function trackSuggestion(item: SearchBoxSuggestion): string {
  if (item.openPicker?.key) {
    return `picker:${item.openPicker.key}`;
  }
  if (item.addGroupBy?.key) {
    return `groupby:${item.addGroupBy.key}`;
  }
  const af = item.applyFilter;
  if (af && 'resultingFilter' in af && af.resultingFilter?.key) {
    return `apply:${af.resultingFilter.key}:${item.label || ''}:${af.pillCondition || ''}`;
  }
  if (af && 'filter' in af && af.filter?.key) {
    return `apply-tjs:${af.filter.key}:${item.label || ''}:${af.pillCondition || ''}`;
  }
  const text = (item.mainText || []).map((seg) => seg.text).join('');
  return `item:${item.label || ''}:${text}`;
}

/** Formats the suggestion count badge text according to BFF count specifications. */
export function formatSuggestionCount(item: SearchBoxSuggestion): string {
  if (item.count === undefined || item.count === null) return '';
  const prefix = item.count > 0 ? item.countPrefix || '' : '';
  const formattedNum = item.count.toLocaleString();
  const unit = item.countUnit ? ` ${item.countUnit}` : '';
  return `${prefix}${formattedNum}${unit}`;
}

/** Tooltip for suggestion counts that exceed maximum scan threshold. */
export function getSuggestionCountTooltip(item: SearchBoxSuggestion): string {
  return item.overMax
    ? 'That many groups is hard to scan — consider filtering first.'
    : '';
}

/** Resolves the display name from either a filter-key or group-by-key catalog entry. */
export function getKeyEntryName(
  entry: FleetFilterKeyEntry | FleetGroupByKeyEntry,
): string {
  if ('displayName' in entry && entry.displayName) {
    return entry.displayName;
  }
  if ('metadata' in entry && entry.metadata?.keyDisplayName) {
    return entry.metadata.keyDisplayName;
  }
  return entry.key;
}

/** Returns what a catalog entry's key means, or '' when the backend sends no description. */
export function getKeyEntryDescription(
  entry: FleetFilterKeyEntry | FleetGroupByKeyEntry,
): string {
  if ('description' in entry && entry.description) {
    return entry.description;
  }
  if ('metadata' in entry && entry.metadata?.description) {
    return entry.metadata.description;
  }
  return '';
}

/** Returns the group count for a group-by catalog entry when shown by BFF. */
export function getKeyEntryGroupCount(
  entry: FleetFilterKeyEntry | FleetGroupByKeyEntry,
  mode: KeyPickerMode,
): number | undefined {
  if (mode !== 'groupby' || !('groupCount' in entry)) {
    return undefined;
  }
  return entry.groupCount?.shown?.count;
}

/** Whether a catalog row is inert (an already-applied group-by key cannot be picked again). */
export function isKeyEntryInert(
  entry: FleetFilterKeyEntry | FleetGroupByKeyEntry,
  mode: KeyPickerMode,
): boolean {
  return Boolean(entry.applied) && mode === 'groupby';
}
