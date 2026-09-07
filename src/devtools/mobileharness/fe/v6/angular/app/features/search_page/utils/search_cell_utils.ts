import {Indicator, NavTarget} from '../../../core/models/search';

/** Mapping of protobuf Indicator enum values to CSS status classes. */
const INDICATOR_CLASS_MAP: Record<string, string> = {
  [Indicator.INDICATOR_OK]: 'status-ok',
  [Indicator.INDICATOR_ACTIVE]: 'status-active',
  [Indicator.INDICATOR_ERROR]: 'status-error',
  [Indicator.INDICATOR_NEUTRAL]: 'status-neutral',
};

/** Constructs Angular RouterLink route path directly from Protobuf NavTarget (100% aligns with search_common.proto). */
export function getRouterLink(target: NavTarget | undefined): string | null {
  if (!target) return null;

  if (target.device?.id) return `/devices/${target.device.id}`;
  if (target.host?.hostName) return `/hosts/${target.host.hostName}`;
  if (target.job?.jobId) return `/jobs/${target.job.jobId}`;
  if (target.session?.sessionId) return `/sessions/${target.session.sessionId}`;
  if (target.test?.testId) {
    const jobId = target.test.jobId || 'unknown_job';
    return `/jobs/${jobId}/tests/${target.test.testId}`;
  }

  return null;
}

/** Computes the CSS status class name directly from protobuf Indicator. */
export function getStatusClass(indicator?: Indicator | string): string {
  return (indicator && INDICATOR_CLASS_MAP[indicator]) || 'status-neutral';
}
