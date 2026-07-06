import {TestOverviewData} from '@deviceinfra/app/core/models/test_overview';

/** Represents the loaded data outcome or error state for the main test detail page. */
export interface TestPageData {
  readonly testOverviewData: TestOverviewData | null;
  readonly error?: string;
}
