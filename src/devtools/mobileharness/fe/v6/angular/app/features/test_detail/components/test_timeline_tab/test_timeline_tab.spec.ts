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
    stages: [],
  };

  let fixture: ComponentFixture<TestHostComponent>;
  let component: TestTimelineTab;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestTimelineTab, TestHostComponent, NoopAnimationsModule],
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
});
