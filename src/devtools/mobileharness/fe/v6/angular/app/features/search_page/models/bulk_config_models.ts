/**
 * @fileoverview Models and state contracts for bulk device configuration wizard dialogs (Wi-Fi and dimensions).
 */

import {Signal} from '@angular/core';
import {Observable} from 'rxjs';

import {
  ConfigurableDimension,
  DeviceTarget,
  DimensionList,
} from '../../../core/models/device_config_models';

/** Input data structure passed into bulk device configuration dialogs. */
export interface BulkConfigDialogData {
  devices: DeviceTarget[];
  dimension?: ConfigurableDimension;
  dimensionList?: DimensionList;
}

/** Standard result returned when a bulk device configuration dialog closes. */
export interface BulkConfigDialogResult {
  updated: boolean;
}

/** Consolidated counts and formatted labels for the bulk configuration review step. */
export interface BulkConfigReviewSummary {
  willUpdateCount: number;
  willUpdateLabel: string;
  alreadyMatchCount: number;
  alreadyMatchLabel: string;
  readOnlyCount: number;
  readOnlyLabel: string;
}

/** Per-device failure entry from a batch configuration update. */
export interface BulkApplyError {
  deviceId: string;
  reason: string;
}

/** Cancellable, retryable async task tracker returned by `useInlineTask`. */
export interface BulkInlineTask {
  readonly isLoading: Signal<boolean>;
  readonly hasLoaded: Signal<boolean>;
  readonly error: Signal<string | null>;
  run: <TResponse>(
    createRequest: () => Observable<TResponse>,
    onSuccess: (res: TResponse) => void,
    fallbackErrorMessage?: string,
  ) => void;
  retry: () => void;
  cancel: () => void;
}

/** Discriminated union representing the mutually exclusive states of the batch apply mutation. */
export type BulkApplyState =
  | {readonly status: 'idle'}
  | {readonly status: 'applying'}
  | {readonly status: 'rpc_error'; readonly message: string}
  | {readonly status: 'success'}
  | {readonly status: 'errors'; readonly errors: readonly BulkApplyError[]};

/** Consolidated dimension configuration state for the active Fleet results view. */
export interface BulkDimensionConfigState {
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly enabled: boolean;
  readonly dimensions: readonly ConfigurableDimension[];
  readonly allowCustom: boolean;
}

/** Configuration callbacks for binding `useBulkConfigActions` to a device table view. */
export interface BulkConfigActionsOptions {
  /** Reactive predicate indicating whether the active view is for devices on the deployment's own fleet. */
  readonly isOwnFleetDevices: () => boolean;
  /** Reactive predicate indicating whether at least one device is currently selected. */
  readonly hasSelection: () => boolean;
  /** Extracts the currently selected `DeviceTarget` items when launching a bulk config dialog. */
  readonly getSelectedTargets: () => DeviceTarget[];
  /** Callback invoked when a bulk configuration dialog closes with `{updated: true}`. */
  readonly onUpdated: () => void;
}
