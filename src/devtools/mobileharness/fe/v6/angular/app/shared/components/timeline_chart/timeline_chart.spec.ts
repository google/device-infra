import {ChangeDetectionStrategy, Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {
  JobTestLane,
  TestTimelineSubTestRow,
  TimeBreakdown,
} from '../../../core/models/timeline';
import {TimelineChart} from './timeline_chart';

@Component({
  standalone: true,
  imports: [TimelineChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-timeline-chart
      [timingBreakdown]="timingBreakdown()"
      [jobId]="jobId()"
      [loading]="loading()"
      [errorMessage]="errorMessage()"
      [emptyMessage]="emptyMessage()"
    ></app-timeline-chart>
  `,
})
class TestHostComponent {
  readonly timingBreakdown = signal<TimeBreakdown | undefined>(undefined);
  readonly jobId = signal<string>('job-456');
  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly emptyMessage = signal<string>('No timing was recorded.');
}

describe('TimelineChart Component', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let component: TimelineChart;

  const mockTestBreakdown: TimeBreakdown = {
    summaryLines: [
      'Failed after 40.0s, during AndroidInstrumentation (+34.0s from start).',
      '10.0s waiting to start, 6.0s setup, 18.0s test run, 6.0s teardown.',
    ],
    originTime: '2026-06-08T03:00:00Z',
    finishTime: '2026-06-08T03:00:40Z',
    axis: {
      tickStepPercent: 25,
      ticks: [
        {
          leftPercent: 0,
          label: '0s',
          alignment: 'TIMELINE_TICK_ALIGNMENT_START',
        },
        {
          leftPercent: 50,
          label: '+20s',
          alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
        },
        {
          leftPercent: 100,
          label: '+40s',
          alignment: 'TIMELINE_TICK_ALIGNMENT_END',
        },
      ],
    },
    splitSegments: [
      {
        focusKey: 'wait',
        label: 'Waiting to start',
        fill: 'TIMELINE_FILL_WAIT',
        flexWeight: 10000,
        durationText: '10.0s',
        shareText: '25%',
      },
      {
        focusKey: 'setup',
        label: 'Setup',
        fill: 'TIMELINE_FILL_SETUP',
        flexWeight: 6000,
        durationText: '6.0s',
        shareText: '15%',
        tooltipFacts: [{label: 'Steps', value: '2'}],
        tooltipHint: 'Preparing test inputs and decorators.',
      },
      {
        focusKey: 'test',
        label: 'Test run',
        fill: 'TIMELINE_FILL_TEST',
        flexWeight: 18000,
        durationText: '18.0s',
        shareText: '45%',
      },
      {
        focusKey: 'teardown',
        label: 'Teardown',
        fill: 'TIMELINE_FILL_TEARDOWN',
        flexWeight: 6000,
        durationText: '6.0s',
        shareText: '15%',
      },
    ],
    testTimeline: {
      phases: [
        {
          phaseId: 'wait',
          focusKey: 'wait',
          label: 'Waiting to start',
          fill: 'TIMELINE_FILL_WAIT',
          bracketLeftPercent: 0,
          bracketWidthPercent: 25,
          durationText: '10.0s',
          shareText: '25%',
          rows: [
            {
              label: 'Waiting for a device',
              focusKey: 'wait',
              fill: 'TIMELINE_FILL_WAIT',
              durationText: '10.0s',
              shareText: '25%',
              bars: [
                {
                  startTime: '2026-06-08T03:00:00Z',
                  endTime: '2026-06-08T03:00:10Z',
                  leftPercent: 0,
                  widthPercent: 25,
                  startOffsetText: '+0ms',
                  endOffsetText: '+10.0s',
                  durationText: '10.0s',
                },
              ],
            },
          ],
        },
        {
          phaseId: 'setup',
          focusKey: 'setup',
          label: 'Setup',
          fill: 'TIMELINE_FILL_SETUP',
          stepCountText: '2 steps',
          bracketLeftPercent: 25,
          bracketWidthPercent: 15,
          durationText: '6.0s',
          shareText: '15%',
          rows: [
            {
              label: 'Sending files to the lab',
              detail: '2 chunks',
              tooltipHint: 'Transfers test artifacts to the lab.',
              focusKey: 'setup',
              fill: 'TIMELINE_FILL_SETUP',
              durationText: '4.0s',
              shareText: '10%',
              bars: [
                {
                  startTime: '2026-06-08T03:00:10Z',
                  endTime: '2026-06-08T03:00:12Z',
                  leftPercent: 25,
                  widthPercent: 5,
                  startOffsetText: '+10.0s',
                  endOffsetText: '+12.0s',
                  durationText: '2.0s',
                },
                {
                  startTime: '2026-06-08T03:00:12Z',
                  endTime: '2026-06-08T03:00:14Z',
                  leftPercent: 30,
                  widthPercent: 5,
                  startOffsetText: '+12.0s',
                  endOffsetText: '+14.0s',
                  durationText: '2.0s',
                },
              ],
            },
            {
              label: 'Setup AndroidLogCatDecorator',
              focusKey: 'setup',
              pairKey: 'AndroidLogCatDecorator',
              fill: 'TIMELINE_FILL_SETUP',
              durationText: '2.0s',
              shareText: '5.0%',
              bars: [
                {
                  startTime: '2026-06-08T03:00:14Z',
                  endTime: '2026-06-08T03:00:16Z',
                  leftPercent: 35,
                  widthPercent: 5,
                  startOffsetText: '+14.0s',
                  endOffsetText: '+16.0s',
                  durationText: '2.0s',
                },
              ],
            },
          ],
        },
        {
          phaseId: 'test',
          focusKey: 'test',
          label: 'Test run',
          fill: 'TIMELINE_FILL_TEST',
          bracketLeftPercent: 40,
          bracketWidthPercent: 45,
          durationText: '18.0s',
          shareText: '45%',
          rows: [
            {
              label: 'AndroidInstrumentation',
              focusKey: 'test',
              fill: 'TIMELINE_FILL_TEST',
              durationText: '18.0s',
              shareText: '45%',
              isEndedStep: true,
              resultTone: 'TIMELINE_RESULT_TONE_FAIL',
              resultLabel: 'Fail',
              endMarkLeftPercent: 85,
              errorMessage: 'AssertionError: expected button to be visible',
              bars: [
                {
                  startTime: '2026-06-08T03:00:16Z',
                  endTime: '2026-06-08T03:00:34Z',
                  leftPercent: 40,
                  widthPercent: 45,
                  startOffsetText: '+16.0s',
                  endOffsetText: '+34.0s',
                  durationText: '18.0s',
                },
              ],
              subTests: Array.from(
                {length: 14},
                (_, idx): TestTimelineSubTestRow => ({
                  name: `case_${idx + 1}`,
                  resultTone:
                    idx === 13
                      ? 'TIMELINE_RESULT_TONE_FAIL'
                      : 'TIMELINE_RESULT_TONE_PASS',
                  resultLabel: idx === 13 ? 'Fail' : 'Pass',
                  durationText: '1.0s',
                  bar: {
                    startTime: '2026-06-08T03:00:16Z',
                    endTime: '2026-06-08T03:00:17Z',
                    leftPercent: 40 + idx * 2,
                    widthPercent: 2,
                  },
                }),
              ),
            },
          ],
        },
        {
          phaseId: 'teardown',
          focusKey: 'teardown',
          label: 'Teardown',
          fill: 'TIMELINE_FILL_TEARDOWN',
          bracketLeftPercent: 85,
          bracketWidthPercent: 15,
          durationText: '6.0s',
          shareText: '15%',
          rows: [
            {
              label: 'Teardown AndroidLogCatDecorator',
              focusKey: 'teardown',
              pairKey: 'AndroidLogCatDecorator',
              fill: 'TIMELINE_FILL_TEARDOWN',
              durationText: '6.0s',
              shareText: '15%',
              bars: [
                {
                  startTime: '2026-06-08T03:00:34Z',
                  endTime: '2026-06-08T03:00:40Z',
                  leftPercent: 85,
                  widthPercent: 15,
                  startOffsetText: '+34.0s',
                  endOffsetText: '+40.0s',
                  durationText: '6.0s',
                },
              ],
            },
          ],
        },
      ],
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TimelineChart, TestHostComponent, NoopAnimationsModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(TimelineChart),
    ).componentInstance;
  });

  it('renders loading, error, and custom empty message states', () => {
    const el = fixture.nativeElement as HTMLElement;

    host.loading.set(true);
    fixture.detectChanges();
    expect(el.querySelector('.tl-empty')?.textContent?.trim()).toBe(
      'Loading timeline...',
    );

    host.loading.set(false);
    host.errorMessage.set('Failed to load timeline.');
    fixture.detectChanges();
    expect(el.querySelector('.tl-empty')?.textContent?.trim()).toBe(
      'Failed to load timeline.',
    );

    host.errorMessage.set(null);
    host.emptyMessage.set('No timing was recorded for this test.');
    host.timingBreakdown.set({});
    fixture.detectChanges();
    expect(el.querySelector('.tl-empty')?.textContent?.trim()).toBe(
      'No timing was recorded for this test.',
    );
  });

  it('renders test timeline summary, split bar, ended step error, and multi-bar and sub-test tooltips', () => {
    host.timingBreakdown.set(mockTestBreakdown);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.tl-headline')?.textContent).toContain(
      'Failed after 40.0s, during AndroidInstrumentation',
    );
    expect(el.querySelector('.tl-sub')?.textContent).toContain(
      '10.0s waiting to start',
    );
    expect(el.querySelectorAll('.tl-split-seg').length).toBe(4);
    expect(el.querySelector('.tl-error')?.textContent?.trim()).toBe(
      'AssertionError: expected button to be visible',
    );
    expect(el.querySelector('.tl-endmark--fail')).not.toBeNull();

    // Hovering a split segment focuses matching phase and rows and opens tooltip.
    const setupSeg = el.querySelectorAll('.tl-split-seg')[1] as HTMLElement;
    setupSeg.dispatchEvent(
      new MouseEvent('mouseenter', {clientX: 120, clientY: 80}),
    );
    fixture.detectChanges();
    expect(el.querySelector('.tl-card.tl-focusing')).not.toBeNull();
    expect(el.querySelectorAll('.tl-focus-match').length).toBeGreaterThan(0);
    expect(el.querySelector('.tl-tip .tl-tip-title')?.textContent).toBe(
      'Setup',
    );

    setupSeg.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(el.querySelector('.tl-card.tl-focusing')).toBeNull();

    // Hovering a multi-bar step row renders Part 1 and Part 2 tooltip rows and clamps near viewport edge.
    const stepRows = Array.from(
      el.querySelectorAll(
        '.tl-table .tl-row:not(.tl-row--phase):not(.tl-row--sub):not(.tl-row--group)',
      ),
    ) as HTMLElement[];
    const multiBarRow = stepRows.find((r) =>
      r.textContent?.includes('Sending files to the lab'),
    )!;
    multiBarRow.dispatchEvent(
      new MouseEvent('mouseenter', {clientX: 5000, clientY: 5000}),
    );
    fixture.detectChanges();
    const tipText = el.querySelector('.tl-tip')?.textContent ?? '';
    expect(tipText).toContain('Part 1');
    expect(tipText).toContain('Part 2');
    expect(tipText).toContain('Transfers test artifacts to the lab.');

    // Hovering a sub-test row opens the sub-test tooltip.
    const firstSubTestRow = el.querySelector('.tl-row--sub') as HTMLElement;
    firstSubTestRow.dispatchEvent(
      new MouseEvent('mouseenter', {clientX: 150, clientY: 180}),
    );
    fixture.detectChanges();
    expect(el.querySelector('.tl-tip .tl-tip-title')?.textContent).toBe(
      'case_1',
    );

    firstSubTestRow.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(el.querySelector('.tl-tip')).toBeNull();
  });

  it('supports collapsible phases, decorator pairing, sub-test cap, keyboard toggling, and linkedSignal resets', () => {
    host.timingBreakdown.set(mockTestBreakdown);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    // Hovering a decorator setup row pairs with its teardown counterpart via pairKey.
    const stepRows = Array.from(
      el.querySelectorAll(
        '.tl-table .tl-row:not(.tl-row--phase):not(.tl-row--sub):not(.tl-row--group)',
      ),
    ) as HTMLElement[];
    const decoratorSetupRow = stepRows.find((r) =>
      r.textContent?.includes('Setup AndroidLogCatDecorator'),
    )!;
    decoratorSetupRow.dispatchEvent(
      new MouseEvent('mouseenter', {clientX: 200, clientY: 160}),
    );
    fixture.detectChanges();
    expect(el.querySelectorAll('.tl-row--paired').length).toBe(2);

    // Sub-test cap shows 12 initially, then expands to all 14 on Enter keydown and collapses on Space.
    expect(el.querySelectorAll('.tl-row--sub').length).toBe(12);
    const subToggle = Array.from(
      el.querySelectorAll('.tl-row--group:not(.tl-row--phase)'),
    )[0] as HTMLElement;
    expect(subToggle.textContent).toContain('2 more sub-tests');
    subToggle.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter'}));
    fixture.detectChanges();
    expect(el.querySelectorAll('.tl-row--sub').length).toBe(14);
    expect(subToggle.textContent).toContain('Show fewer sub-tests');

    subToggle.dispatchEvent(new KeyboardEvent('keydown', {key: ' '}));
    fixture.detectChanges();
    expect(el.querySelectorAll('.tl-row--sub').length).toBe(12);
    subToggle.click();
    fixture.detectChanges();
    expect(component.expandedSubTestRowKeys().size).toBe(1);

    // Phase header toggles via click, Enter, and Space.
    const setupPhaseHeader = el.querySelectorAll(
      '.tl-row--phase',
    )[1] as HTMLElement;
    expect(setupPhaseHeader.getAttribute('aria-expanded')).toBe('true');
    setupPhaseHeader.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Enter'}),
    );
    fixture.detectChanges();
    expect(setupPhaseHeader.getAttribute('aria-expanded')).toBe('false');
    expect(el.textContent).not.toContain('Sending files to the lab');

    setupPhaseHeader.dispatchEvent(new KeyboardEvent('keydown', {key: ' '}));
    fixture.detectChanges();
    expect(setupPhaseHeader.getAttribute('aria-expanded')).toBe('true');
    setupPhaseHeader.click();
    fixture.detectChanges();
    expect(component.collapsedPhaseIds().size).toBe(1);

    // Updating timingBreakdown resets linkedSignal UI states.
    host.timingBreakdown.set({...mockTestBreakdown});
    fixture.detectChanges();
    expect(component.collapsedPhaseIds().size).toBe(0);
    expect(component.expandedSubTestRowKeys().size).toBe(0);
    expect(component.pairedKey()).toBeNull();
    expect(component.focusedKey()).toBeNull();
    expect(component.activeTooltip()).toBeNull();
  });

  it('renders job timeline with Job setup inner bar, linked and unrecorded lanes, tooltips, and 40-lane cap toggle', () => {
    const lanes: JobTestLane[] = Array.from(
      {length: 42},
      (_, idx): JobTestLane =>
        idx === 1
          ? {
              testId: 'missing-test',
              displayName: 'expiredCase',
              fullName: 'Suite#expiredCase',
              hasRecord: false,
              missingRecordHint: 'MOSS has no record for this test.',
              waitedText: '4.0s',
              ranCell: {unrecorded: {reason: 'Unknown'}},
              bars: [
                {
                  focusKey: 'unknown',
                  fill: 'TIMELINE_FILL_MISSING',
                  leftPercent: 20,
                  widthPercent: 30,
                },
              ],
            }
          : {
              testId: `test-${idx + 1}`,
              displayName: `case_${idx + 1}`,
              fullName: idx === 0 ? '' : `Suite#case_${idx + 1}`,
              hasRecord: true,
              runTag: idx === 2 ? 'Retry' : undefined,
              deviceIds: ['DEV001'],
              resultTone:
                idx === 3
                  ? 'TIMELINE_RESULT_TONE_TIMEOUT'
                  : 'TIMELINE_RESULT_TONE_PASS',
              resultLabel: idx === 3 ? 'Timed out' : 'Passed',
              hasEndMark: idx === 0 || idx === 3,
              endMarkLeftPercent: 40,
              overheadHint:
                idx === 0
                  ? 'Largest setup or teardown step: Setup Foo, 3.0s'
                  : undefined,
              tooltipRows: [{label: 'Test run', value: '12.0s'}],
              waitedText: '2.0s',
              ranCell:
                idx === 3
                  ? {unrecorded: {reason: 'Not started'}}
                  : {recorded: {durationText: '12.0s'}},
              bars:
                idx === 3
                  ? [
                      {
                        focusKey: 'waiting',
                        fill: 'TIMELINE_FILL_WAIT',
                        leftPercent: 10,
                        widthPercent: 30,
                      },
                    ]
                  : [
                      {
                        focusKey: 'waiting',
                        fill: 'TIMELINE_FILL_WAIT',
                        leftPercent: 10,
                        widthPercent: 5,
                      },
                      {
                        focusKey: 'running',
                        fill:
                          idx === 4
                            ? 'TIMELINE_FILL_OPAQUE'
                            : 'TIMELINE_FILL_TEST',
                        leftPercent: 15,
                        widthPercent: 25,
                      },
                    ],
            },
    );

    host.jobId.set('job-999');
    host.timingBreakdown.set({
      summaryLines: ['Ran for 1m 0s and passed.'],
      originTime: '2026-06-08T03:00:00Z',
      finishTime: '2026-06-08T03:01:00Z',
      jobTimeline: {
        totalDurationText: '1m 0s',
        activityBars: [
          {
            focusKey: 'preparing',
            label: 'Preparing',
            fill: 'TIMELINE_FILL_SETUP',
            shareText: '10%',
            interval: {
              startTime: '2026-06-08T03:00:00Z',
              endTime: '2026-06-08T03:00:06Z',
              leftPercent: 0,
              widthPercent: 10,
              durationText: '6.0s',
            },
          },
        ],
        jobSetup: {
          detail: 'Resolving files 4.5s',
          durationText: '6.0s',
          bars: [
            {
              focusKey: 'preparing',
              label: 'Preparing the job',
              fill: 'TIMELINE_FILL_SETUP',
              interval: {
                leftPercent: 0,
                widthPercent: 10,
                durationText: '6.0s',
              },
            },
            {
              focusKey: 'preparing',
              label: 'Resolving files',
              detail: 'Part of preparing the job',
              fill: 'TIMELINE_FILL_RESOLVE',
              isInner: true,
              interval: {
                leftPercent: 1,
                widthPercent: 7.5,
                durationText: '4.5s',
              },
            },
          ],
        },
        lanes,
      },
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.tl-bar--inner')).not.toBeNull();
    expect(el.querySelectorAll('.tl-row--lane').length).toBe(40);
    expect(el.querySelector('.tl-legend--lanes')?.textContent).toContain(
      'Running',
    );
    expect(el.querySelector('.tl-legend--lanes')?.textContent).toContain(
      'No test record',
    );

    const firstLaneLink = el.querySelector(
      'a.tl-row--lane',
    ) as HTMLAnchorElement | null;
    expect(firstLaneLink).not.toBeNull();
    expect(firstLaneLink?.getAttribute('href')).toBe(
      '/jobs/job-999/tests/test-1?tab=timeline',
    );

    // Hovering a job setup bar opens enterJobBar tooltip.
    const innerSetupBar = el.querySelector('.tl-bar--inner') as HTMLElement;
    innerSetupBar.dispatchEvent(
      new MouseEvent('mouseenter', {clientX: 100, clientY: 100}),
    );
    fixture.detectChanges();
    expect(el.querySelector('.tl-tip .tl-tip-title')?.textContent).toBe(
      'Resolving files',
    );

    // Hovering a recorded lane with empty fullName falls back to displayName in enterJobLane.
    firstLaneLink!.dispatchEvent(
      new MouseEvent('mouseenter', {clientX: 120, clientY: 140}),
    );
    fixture.detectChanges();
    expect(el.querySelector('.tl-tip .tl-tip-title')?.textContent).toBe(
      'case_1',
    );

    // Unrecorded lane renders as a non-link div with "No test record in MOSS" and "Unknown".
    const secondLane = el.querySelectorAll('.tl-row--lane')[1] as HTMLElement;
    expect(secondLane.tagName).toBe('DIV');
    expect(secondLane.textContent).toContain('No test record in MOSS');
    expect(secondLane.textContent).toContain('Unknown');
    secondLane.dispatchEvent(
      new MouseEvent('mouseenter', {clientX: 120, clientY: 160}),
    );
    fixture.detectChanges();
    expect(el.querySelector('.tl-tip .tl-tip-title')?.textContent).toBe(
      'No test record',
    );

    // Endmark focusKey matches 'waiting' for lane 4 (ended during device wait) and 'running' for lane 1.
    expect(component.laneEndMarkFocusKey(lanes[0])).toBe('running');
    expect(component.laneEndMarkFocusKey(lanes[3])).toBe('waiting');

    // Expand beyond the 40-lane cap.
    const showMoreRow = el.querySelector('.tl-row--group') as HTMLElement;
    expect(showMoreRow.textContent).toContain('Show 2 more test runs');
    showMoreRow.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.tl-row--lane').length).toBe(42);
    expect(component.showAllLanes()).toBeTrue();
  });
});
