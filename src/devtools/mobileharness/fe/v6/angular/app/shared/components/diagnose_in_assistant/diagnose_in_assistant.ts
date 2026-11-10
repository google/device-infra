/**
 * @fileoverview Outlined Material 3 assist chip that opens the OmniLab
 * Assistant in a new tab with a diagnostic question about a failing entity.
 *
 * Detail pages place one chip where a failure is explained: right after the
 * state headline of an out-of-service device, and at the start of the Error
 * Details card header on a finished test, job, or session with a failing
 * result. In OSS builds, `IS_ASSISTANT_ENABLED` is not provided and nothing
 * renders.
 */

import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  InjectionToken,
  input,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute, ParamMap, RouterModule} from '@angular/router';
import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  TestResult,
  TestStatus,
} from '../../../core/models/common_models';
import type {
  DeviceOverview,
  HealthAndActivityInfo,
} from '../../../core/models/device_overview';
import {CommonParamsService} from '../../../core/services/common_params_service';

/**
 * Checks whether the OmniLab Assistant feature is enabled on the active route.
 * Provided in Google-internal builds; absent in OSS builds.
 */
export const IS_ASSISTANT_ENABLED = new InjectionToken<
  (queryParamMap?: ParamMap | null) => boolean
>('IS_ASSISTANT_ENABLED');

/** Returns whether a device is actively serving (healthy idle or busy). */
export function isDeviceInService(health: HealthAndActivityInfo): boolean {
  if (health.uiState && health.uiState !== 'UI_STATE_UNSPECIFIED') {
    return health.uiState === 'HEALTHY' || health.uiState === 'BUSY';
  }
  return (
    health.state === 'IN_SERVICE_IDLE' || health.state === 'IN_SERVICE_BUSY'
  );
}

/**
 * Returns the diagnostic question for an out-of-service device, or `''` while
 * the device is in service.
 */
export function deviceDiagnosePrompt(device: DeviceOverview): string {
  const health = device.healthAndActivity;
  if (isDeviceInService(health)) {
    return '';
  }
  const location = device.host.name
    ? `device ${device.id} on ${device.host.name}`
    : `device ${device.id}`;
  const quarantined =
    health.uiState && health.uiState !== 'UI_STATE_UNSPECIFIED'
      ? health.uiState === 'BLOCKED'
      : health.state === 'IDLE_BUT_QUARANTINED';
  if (quarantined) {
    return `Why is ${location} quarantined and unavailable for tests?`;
  }
  if (health.deviceTypes.length === 0) {
    return `Why does ${location} have no detected types?`;
  }
  return `Why is ${location} out of service?`;
}

/** Formats `test <id> (<name>)` or `job <id> (<name>)` for a question. */
export function namedExecutionSubject(
  kind: 'test' | 'job',
  id: string,
  name?: string,
): string {
  const trimmedName = (name ?? '').trim();
  return trimmedName ? `${kind} ${id} (${trimmedName})` : `${kind} ${id}`;
}

/**
 * Returns the diagnostic question for a test, job, or session that finished
 * with a failing result, worded after that result, or `''` when the run is not
 * finished or did not fail.
 */
export function executionDiagnosePrompt(
  subject: string,
  status: TestStatus | JobStatus | SessionStatus,
  result?: TestResult | JobResult | SessionResult,
): string {
  const isDone =
    status === TestStatus.TEST_STATUS_DONE ||
    status === JobStatus.JOB_STATUS_DONE ||
    status === SessionStatus.SESSION_STATUS_DONE;
  if (!isDone) {
    return '';
  }
  switch (result) {
    case TestResult.TEST_RESULT_FAIL:
    case JobResult.JOB_RESULT_FAIL:
    case SessionResult.SESSION_RESULT_FAIL:
      return `Why did ${subject} fail?`;
    case TestResult.TEST_RESULT_ERROR:
    case JobResult.JOB_RESULT_ERROR:
    case SessionResult.SESSION_RESULT_ERROR:
      return `Why did ${subject} end with an error?`;
    case TestResult.TEST_RESULT_TIMEOUT:
    case JobResult.JOB_RESULT_TIMEOUT:
    case SessionResult.SESSION_RESULT_TIMEOUT:
      return `Why did ${subject} time out?`;
    case TestResult.TEST_RESULT_ABORT:
    case JobResult.JOB_RESULT_ABORT:
    case SessionResult.SESSION_RESULT_ABORT:
      return `Why was ${subject} aborted?`;
    default:
      return '';
  }
}

/**
 * Renders the "Diagnose in Assistant" outlined Material 3 assist chip when the
 * Assistant is enabled and `prompt` is non-empty.
 */
@Component({
  selector: 'app-diagnose-in-assistant',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './diagnose_in_assistant.ng.html',
  styleUrl: './diagnose_in_assistant.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiagnoseInAssistant {
  /** Diagnostic question passed to `/assistant?q=...`. */
  readonly prompt = input.required<string>();

  private readonly route = inject(ActivatedRoute);
  private readonly commonParamsService = inject(CommonParamsService);
  private readonly isAssistantEnabledFn = inject(IS_ASSISTANT_ENABLED, {
    optional: true,
  });

  private readonly queryParamMap = toSignal(this.route.queryParamMap, {
    requireSync: true,
  });

  readonly trimmedPrompt = computed(() => this.prompt().trim());

  readonly visible = computed(
    () =>
      Boolean(this.trimmedPrompt()) &&
      (this.isAssistantEnabledFn?.(this.queryParamMap()) ?? false),
  );

  readonly assistantQueryParams = computed(() => {
    const params: Record<string, string> = {
      ...this.commonParamsService.getCommonParams(),
      'enable_assistant': 'true',
      'q': this.trimmedPrompt(),
    };
    delete params['is_embedded_mode'];
    return params;
  });

  onChipClick(event: MouseEvent): void {
    // Collapsible card headers listen for clicks on the header row; opening
    // the Assistant in a new tab leaves the card's expansion state untouched.
    event.stopPropagation();
  }
}
