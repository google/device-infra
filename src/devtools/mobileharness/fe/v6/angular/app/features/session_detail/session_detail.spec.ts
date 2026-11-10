import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {of} from 'rxjs';

import {APP_DATA} from '../../core/models/app_data';
import {SessionResult, SessionStatus} from '../../core/models/common_models';
import {SessionDetail} from '../../core/models/session_overview';
import {
  SESSION_SERVICE,
  SessionService,
} from '../../core/services/session/session_service';
import {IS_ASSISTANT_ENABLED} from '../../shared/components/diagnose_in_assistant/diagnose_in_assistant';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {LoadingService} from '../../shared/services/loading_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {SessionDetailPage} from './session_detail';

describe('SessionDetailPage Component', () => {
  let fixture: ComponentFixture<SessionDetailPage>;
  let component: SessionDetailPage;
  let mockSessionService: jasmine.SpyObj<SessionService>;

  const mockSessionData: SessionDetail = {
    id: 'session_12345678',
    name: 'Example session',
    status: SessionStatus.SESSION_STATUS_UNSPECIFIED,
    result: SessionResult.SESSION_RESULT_UNSPECIFIED,
  };

  beforeEach(async () => {
    mockSessionService = jasmine.createSpyObj<SessionService>(
      'SessionService',
      ['getSession', 'getSessionLog', 'getSessionFile'],
    );
    mockSessionService.getSession.and.returnValue(
      of({session: mockSessionData}),
    );

    await TestBed.configureTestingModule({
      imports: [SessionDetailPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: SESSION_SERVICE, useValue: mockSessionService},
        {
          provide: ClipboardService,
          useValue: jasmine.createSpyObj('ClipboardService', [
            'copyToClipboard',
          ]),
        },
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        {
          provide: LoadingService,
          useValue: jasmine.createSpyObj('LoadingService', ['show', 'hide']),
        },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({'id': 'session_12345678'})),
            queryParamMap: of(convertToParamMap({})),
          },
        },
        {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
        {provide: IS_ASSISTANT_ENABLED, useValue: () => true},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionDetailPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('treats SESSION_STATUS_UNSPECIFIED as UNKNOWN and filters out SESSION_RESULT_UNSPECIFIED', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(component.statusPillLabel()).toBe('UNKNOWN');
    expect(component.resultPillLabel()).toBeNull();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.status-indicator')?.textContent).toContain(
      'Status: UNKNOWN',
    );
    expect(
      compiled.querySelector('.status-indicator')?.textContent,
    ).not.toContain('Result:');
  }));

  it('shows Diagnose in Assistant in Error Details when the session fails', fakeAsync(() => {
    mockSessionService.getSession.and.returnValue(
      of({
        session: {
          id: 'session_12345678',
          name: 'Example session',
          status: SessionStatus.SESSION_STATUS_DONE,
          result: SessionResult.SESSION_RESULT_FAIL,
          troubleshooting: {
            resultCause: {error: [{message: 'Session job failed'}]},
          },
        },
      }),
    );
    const failedFixture = TestBed.createComponent(SessionDetailPage);
    failedFixture.detectChanges();
    tick();
    failedFixture.detectChanges();

    const chip = (failedFixture.nativeElement as HTMLElement).querySelector(
      '#overview-error .title-container a.diagnose-chip',
    ) as HTMLAnchorElement;
    expect(chip).not.toBeNull();
    expect(chip.getAttribute('href')).toContain(
      'q=Why%20did%20session%20session_12345678%20fail%3F',
    );
  }));
});
