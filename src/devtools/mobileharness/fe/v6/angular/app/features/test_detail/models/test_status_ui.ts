import {
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';

/** UI status display metadata structure. */
export declare interface StatusDisplayMeta {
  readonly label: string;
  readonly cssClass: string;
  readonly icon: string;
  readonly isSpinning?: boolean;
}

/** Mapping of TestStatus to UI display properties. */
export const TEST_STATUS_DISPLAY_MAP: Record<TestStatus, StatusDisplayMeta> = {
  [TestStatus.TEST_STATUS_RUNNING]: {
    label: 'Running',
    cssClass: 'blue',
    icon: 'sync',
    isSpinning: true,
  },
  [TestStatus.TEST_STATUS_SUSPENDED]: {
    label: 'Suspended',
    cssClass: 'yellow',
    icon: 'pause_circle_filled',
  },
  [TestStatus.TEST_STATUS_DONE]: {
    label: 'Done',
    cssClass: 'gray',
    icon: 'watch_later',
  },
  [TestStatus.TEST_STATUS_NEW]: {
    label: 'Queued',
    cssClass: 'gray',
    icon: 'fiber_new',
  },
  [TestStatus.TEST_STATUS_ASSIGNED]: {
    label: 'Assigned',
    cssClass: 'gray',
    icon: 'assignment_ind',
  },
  [TestStatus.TEST_STATUS_UNSPECIFIED]: {
    label: 'Unknown',
    cssClass: 'gray',
    icon: 'help_outline',
  },
};

/** Mapping of TestResult to UI display properties. */
export const TEST_RESULT_DISPLAY_MAP: Record<TestResult, StatusDisplayMeta> = {
  [TestResult.TEST_RESULT_PASS]: {
    label: 'Pass',
    cssClass: 'green',
    icon: 'check_circle',
  },
  [TestResult.TEST_RESULT_FAIL]: {
    label: 'Fail',
    cssClass: 'red',
    icon: 'error',
  },
  [TestResult.TEST_RESULT_ERROR]: {
    label: 'Error',
    cssClass: 'red',
    icon: 'error',
  },
  [TestResult.TEST_RESULT_TIMEOUT]: {
    label: 'Timeout',
    cssClass: 'red',
    icon: 'access_time',
  },
  [TestResult.TEST_RESULT_SKIP]: {
    label: 'Skipped',
    cssClass: 'gray',
    icon: 'block',
  },
  [TestResult.TEST_RESULT_ABORT]: {
    label: 'Aborted',
    cssClass: 'gray',
    icon: 'cancel',
  },
  [TestResult.TEST_RESULT_UNSPECIFIED]: {
    label: 'Unknown',
    cssClass: 'gray',
    icon: 'help_outline',
  },
};
