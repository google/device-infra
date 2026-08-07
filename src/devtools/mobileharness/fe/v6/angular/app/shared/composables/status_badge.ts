import {JobResult, JobStatus} from '../../core/models/common_models';
import {TestResult, TestStatus} from '../../core/models/test_overview';

/** Configuration model for rendering status badge UI elements. */
export interface StatusBadge {
  bgClass: string;
  colorClass: string;
  icon: string;
  text: string;
  spin?: boolean;
}

/** Returns UI badge configuration for a given JobStatus / JobResult. */
export function getJobStatusBadge(
  status?: JobStatus | string,
  result?: JobResult | string,
): StatusBadge {
  if (
    status === JobStatus.JOB_STATUS_RUNNING ||
    status === 'JOB_STATUS_RUNNING' ||
    status === 'RUNNING'
  ) {
    return {
      bgClass: 'status-blue bg-blue-50',
      colorClass: 'text-blue-600',
      icon: 'sync',
      text: 'Running',
      spin: true,
    };
  }
  if (
    status === JobStatus.JOB_STATUS_SUSPENDED ||
    status === 'JOB_STATUS_SUSPENDED' ||
    status === 'SUSPENDED'
  ) {
    return {
      bgClass: 'status-yellow bg-yellow-55',
      colorClass: 'text-yellow-700',
      icon: 'pause_circle_filled',
      text: 'Suspended',
      spin: false,
    };
  }
  if (
    status === JobStatus.JOB_STATUS_ASSIGNED ||
    status === 'JOB_STATUS_ASSIGNED' ||
    status === 'ASSIGNED'
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-500',
      icon: 'schedule',
      text: 'Assigned',
      spin: false,
    };
  }
  if (
    status !== JobStatus.JOB_STATUS_DONE &&
    status !== 'JOB_STATUS_DONE' &&
    status !== 'DONE'
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-500',
      icon: 'schedule',
      text: 'Queued',
      spin: false,
    };
  }

  if (
    result === JobResult.JOB_RESULT_PASS ||
    result === 'JOB_RESULT_PASS' ||
    result === 'PASS'
  ) {
    return {
      bgClass: 'status-green bg-green-50',
      colorClass: 'text-green-600',
      icon: 'check_circle',
      text: 'Passed',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_FAIL ||
    result === 'JOB_RESULT_FAIL' ||
    result === 'FAIL'
  ) {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'error',
      text: 'Failed',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_ERROR ||
    result === 'JOB_RESULT_ERROR' ||
    result === 'ERROR'
  ) {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'error',
      text: 'Error',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_TIMEOUT ||
    result === 'JOB_RESULT_TIMEOUT' ||
    result === 'TIMEOUT'
  ) {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'access_time',
      text: 'Timeout',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_ABORT ||
    result === 'JOB_RESULT_ABORT' ||
    result === 'ABORT'
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-600',
      icon: 'do_not_disturb_on',
      text: 'Aborted',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_SKIP ||
    result === 'JOB_RESULT_SKIP' ||
    result === 'SKIP'
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-500',
      icon: 'block',
      text: 'Skipped',
      spin: false,
    };
  }
  return {
    bgClass: 'status-gray bg-gray-50',
    colorClass: 'text-gray-500',
    icon: 'check_circle_outline',
    text: 'Done',
    spin: false,
  };
}

/** Returns UI badge configuration for Job Status only. */
export function getJobStatusBadgeOnly(
  status?: JobStatus | string,
): StatusBadge {
  if (
    status === JobStatus.JOB_STATUS_RUNNING ||
    status === 'JOB_STATUS_RUNNING' ||
    status === 'RUNNING'
  ) {
    return {
      bgClass: 'status-blue bg-blue-50',
      colorClass: 'text-blue-600',
      icon: 'sync',
      text: 'Running',
      spin: true,
    };
  }
  if (
    status === JobStatus.JOB_STATUS_SUSPENDED ||
    status === 'JOB_STATUS_SUSPENDED' ||
    status === 'SUSPENDED'
  ) {
    return {
      bgClass: 'status-yellow bg-yellow-55',
      colorClass: 'text-yellow-700',
      icon: 'pause_circle_filled',
      text: 'Suspended',
      spin: false,
    };
  }
  if (
    status === JobStatus.JOB_STATUS_ASSIGNED ||
    status === 'JOB_STATUS_ASSIGNED' ||
    status === 'ASSIGNED'
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-500',
      icon: 'schedule',
      text: 'Assigned',
      spin: false,
    };
  }
  if (
    status === JobStatus.JOB_STATUS_DONE ||
    status === 'JOB_STATUS_DONE' ||
    status === 'DONE'
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-600',
      icon: 'check_circle_outline',
      text: 'Done',
      spin: false,
    };
  }
  return {
    bgClass: 'status-gray bg-gray-50',
    colorClass: 'text-gray-500',
    icon: 'schedule',
    text: 'Queued',
    spin: false,
  };
}

