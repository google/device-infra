import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import {MatTableModule} from '@angular/material/table';
import {GoogleChartComponent} from '../google_chart/google_chart';

type PieChartOptions = google.visualization.PieChartOptions;

/** Interface for breakdown chart data. */
export interface BreakdownChart {
  title: string;
  data: google.visualization.DataTable;
  options: PieChartOptions;
}

/** Interface for breakdown table data. */
export interface TableItem {
  label: string;
  value?: number;
  count?: number;
  percent?: number;
  color?: string;
  type: 'total' | 'summary' | 'detail';
}

/** Component for displaying statistic breakdown in table or chart view. */
@Component({
  selector: 'app-statistic-breakdown',
  standalone: true,
  imports: [CommonModule, MatTableModule, GoogleChartComponent],
  templateUrl: './statistic_breakdown.ng.html',
  styleUrl: './statistic_breakdown.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatisticBreakdown implements OnChanges {
  @Input() title = '';
  @Input() selectedPeriodStr = '';
  @Input() tableData: TableItem[] = [];
  @Input() tableColumns: string[] = [];
  @Input() charts: BreakdownChart[] = [];

  view: 'table' | 'chart' = 'table';
  filteredData: TableItem[] = [];

  ngOnChanges(changes: SimpleChanges) {
    if (changes['tableData'] || changes['tableColumns']) {
      this.updateFilteredData();
    }
  }

  private updateFilteredData() {
    this.filteredData = this.tableData.filter((row) => {
      if (this.tableColumns.includes('count')) {
        return row.count && row.count !== 0;
      }
      return row.value && row.value !== 0;
    });
  }

  toggleView(view: 'table' | 'chart') {
    if (this.view === view) return;

    this.view = view;
  }
}
