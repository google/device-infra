import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {DateAdapter, MatNativeDateModule} from '@angular/material/core';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatMenuModule, MatMenuTrigger} from '@angular/material/menu';

import {PdtDateAdapter} from '../../utils/pdt_date_adapter';

/**
 * A custom date range picker component that allows users to select a date
 * range using quick selections (Last 7/30/90 days) or a custom date range
 * with start and end date pickers.
 */
@Component({
  selector: 'app-date-range-picker',
  templateUrl: './date_range_picker.ng.html',
  styleUrls: ['./date_range_picker.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatNativeDateModule,
    ReactiveFormsModule,
  ],
  providers: [{provide: DateAdapter, useClass: PdtDateAdapter}],
})
export class DateRangePicker implements OnInit {
  @ViewChild(MatMenuTrigger) menuTrigger?: MatMenuTrigger;
  @Output() readonly dateRangeChange = new EventEmitter<{
    start: Date;
    end: Date;
  }>();

  private readonly dateAdapter = inject(DateAdapter<Date>);

  dateRangeValidator: ValidatorFn = (
    control: AbstractControl,
  ): ValidationErrors | null => {
    const start = control.get('start')?.value;
    const end = control.get('end')?.value;

    if (start && end) {
      // Ignore time components for validation
      start.setHours(0, 0, 0, 0);
      end.setHours(0, 0, 0, 0);

      if (start > end) {
        return {'endDateBeforeStartDate': true};
      }

      const diffTime = end.getTime() - start.getTime();
      const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));
      // Inclusive days count, e.g. start=end -> 1 day
      const duration = diffDays + 1;

      if (duration < 3) {
        return {'minDateRange': true};
      }

      if (duration > 90) {
        return {'maxDateRange': true};
      }
    }

    return null;
  };

  range = new FormGroup(
    {
      start: new FormControl<Date | null>(null),
      end: new FormControl<Date | null>(null),
    },
    {validators: this.dateRangeValidator},
  );

  today = this.dateAdapter.today();
  displayedRange: {start: Date | null; end: Date | null} = {
    start: null,
    end: null,
  };

  ngOnInit() {
    this.selectLastNDays(7);
    this.displayedRange = {
      start: this.range.value.start!,
      end: this.range.value.end!,
    };
  }

  updateToday() {
    this.today = this.dateAdapter.today();
  }

  selectLastNDays(days: number) {
    this.updateToday();
    const endDate = this.dateAdapter.today();
    const startDate = this.dateAdapter.today();
    // days - 1 because if I want 7 days including today, I subtract 6 days.
    // e.g. today is 10th. 7 days: 4th, 5th, 6th, 7th, 8th, 9th, 10th.
    // 10 - 6 = 4.
    this.dateAdapter.addCalendarDays(startDate, -days + 1);
    startDate.setDate(endDate.getDate() - days + 1);
    this.range.setValue({start: startDate, end: endDate});
    this.apply();
  }

  apply() {
    if (this.range.valid) {
      const start = this.range.value.start!;
      const end = this.range.value.end!;
      this.displayedRange = {start, end};
      this.dateRangeChange.emit({start, end});
      this.menuTrigger?.closeMenu();
    }
  }
}
