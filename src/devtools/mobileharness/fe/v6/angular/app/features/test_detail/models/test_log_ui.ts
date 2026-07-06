import {TestStatus} from '@deviceinfra/app/core/models/test_overview';

/**
 * Internal state representing the outcome of a log chunk fetch operation.
 */
export interface FetchState {
  /** The byte offset representing the end of the downloaded log chunk. */
  readonly offset: number;
  /** Boolean indicating whether additional log chunks are immediately available on the server. */
  readonly hasMore: boolean;
  /** The current execution status of the test being monitored. */
  readonly status: TestStatus;
  /** The raw textual log content downloaded in this specific chunk. */
  readonly logContent: string;
}
