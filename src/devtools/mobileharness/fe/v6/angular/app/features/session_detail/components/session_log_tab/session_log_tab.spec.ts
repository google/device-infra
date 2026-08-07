import {ChangeDetectionStrategy, Component} from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  discardPeriodicTasks,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {SessionStatus} from '../../../../core/models/common_models';
import {GetSessionLogResponse} from '../../../../core/models/session_overview';
import {
  SESSION_SERVICE,
  SessionService,
} from '../../../../core/services/session/session_service';
import {SessionLogTab} from './session_log_tab';

@Component({
  standalone: true,
  imports: [SessionLogTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-session-log-tab [sessionId]="sessionId"></app-session-log-tab>`,
})
class TestHostComponent {
  sessionId = 'session_123';
}

describe('SessionLogTab Component', () => {
  describe('with DONE session', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let component: SessionLogTab;
    let mockSessionService: jasmine.SpyObj<SessionService>;

    beforeEach(async () => {
      mockSessionService = jasmine.createSpyObj('SessionService', [
        'getSessionLog',
      ]);
      mockSessionService.getSessionLog.and.returnValue(
        of({
          logContent: 'Log Content 1\nLog Content 2',
          nextOffset: 27,
          sessionStatus: SessionStatus.SESSION_STATUS_DONE,
          logReset: false,
          contentHash: 'hash-done',
        } as unknown as GetSessionLogResponse),
      );

      await TestBed.configureTestingModule({
        imports: [SessionLogTab, TestHostComponent, NoopAnimationsModule],
        providers: [{provide: SESSION_SERVICE, useValue: mockSessionService}],
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      fixture.componentInstance.sessionId = 'session_123';
      fixture.detectChanges();
      component = fixture.debugElement.query(
        By.directive(SessionLogTab),
      ).componentInstance;
    });

    it('should be created', () => {
      expect(component).toBeTruthy();
    });

    it('should fetch logs correctly', () => {
      expect(mockSessionService.getSessionLog).toHaveBeenCalledWith({
        sessionId: 'session_123',
        offset: 0,
        contentHash: undefined,
      });
      expect(component.logLines()).toEqual(['Log Content 1', 'Log Content 2']);
      expect(component.logViewport()).toBeTruthy();
    });
  });

  describe('with RUNNING session', () => {
    let runningFixture: ComponentFixture<TestHostComponent>;
    let runningComponent: SessionLogTab;
    let runningMockSessionService: jasmine.SpyObj<SessionService>;

    beforeEach(async () => {
      runningMockSessionService = jasmine.createSpyObj('SessionService', [
        'getSessionLog',
      ]);

      let calls = 0;
      runningMockSessionService.getSessionLog.and.callFake(() => {
        calls++;
        if (calls === 1) {
          return of({
            logContent: 'Line 1\nLine 2\n',
            nextOffset: 13,
            sessionStatus: SessionStatus.SESSION_STATUS_RUNNING,
            logReset: false,
            contentHash: 'hash-running-1',
          } as unknown as GetSessionLogResponse);
        }
        return of({
          logContent: 'Line 3\nLine 4\n',
          nextOffset: 27,
          sessionStatus: SessionStatus.SESSION_STATUS_RUNNING,
          logReset: false,
          contentHash: 'hash-running-2',
        } as unknown as GetSessionLogResponse);
      });

      await TestBed.configureTestingModule({
        imports: [SessionLogTab, TestHostComponent, NoopAnimationsModule],
        providers: [
          {provide: SESSION_SERVICE, useValue: runningMockSessionService},
        ],
      }).compileComponents();

      runningFixture = TestBed.createComponent(TestHostComponent);
      runningFixture.componentInstance.sessionId = 'session_123';
      runningComponent = runningFixture.debugElement.query(
        By.directive(SessionLogTab),
      ).componentInstance;
    });

    it('should poll and append logs', fakeAsync(() => {
      runningFixture.detectChanges();

      expect(runningComponent.logLines()).toEqual(['Line 1', 'Line 2']);

      tick(5000);

      expect(runningComponent.logLines()).toEqual([
        'Line 1',
        'Line 2',
        'Line 3',
        'Line 4',
      ]);

      discardPeriodicTasks();
    }));
  });
});
