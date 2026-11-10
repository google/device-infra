import {EnvironmentProviders, Provider} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {of} from 'rxjs';
import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
} from '../../../core/models/common_models';
import type {DeviceOverview} from '../../../core/models/device_overview';
import {CommonParamsService} from '../../../core/services/common_params_service';
import {
  deviceDiagnosePrompt,
  DiagnoseInAssistant,
  executionDiagnosePrompt,
  IS_ASSISTANT_ENABLED,
  isDeviceInService,
  namedExecutionSubject,
} from './diagnose_in_assistant';

function makeDevice(
  overrides: Partial<DeviceOverview['healthAndActivity']> = {},
  hostName = 'lab-1.prod.example.com',
): DeviceOverview {
  return {
    id: 'dev-42',
    host: {name: hostName, ip: '10.0.0.1'},
    healthAndActivity: {
      title: 'Out of Service (Needs Fixing)',
      subtitle: 'The device status is FAILED.',
      state: 'OUT_OF_SERVICE_NEEDS_FIXING',
      uiState: 'ERROR',
      deviceStatus: {status: 'FAILED', isCritical: true},
      deviceTypes: [{type: 'AndroidRealDevice', isAbnormal: false}],
      lastInServiceTime: null,
      ...overrides,
    },
    basicInfo: {
      model: 'Pixel 9',
      version: '15',
      form: 'physical',
      os: 'Android',
      batteryLevel: 80,
      network: {},
    },
    capabilities: {supportedDrivers: [], supportedDecorators: []},
    properties: {},
  };
}

describe('diagnose_in_assistant helpers', () => {
  it('recognizes serving devices from uiState or transitional state', () => {
    expect(
      isDeviceInService(makeDevice({uiState: 'HEALTHY'}).healthAndActivity),
    ).toBeTrue();
    expect(
      isDeviceInService(makeDevice({uiState: 'BUSY'}).healthAndActivity),
    ).toBeTrue();
    expect(
      isDeviceInService(makeDevice({uiState: 'ERROR'}).healthAndActivity),
    ).toBeFalse();
    expect(
      isDeviceInService(
        makeDevice({uiState: undefined, state: 'IN_SERVICE_IDLE'})
          .healthAndActivity,
      ),
    ).toBeTrue();
    expect(
      isDeviceInService(
        makeDevice({
          uiState: 'UI_STATE_UNSPECIFIED',
          state: 'IN_SERVICE_BUSY',
        }).healthAndActivity,
      ),
    ).toBeTrue();
    expect(
      isDeviceInService(
        makeDevice({
          uiState: undefined,
          state: 'OUT_OF_SERVICE_RECOVERING',
        }).healthAndActivity,
      ),
    ).toBeFalse();
  });

  it('builds device diagnostic questions only when out of service', () => {
    expect(deviceDiagnosePrompt(makeDevice({uiState: 'HEALTHY'}))).toBe('');
    expect(deviceDiagnosePrompt(makeDevice())).toBe(
      'Why is device dev-42 on lab-1.prod.example.com out of service?',
    );
    expect(deviceDiagnosePrompt(makeDevice({}, ''))).toBe(
      'Why is device dev-42 out of service?',
    );
    expect(
      deviceDiagnosePrompt(
        makeDevice({
          uiState: 'BLOCKED',
          deviceStatus: {status: 'IDLE', isCritical: false},
        }),
      ),
    ).toBe(
      'Why is device dev-42 on lab-1.prod.example.com quarantined and unavailable for tests?',
    );
    expect(
      deviceDiagnosePrompt(
        makeDevice({
          uiState: 'ERROR',
          deviceStatus: {status: 'IDLE', isCritical: false},
        }),
      ),
    ).toBe('Why is device dev-42 on lab-1.prod.example.com out of service?');
    expect(
      deviceDiagnosePrompt(
        makeDevice({
          uiState: undefined,
          state: 'IDLE_BUT_QUARANTINED',
          deviceStatus: {status: 'IDLE', isCritical: false},
        }),
      ),
    ).toBe(
      'Why is device dev-42 on lab-1.prod.example.com quarantined and unavailable for tests?',
    );
    expect(
      deviceDiagnosePrompt(
        makeDevice({
          uiState: 'UI_STATE_UNSPECIFIED',
          state: 'IDLE_BUT_QUARANTINED',
          deviceStatus: {status: 'IDLE', isCritical: false},
        }),
      ),
    ).toBe(
      'Why is device dev-42 on lab-1.prod.example.com quarantined and unavailable for tests?',
    );
    expect(
      deviceDiagnosePrompt(
        makeDevice({
          uiState: 'ERROR',
          state: 'IDLE_BUT_QUARANTINED',
          deviceStatus: {status: 'IDLE', isCritical: false},
        }),
      ),
    ).toBe('Why is device dev-42 on lab-1.prod.example.com out of service?');
    expect(deviceDiagnosePrompt(makeDevice({deviceTypes: []}))).toBe(
      'Why does device dev-42 on lab-1.prod.example.com have no detected types?',
    );
  });

  it('formats test, job, and session diagnostic questions by result', () => {
    expect(namedExecutionSubject('test', 't1', 'SmokeTest')).toBe(
      'test t1 (SmokeTest)',
    );
    expect(namedExecutionSubject('job', 'j1', '  ')).toBe('job j1');

    expect(
      executionDiagnosePrompt(
        'test t1 (Smoke)',
        TestStatus.TEST_STATUS_DONE,
        TestResult.TEST_RESULT_FAIL,
      ),
    ).toBe('Why did test t1 (Smoke) fail?');
    expect(
      executionDiagnosePrompt(
        'job j1 (Boot)',
        JobStatus.JOB_STATUS_DONE,
        JobResult.JOB_RESULT_ERROR,
      ),
    ).toBe('Why did job j1 (Boot) end with an error?');
    expect(
      executionDiagnosePrompt(
        'session s1',
        SessionStatus.SESSION_STATUS_DONE,
        SessionResult.SESSION_RESULT_TIMEOUT,
      ),
    ).toBe('Why did session s1 time out?');
    expect(
      executionDiagnosePrompt(
        'test t2',
        TestStatus.TEST_STATUS_DONE,
        TestResult.TEST_RESULT_ABORT,
      ),
    ).toBe('Why was test t2 aborted?');

    expect(
      executionDiagnosePrompt(
        'test t3',
        TestStatus.TEST_STATUS_RUNNING,
        TestResult.TEST_RESULT_FAIL,
      ),
    ).toBe('');
    expect(
      executionDiagnosePrompt(
        'test t4',
        TestStatus.TEST_STATUS_DONE,
        TestResult.TEST_RESULT_PASS,
      ),
    ).toBe('');
  });
});

