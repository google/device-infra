/**
 * @fileoverview Composable store slice managing the FilterValuePicker CDK overlay state and Quick-Filter/Group-By staging.
 */

import {CdkOverlayOrigin} from '@angular/cdk/overlay';
import {computed, signal} from '@angular/core';

import {
  createInitialValuePickerState,
  FilterChip,
  FilterKeyMetadata,
  FocusChipType,
  MAX_GROUP_BY_KEYS,
  PendingFilterItem,
  PendingFilterOpenRequest,
  ValuePickerConfig,
  ValuePickerOverlaySliceOptions,
  ValuePickerState,
} from '../../models';
import {isSameFilterChip, normalizeKey} from '../../utils';

/** Composable managing the ValuePicker CDK overlay popover and Quick-Filter/Group-By staging state. */
export function useValuePickerOverlay(opts: ValuePickerOverlaySliceOptions) {
  const showValuePicker = signal<boolean>(false);
  const pickerAnchor = signal<CdkOverlayOrigin | HTMLElement | null>(null);
  const pickerConfig = signal<ValuePickerConfig | null>(null);
  const pickerState = signal<ValuePickerState>(createInitialValuePickerState());
  const pickerKey = computed(() => pickerConfig()?.key || '');
  const pickerTitle = computed(() => pickerConfig()?.title || '');

  const pendingFilter = signal<PendingFilterItem | null>(null);
  const focusChipKey = signal<string | null>(null);
  const focusChipType = signal<FocusChipType | null>(null);
  const pendingFilterOpenRequest = signal<PendingFilterOpenRequest | null>(
    null,
  );

  const pendingFilterKey = computed<string | null>(() => {
    const pf = pendingFilter();
    const pk = pf?.key || (showValuePicker() ? pickerKey() : null);
    if (!pk) return null;
    const hasApplied = opts
      .filterChips()
      .some((c) => isSameFilterChip(c, {key: pk, isGroupBy: false}));
    return hasApplied ? null : pk;
  });

  const pendingFilterTitle = computed<string>(
    () =>
      pendingFilter()?.displayName || pickerTitle() || pendingFilterKey() || '',
  );

  /** Returns true if the FilterValuePicker is currently open for the given filter chip. */
  const isChipPickerActive = (chip: FilterChip): boolean => {
    const currentKey = pickerKey();
    if (!showValuePicker() || chip.isGroupBy || !currentKey) return false;
    return isSameFilterChip(chip, {key: currentKey, isGroupBy: false});
  };

  /** Returns true if the FilterValuePicker is currently open for the given key (and optional title). */
  const isKeyPickerActive = (key: string, title?: string): boolean => {
    if (!showValuePicker()) return false;
    const currentKey = normalizeKey(pickerKey());
    if (!currentKey || currentKey !== normalizeKey(key)) return false;
    return !(
      title &&
      pickerTitle() &&
      pickerTitle().toLowerCase() !== title.toLowerCase()
    );
  };

  /** Closes the FilterValuePicker overlay and clears pending filter and chip focus state. */
  const closeValuePicker = (): void => {
    if (
      !showValuePicker() &&
      !pickerConfig() &&
      !pickerAnchor() &&
      !pendingFilter() &&
      !pendingFilterOpenRequest() &&
      !focusChipKey() &&
      !focusChipType()
    ) {
      return;
    }
    showValuePicker.set(false);
    pickerConfig.set(null);
    pickerState.set(createInitialValuePickerState());
    pickerAnchor.set(null);
    pendingFilter.set(null);
    pendingFilterOpenRequest.set(null);
    focusChipKey.set(null);
    focusChipType.set(null);
  };

  /** Stages a pending filter chip (if not already applied), opens the suggestions popover, and requests ValuePicker launch. */
  const openQuickFilter = (
    key: string,
    title?: string,
    metadata?: FilterKeyMetadata,
    stagedValues?: string[],
  ): void => {
    closeValuePicker();
    const activeChip = opts
      .activeChips()
      .find(
        (c) => !c.isGroupBy && isSameFilterChip(c, {key, isGroupBy: false}),
      );
    pendingFilter.set(
      activeChip ? null : {key, displayName: title || key, metadata},
    );
    focusChipKey.set(key);
    focusChipType.set('filter');
    opts.showSuggestions.set(true);
    pendingFilterOpenRequest.set({key, title, metadata, stagedValues});
  };

  /** Adds or focuses a group-by chip in the suggestions popover. */
  const openQuickGroupBy = (key: string, displayName?: string): void => {
    const isAlreadyActive = opts.groupByKeys().includes(key);
    if (!isAlreadyActive) {
      if (opts.groupByKeys().length >= MAX_GROUP_BY_KEYS) return;
      opts.toggleGroupBy(key, displayName);
    }
    closeValuePicker();
    focusChipKey.set(key);
    focusChipType.set('groupby');
    opts.showSuggestions.set(isAlreadyActive || !opts.autoCollapseAfterApply());
  };

  /** Opens or toggles the FilterValuePicker overlay anchored to the specified element. */
  const openValuePicker = (
    key: string,
    anchor?: CdkOverlayOrigin | HTMLElement | null,
    title?: string,
    metadata?: FilterKeyMetadata,
    stagedValues?: string[],
    keepSuggestionsOpen = false,
  ): void => {
    if (!keepSuggestionsOpen) {
      opts.showSuggestions.set(false);
    }
    if (isKeyPickerActive(key, title)) {
      closeValuePicker();
      return;
    }
    if (anchor) {
      pickerAnchor.set(anchor);
    }
    const activeChip = opts
      .activeChips()
      .find((c) => isSameFilterChip(c, {key, isGroupBy: false}));
    pickerConfig.set(opts.buildPickerConfig(key, title, metadata));
    pickerState.set(
      opts.buildInitialPickerState(key, activeChip, metadata, stagedValues),
    );
    showValuePicker.set(true);
  };

  return {
    showValuePicker,
    pickerAnchor,
    pickerConfig,
    pickerState,
    pickerKey,
    pickerTitle,
    pendingFilter,
    focusChipKey,
    focusChipType,
    pendingFilterOpenRequest,
    pendingFilterKey,
    pendingFilterTitle,
    isChipPickerActive,
    isKeyPickerActive,
    openQuickFilter,
    openQuickGroupBy,
    openValuePicker,
    closeValuePicker,
  };
}
