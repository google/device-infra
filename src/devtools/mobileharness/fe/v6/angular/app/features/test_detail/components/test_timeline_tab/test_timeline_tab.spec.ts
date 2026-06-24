import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {TimeBreakdown} from '../../../../core/models/test_overview';
import {TestTimelineTab} from './test_timeline_tab';

@Component({
  standalone: true,
  imports: [TestTimelineTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-test-timeline-tab [timingBreakdown]="timingBreakdown"></app-test-timeline-tab>`,
})
class TestHostComponent {
  timingBreakdown!: TimeBreakdown;
}

describe('TestTimelineTab Component', () => {
  const mockTiming: TimeBreakdown = {
    createTime: '2026-06-08T03:00:00Z',
    stages: [
      {name: 'Step 1', startTime: '2026-06-08T03:00:00Z', endTime: '2026-06-08T03:00:10Z'},
      {name: 'Step 2', startTime: '2026-06-08T03:00:10Z', endTime: '2026-06-08T03:00:35Z'},
      {name: 'Step 3', startTime: '2026-06-08T03:00:35Z', endTime: '2026-06-08T03:00:40Z'},
    ],
  };

  let fixture: ComponentFixture<TestHostComponent>;
  let component: TestTimelineTab;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        TestTimelineTab,
        TestHostComponent,
        NoopAnimationsModule,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.componentInstance.timingBreakdown = mockTiming;
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(TestTimelineTab),
    ).componentInstance;
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should compute timeline events with correct offsets and durations', () => {
    const events = component.timelineEvents();
    expect(events).toEqual([
      {name: 'Step 1', offset: 0, duration: 10, inProgress: false},
      {name: 'Step 2', offset: 10, duration: 25, inProgress: false},
      {name: 'Step 3', offset: 35, duration: 5, inProgress: false},
    ]);
  });

  it('should calculate total duration of timeline events correctly', () => {
    const duration = component.getTimelineTotalDuration(component.timelineEvents());
    expect(duration).toBe(40);
  });
});
