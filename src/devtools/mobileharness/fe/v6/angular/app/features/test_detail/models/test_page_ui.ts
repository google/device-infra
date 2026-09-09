import {
  TestActions,
  TestOverviewData,
} from '@deviceinfra/app/core/models/test_overview';

/** Represents the loaded data outcome or error state for the main test detail page. */
export declare interface TestPageData {
  readonly testOverviewData: TestOverviewData | null;
  readonly actions?: TestActions;
  readonly error?: string;
  readonly jobId?: string;
}
