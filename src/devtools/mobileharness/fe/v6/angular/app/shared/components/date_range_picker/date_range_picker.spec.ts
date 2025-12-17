import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {DateRangePicker} from './date_range_picker';

describe('DateRangePicker Component', () => {
  let fixture: ComponentFixture<DateRangePicker>;
  let component: DateRangePicker;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        DateRangePicker,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DateRangePicker);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
