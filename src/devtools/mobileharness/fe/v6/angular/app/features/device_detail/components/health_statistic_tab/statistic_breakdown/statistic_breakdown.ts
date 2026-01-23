import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  QueryList,
  SimpleChanges,
  ViewChildren,
} from '@angular/core';
import {MatTableModule} from '@angular/material/table';

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
  imports: [CommonModule, MatTableModule],
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

  @ViewChildren('chartContainer') chartElements!: QueryList<ElementRef>;

  ngOnChanges(changes: SimpleChanges) {
    if (changes['tableData'] || changes['tableColumns']) {
      this.updateFilteredData();
    }
    if (changes['charts'] && this.view === 'chart') {
      // Allow view to update before drawing
      setTimeout(() => {
        this.drawCharts();
      }, 0);
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
    if (view === 'chart') {
      setTimeout(() => {
        this.drawCharts();
      }, 0);
    }
  }

  private drawCharts() {
    if (!this.chartElements || this.charts.length === 0) {
      return;
    }

    this.chartElements.forEach((element, index) => {
      const chartConfig = this.charts[index];
      if (chartConfig && element.nativeElement) {
        new google.visualization.PieChart(element.nativeElement).draw(
          chartConfig.data,
          chartConfig.options,
        );
      }
    });
  }
}
