import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {TestOverviewData} from '../../../../core/models/test_overview';
import {TEST_SERVICE, TestService} from '../../../../core/services/test/test_service';
import {TestLogTab} from './test_log_tab';

@Component({
  standalone: true,
  imports: [TestLogTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-test-log-tab [testId]="testId"></app-test-log-tab>`,
})
class TestHostComponent {
  testId!: string;
}

describe('TestLogTab Component', () => {

  describe('with DONE test', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let component: TestLogTab;
    let mockTestService: jasmine.SpyObj<TestService>;

    beforeEach(async () => {
      mockTestService = jasmine.createSpyObj('TestService', ['getTest', 'getTestLog']);
      mockTestService.getTest.and.returnValue(
        of({
          status: 'DONE',
        } as TestOverviewData),
      );
      mockTestService.getTestLog.and.returnValue(
        of({
          logContent: 'Log Content 1\nLog Content 2',
          nextOffset: 27,
          hasMore: false,
          cloudLogLink: 'http://cloud-log-link',
        }),
      );

      await TestBed.configureTestingModule({
        imports: [TestLogTab, TestHostComponent, NoopAnimationsModule],
        providers: [{provide: TEST_SERVICE, useValue: mockTestService}],
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      fixture.componentInstance.testId = 'test_123';
      fixture.detectChanges();
      component = fixture.debugElement.query(
        By.directive(TestLogTab),
      ).componentInstance;
    });

    it('should be created', () => {
      expect(component).toBeTruthy();
    });

    it('should fetch logs and bind inputs correctly', () => {
      expect(mockTestService.getTest).toHaveBeenCalledWith('test_123');
      expect(mockTestService.getTestLog).toHaveBeenCalledWith(
        'test_123',
        0,
        100000,
      );
      expect(component.logLines()).toEqual(['Log Content 1', 'Log Content 2']);
      expect(component.cloudLogLink()).toBe('http://cloud-log-link');
    });
  });

  describe('with RUNNING test', () => {
    let runningFixture: ComponentFixture<TestHostComponent>;
    let runningComponent: TestLogTab;
    let runningMockTestService: jasmine.SpyObj<TestService>;

    beforeEach(async () => {
      runningMockTestService = jasmine.createSpyObj('TestService', ['getTest', 'getTestLog']);

      runningMockTestService.getTest.and.returnValue(
        of({
          status: 'RUNNING',
        } as TestOverviewData),
      );

      runningMockTestService.getTestLog.and.returnValues(
        of({
          logContent: 'Line 1\nLine 2',
          nextOffset: 13,
          hasMore: false,
          cloudLogLink: 'http://cloud-log-link',
        }),
        of({
          logContent: '\nLine 3\nLine 4',
          nextOffset: 27,
          hasMore: false,
          cloudLogLink: 'http://cloud-log-link',
        })
      );

      await TestBed.configureTestingModule({
        imports: [TestLogTab, TestHostComponent, NoopAnimationsModule],
        providers: [{provide: TEST_SERVICE, useValue: runningMockTestService}],
      }).compileComponents();

      runningFixture = TestBed.createComponent(TestHostComponent);
      runningFixture.componentInstance.testId = 'test_running';
      runningComponent = runningFixture.debugElement.query(
        By.directive(TestLogTab),
      ).componentInstance;
    });

    it('should poll and append logs', fakeAsync(() => {
      runningFixture.detectChanges();

      expect(runningComponent.logLines()).toEqual(['Line 1', 'Line 2']);

      tick(2000);

      expect(runningComponent.logLines()).toEqual([
        'Line 1',
        'Line 2',
        '',
        'Line 3',
        'Line 4',
      ]);

      discardPeriodicTasks();
    }));
  });
});
