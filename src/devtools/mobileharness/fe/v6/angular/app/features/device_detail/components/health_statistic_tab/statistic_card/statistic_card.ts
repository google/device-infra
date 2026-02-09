import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {DateRangePicker} from '../../../../../shared/components/date_range_picker/date_range_picker';
import {InfoCard} from '../../../../../shared/components/info_card/info_card';

/**
 * A card component that displays statistics with a date range picker.
 */
@Component({
  selector: 'app-statistic-card',
  standalone: true,
  imports: [CommonModule, InfoCard, DateRangePicker, MatProgressBarModule],
  templateUrl: './statistic_card.ng.html',
  styleUrl: './statistic_card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatisticCard {
  @Input({required: true}) title!: string;
  @Input({required: true}) id!: string;
  @Input() loading = false;
  @Output() readonly dateRangeChange = new EventEmitter<{
    start: Date;
    end: Date;
  }>();
}
