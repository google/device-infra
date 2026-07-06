import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {TimeBreakdown} from '../../../core/models/timeline';
import {TimelineChart} from './timeline_chart';

@Component({
  standalone: true,
  imports: [TimelineChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-timeline-chart [timingBreakdown]="timingBreakdown" theme="green"></app-timeline-chart>`,
})
class TestHostComponent {
  timingBreakdown!: TimeBreakdown;
}

describe('TimelineChart Component', () => {
  const mockTiming: TimeBreakdown = {
    createTime: '2026-06-08T03:00:00Z',
    stages: [
      {
        name: 'Step 1',
        startTime: '2026-06-08T03:00:00Z',
        endTime: '2026-06-08T03:00:10Z',
      },
      {
        name: 'Step 2',
        startTime: '2026-06-08T03:00:10Z',
        endTime: '2026-06-08T03:00:35Z',
      },
      {
        name: 'Step 3',
        startTime: '2026-06-08T03:00:35Z',
        endTime: '2026-06-08T03:00:40Z',
      },
    ],
  };

  let fixture: ComponentFixture<TestHostComponent>;
  let component: TimelineChart;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TimelineChart, TestHostComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.componentInstance.timingBreakdown = mockTiming;
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(TimelineChart),
    ).componentInstance;
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should compute stage rows and events with correct offsets and durations', () => {
    const rows = component.stageRows();
    expect(rows).toEqual([
      {
        stageName: 'Step 1',
        rowCount: 1,
        tracks: [0],
        events: [
          {
            label: '',
            offset: 0,
            duration: 10,
            inProgress: false,
            trackIndex: 0,
            startTime: '2026-06-08T03:00:00Z',
            endTime: '2026-06-08T03:00:10Z',
          },
        ],
      },
      {
        stageName: 'Step 2',
        rowCount: 1,
        tracks: [0],
        events: [
          {
            label: '',
            offset: 10,
            duration: 25,
            inProgress: false,
            trackIndex: 0,
            startTime: '2026-06-08T03:00:10Z',
            endTime: '2026-06-08T03:00:35Z',
          },
        ],
      },
      {
        stageName: 'Step 3',
        rowCount: 1,
        tracks: [0],
        events: [
          {
            label: '',
            offset: 35,
            duration: 5,
            inProgress: false,
            trackIndex: 0,
            startTime: '2026-06-08T03:00:35Z',
            endTime: '2026-06-08T03:00:40Z',
          },
        ],
      },
    ]);
  });

  it('should calculate total duration correctly', () => {
    expect(component.totalDuration()).toBe(40);
  });
});
