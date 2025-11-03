import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input, OnInit} from '@angular/core';
import {MatTableModule} from '@angular/material/table';

import {SafeHtmlPipe} from '../../../pipes/safe_html_pipe';

/**
 * Represents a single row in the review table.
 */
export interface ReviewTableRow {
  type: 'title'|'data';
  feature: string;
  value?: string|number;
}

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
  @Input() title = '';
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