describe('DiagnoseInAssistant', () => {
  function createChip(options: {
    prompt: string;
    assistantEnabled?: boolean | null;
    routeParams?: Record<string, string>;
    commonParams?: Record<string, string>;
  }): ComponentFixture<DiagnoseInAssistant> {
    TestBed.resetTestingModule();
    const providers: Array<Provider | EnvironmentProviders> = [
      provideRouter([]),
      {
        provide: CommonParamsService,
        useValue: {
          getCommonParams: () => options.commonParams ?? {},
        },
      },
      {
        provide: ActivatedRoute,
        useValue: {
          queryParamMap: of(convertToParamMap(options.routeParams ?? {})),
        },
      },
    ];
    if (
      options.assistantEnabled !== null &&
      options.assistantEnabled !== undefined
    ) {
      providers.push({
        provide: IS_ASSISTANT_ENABLED,
        useValue: () => Boolean(options.assistantEnabled),
      });
    }
    TestBed.configureTestingModule({
      imports: [DiagnoseInAssistant],
      providers,
    });
    const fixture = TestBed.createComponent(DiagnoseInAssistant);
    fixture.componentRef.setInput('prompt', options.prompt);
    fixture.detectChanges();
    return fixture;
  }

  it('renders nothing when IS_ASSISTANT_ENABLED is not provided (OSS)', () => {
    const fixture = createChip({
      prompt: 'Why did test t1 fail?',
      assistantEnabled: null,
    });

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.diagnose-chip'),
    ).toBeNull();
  });

  it('renders nothing when the Assistant is off or the prompt is blank', () => {
    const disabled = createChip({
      prompt: 'Why did test t1 fail?',
      assistantEnabled: false,
    });
    expect(
      (disabled.nativeElement as HTMLElement).querySelector('.diagnose-chip'),
    ).toBeNull();

    const blank = createChip({
      prompt: '   ',
      assistantEnabled: true,
    });
    expect(
      (blank.nativeElement as HTMLElement).querySelector('.diagnose-chip'),
    ).toBeNull();
  });

  it('renders an outlined chip linking to /assistant?q=... in a new tab and stops click propagation', () => {
    const fixture = createChip({
      prompt: '  Why did test t1 fail?  ',
      assistantEnabled: true,
      commonParams: {'fake_data': 'true', 'is_embedded_mode': 'true'},
    });

    const anchor = (fixture.nativeElement as HTMLElement).querySelector(
      'a.diagnose-chip',
    ) as HTMLAnchorElement;
    expect(anchor).not.toBeNull();
    expect(anchor.getAttribute('target')).toBe('_blank');
    expect(anchor.getAttribute('rel')).toBe('noopener');
    expect(anchor.textContent).toContain('Diagnose in Assistant');
    expect(
      anchor.querySelector('.diagnose-chip-icon')?.textContent?.trim(),
    ).toBe('smart_toy');
    expect(
      anchor.querySelector('.diagnose-chip-trail')?.textContent?.trim(),
    ).toBe('open_in_new');
    const href = anchor.getAttribute('href') ?? '';
    expect(href).toContain('/assistant?');
    expect(href).toContain('q=Why%20did%20test%20t1%20fail%3F');
    expect(href).toContain('enable_assistant=true');
    expect(href).toContain('fake_data=true');
    expect(href).not.toContain('is_embedded_mode');

    let parentClicked = false;
    (fixture.nativeElement as HTMLElement).addEventListener('click', () => {
      parentClicked = true;
    });
    anchor.addEventListener('click', (e) => {
      e.preventDefault();
    });
    anchor.click();

    expect(parentClicked).toBeFalse();
  });
});
