import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  effect,
  input,
  signal,
} from '@angular/core';
import {load} from '@google-web-components/google-chart/loader';

type ChartRowValue = string | number | boolean | Date | {v: number; f: string};

/**
 * A wrapper component for Google Charts.
 */
@Component({
  selector: 'app-google-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './google_chart.ng.html',
  styleUrl: './google_chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoogleChartComponent implements OnInit, OnDestroy {
  readonly type = input.required<'ColumnChart' | 'PieChart'>();
  readonly dataTable = input<google.visualization.DataTable | undefined>();
  readonly columns = input<Array<{type: string; label: string}> | undefined>();
  readonly rows = input<Array<Array<ChartRowValue | null | undefined>> | undefined>();
  readonly options = input<
    | google.visualization.ColumnChartOptions
    | google.visualization.PieChartOptions
  >({});

  @ViewChild('chartContainer', {static: true}) chartContainer!: ElementRef;

  private chart:
    | google.visualization.ColumnChart
    | google.visualization.PieChart
    | undefined;
  private resizeObserver: ResizeObserver | undefined;
  private readonly libLoaded = signal(false);

  constructor() {
    load().then(() => {
      google.charts.setOnLoadCallback(() => {
        this.libLoaded.set(true);
      });
    });

    effect(() => {
      const loaded = this.libLoaded();
      const type = this.type();
      const dt = this.dataTable();
      const cols = this.columns();
      const rows = this.rows();
      const opts = this.options();

      if (loaded && (dt || (cols && rows))) {
        this.drawChart(type, dt, cols, rows, opts);
      }
    });
  }

  ngOnInit() {
    this.resizeObserver = new ResizeObserver(() => {
      if (this.libLoaded() && this.chart) {
        // Re-draw with current state
        const dt = this.dataTable();
        const cols = this.columns();
        const rows = this.rows();
        const opts = this.options();
        if (dt || (cols && rows)) {
          this.drawChart(this.type(), dt, cols, rows, opts);
        }
      }
    });
    this.resizeObserver.observe(this.chartContainer.nativeElement);
  }

  ngOnDestroy() {
    this.resizeObserver?.disconnect();
    this.chart?.clearChart();
  }

  private drawChart(
    type: string,
    dataTable: google.visualization.DataTable | undefined,
    columns: Array<{type: string; label: string}> | undefined,
    rows: Array<Array<ChartRowValue | null | undefined>> | undefined,
    options:
      | google.visualization.ColumnChartOptions
      | google.visualization.PieChartOptions,
  ) {
    if (!this.chartContainer?.nativeElement || !google?.visualization) return;

    let data: google.visualization.DataTable;

    if (dataTable) {
      data = dataTable;
    } else if (columns && rows) {
      data = new google.visualization.DataTable();
      columns.forEach((col) => {
        data.addColumn(col.type, col.label);
      });
      // The addRows method signature in some typings might not strictly match our ChartRowValue[][]
      // but at runtime it handles these types.
      data.addRows(rows as Array<Array<ChartRowValue | null | undefined>>);
    } else {
      return;
    }

    if (!this.chart) {
      if (type === 'ColumnChart') {
        this.chart = new google.visualization.ColumnChart(
          this.chartContainer.nativeElement,
        );
      } else if (type === 'PieChart') {
        this.chart = new google.visualization.PieChart(
          this.chartContainer.nativeElement,
        );
      }
    }

    if (this.chart) {
      if (type === 'ColumnChart') {
        (this.chart as google.visualization.ColumnChart).draw(
          data,
          options as google.visualization.ColumnChartOptions,
        );
      } else {
        (this.chart as google.visualization.PieChart).draw(
          data,
          options as google.visualization.PieChartOptions,
        );
      }
    }
  }
}
