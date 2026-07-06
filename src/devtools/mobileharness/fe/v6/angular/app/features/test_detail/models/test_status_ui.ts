import {
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';

/** UI status display metadata structure. */
export interface StatusDisplayMeta {
  readonly label: string;
  readonly cssClass: string;
  readonly icon: string;
  readonly isSpinning?: boolean;
}

/** Mapping of TestStatus to UI display properties. */
export const TEST_STATUS_DISPLAY_MAP: Record<TestStatus, StatusDisplayMeta> = {
  [TestStatus.TEST_STATUS_RUNNING]: {
    label: 'RUNNING',
    cssClass: 'blue',
    icon: 'sync',
    isSpinning: true,
  },
  [TestStatus.TEST_STATUS_SUSPENDED]: {
    label: 'SUSPENDED',
    cssClass: 'yellow',
    icon: 'pause_circle_filled',
  },
  [TestStatus.TEST_STATUS_DONE]: {
    label: 'DONE',
    cssClass: 'gray',
    icon: 'watch_later',
  },
  [TestStatus.TEST_STATUS_NEW]: {
    label: 'NEW',
    cssClass: 'gray',
    icon: 'fiber_new',
  },
  [TestStatus.TEST_STATUS_ASSIGNED]: {
    label: 'ASSIGNED',
    cssClass: 'gray',
    icon: 'assignment_ind',
  },
  [TestStatus.TEST_STATUS_UNSPECIFIED]: {
    label: 'UNSPECIFIED',
    cssClass: 'gray',
    icon: 'help_outline',
  },
};

/** Mapping of TestResult to UI display properties. */
export const TEST_RESULT_DISPLAY_MAP: Record<TestResult, StatusDisplayMeta> = {
  [TestResult.TEST_RESULT_PASS]: {
    label: 'PASS',
    cssClass: 'green',
    icon: 'check_circle',
  },
  [TestResult.TEST_RESULT_FAIL]: {
    label: 'FAIL',
    cssClass: 'red',
    icon: 'error',
  },
  [TestResult.TEST_RESULT_ERROR]: {
    label: 'ERROR',
    cssClass: 'red',
    icon: 'error',
  },
  [TestResult.TEST_RESULT_TIMEOUT]: {
    label: 'TIMEOUT',
    cssClass: 'red',
    icon: 'access_time',
  },
  [TestResult.TEST_RESULT_SKIP]: {
    label: 'SKIP',
    cssClass: 'gray',
    icon: 'block',
  },
  [TestResult.TEST_RESULT_ABORT]: {
    label: 'ABORT',
    cssClass: 'gray',
    icon: 'cancel',
  },
  [TestResult.TEST_RESULT_UNSPECIFIED]: {
    label: 'UNKNOWN',
    cssClass: 'gray',
    icon: 'help_outline',
  },
};
