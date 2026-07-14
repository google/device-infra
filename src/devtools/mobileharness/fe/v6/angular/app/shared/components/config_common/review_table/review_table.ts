import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input, OnInit} from '@angular/core';
import {MatTableModule} from '@angular/material/table';

import {DeviceDimension} from '../../../../core/models/device_config_models';
import {
  DeviceDiscoverySettings,
  HostProperty,
  ManekiSpec,
} from '../../../../core/models/host_config_models';
import {SafeHtmlPipe} from '../../../pipes/safe_html_pipe';

/** Represents an SSH device configuration. */
export type SshDevice = DeviceDiscoverySettings['overSshDevices'][number];

/** Base row interface for review table. */
export interface ReviewTableBaseRow {
  type: 'title' | 'data';
  feature: string;
}

/** Row representing a simple string or number configuration. */
export interface ReviewTableStringRow extends ReviewTableBaseRow {
  value?: string | number;
  valueType?: 'string';
}

/** Row representing a list of strings configuration. */
export interface ReviewTableStringListRow extends ReviewTableBaseRow {
  value?: string[];
  valueType: 'string-list';
}

/** Row representing device dimensions configuration. */
export interface ReviewTableDimensionsRow extends ReviewTableBaseRow {
  value?: DeviceDimension[];
  valueType: 'dimensions';
}

/** Row representing a list of SSH devices configuration. */
export interface ReviewTableSshDevicesRow extends ReviewTableBaseRow {
  value?: SshDevice[];
  valueType: 'ssh-devices';
}

/** Row representing Maneki specs configuration. */
export interface ReviewTableManekiSpecsRow extends ReviewTableBaseRow {
  value?: ManekiSpec[];
  valueType: 'maneki-specs';
}

/** Row representing host properties configuration. */
export interface ReviewTableHostPropertiesRow extends ReviewTableBaseRow {
  value?: HostProperty[];
  valueType: 'host-properties';
}

/**
 * Represents a single row in the review table.
 */
export type ReviewTableRow =
  | ReviewTableStringRow
  | ReviewTableStringListRow
  | ReviewTableDimensionsRow
  | ReviewTableSshDevicesRow
  | ReviewTableManekiSpecsRow
  | ReviewTableHostPropertiesRow;

/**
 * Component for displaying the review table of a configuration.
 *
 * It is used to display the review table of a configuration.
 */
@Component({
  selector: 'app-review-table',
  standalone: true,
  templateUrl: './review_table.ng.html',
  styleUrl: './review_table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatTableModule, SafeHtmlPipe],
})
export class ReviewTable implements OnInit {
  @Input() tableTitle = '';
  @Input() description = '';
  @Input() dataSource: ReviewTableRow[] = [];
  displayedColumns = ['feature', 'value'];

  ngOnInit() {}

  isTitleRow(index: number, row: ReviewTableRow): boolean {
    return row.type === 'title';
  }
  isDataRow(index: number, row: ReviewTableRow): boolean {
    return row.type === 'data';
  }
}