/** Returns UI badge configuration for Job Result. */
export function getJobResultBadge(result?: JobResult | string): StatusBadge {
  if (
    result === JobResult.JOB_RESULT_PASS ||
    result === 'JOB_RESULT_PASS' ||
    result === 'PASS'
  ) {
    return {
      bgClass: 'status-green bg-green-50',
      colorClass: 'text-green-600',
      icon: 'check_circle',
      text: 'Passed',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_FAIL ||
    result === 'JOB_RESULT_FAIL' ||
    result === 'FAIL'
  ) {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'error',
      text: 'Failed',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_ERROR ||
    result === 'JOB_RESULT_ERROR' ||
    result === 'ERROR'
  ) {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'error',
      text: 'Error',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_TIMEOUT ||
    result === 'JOB_RESULT_TIMEOUT' ||
    result === 'TIMEOUT'
  ) {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'access_time',
      text: 'Timeout',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_ABORT ||
    result === 'JOB_RESULT_ABORT' ||
    result === 'ABORT'
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-600',
      icon: 'do_not_disturb_on',
      text: 'Aborted',
      spin: false,
    };
  }
  if (
    result === JobResult.JOB_RESULT_SKIP ||
    result === 'JOB_RESULT_SKIP' ||
    result === 'SKIP'
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-500',
      icon: 'block',
      text: 'Skipped',
      spin: false,
    };
  }
  return {
    bgClass: 'status-gray bg-gray-50',
    colorClass: 'text-gray-500',
    icon: 'help_outline',
    text: 'N/A',
    spin: false,
  };
}

/** Returns UI badge configuration for a given TestStatus. */
export function getTestStatusBadge(status?: TestStatus): StatusBadge {
  if (status === TestStatus.TEST_STATUS_RUNNING) {
    return {
      bgClass: 'status-blue bg-blue-50',
      colorClass: 'text-blue-600',
      icon: 'sync',
      text: 'Running',
      spin: true,
    };
  }
  if (status === TestStatus.TEST_STATUS_SUSPENDED) {
    return {
      bgClass: 'status-yellow bg-yellow-55',
      colorClass: 'text-yellow-700',
      icon: 'pause_circle_filled',
      text: 'Suspended',
      spin: false,
    };
  }
  if (
    status === TestStatus.TEST_STATUS_NEW ||
    status === TestStatus.TEST_STATUS_ASSIGNED
  ) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-500',
      icon: 'schedule',
      text: status === TestStatus.TEST_STATUS_ASSIGNED ? 'Assigned' : 'Queued',
      spin: false,
    };
  }
  if (status === TestStatus.TEST_STATUS_DONE) {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-600',
      icon: 'check_circle_outline',
      text: 'Done',
      spin: false,
    };
  }
  return {
    bgClass: 'status-gray bg-gray-50',
    colorClass: 'text-gray-500',
    icon: 'help_outline',
    text: 'Unknown',
    spin: false,
  };
}

/** Returns UI badge configuration for a given TestResult. */
export function getTestResultBadge(result?: TestResult | string): StatusBadge {
  if (result === TestResult.TEST_RESULT_PASS || result === 'PASS') {
    return {
      bgClass: 'status-green bg-green-50',
      colorClass: 'text-green-600',
      icon: 'check_circle',
      text: 'Pass',
      spin: false,
    };
  }
  if (result === TestResult.TEST_RESULT_FAIL || result === 'FAIL') {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'error',
      text: 'Fail',
      spin: false,
    };
  }
  if (result === TestResult.TEST_RESULT_ERROR || result === 'ERROR') {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'error',
      text: 'Error',
      spin: false,
    };
  }
  if (result === TestResult.TEST_RESULT_TIMEOUT || result === 'TIMEOUT') {
    return {
      bgClass: 'status-red bg-red-50',
      colorClass: 'text-red-600',
      icon: 'access_time',
      text: 'Timeout',
      spin: false,
    };
  }
  if (result === TestResult.TEST_RESULT_ABORT || result === 'ABORT') {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-600',
      icon: 'do_not_disturb_on',
      text: 'Aborted',
      spin: false,
    };
  }
  if (result === TestResult.TEST_RESULT_SKIP || result === 'SKIP') {
    return {
      bgClass: 'status-gray bg-gray-50',
      colorClass: 'text-gray-500',
      icon: 'block',
      text: 'Skipped',
      spin: false,
    };
  }
  return {
    bgClass: 'status-gray bg-gray-50',
    colorClass: 'text-gray-500',
    icon: 'help_outline',
    text: 'N/A',
    spin: false,
  };
}
