import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {StatisticBreakdown} from './statistic_breakdown';

describe('StatisticBreakdown Component', () => {
  let fixture: ComponentFixture<StatisticBreakdown>;
  let component: StatisticBreakdown;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        StatisticBreakdown,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(StatisticBreakdown);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
