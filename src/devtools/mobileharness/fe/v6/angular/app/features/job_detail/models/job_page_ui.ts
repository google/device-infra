import {JobActions, JobOverviewData} from '../../../core/models/job_overview';

/** UI View State for Job Detail page component. */
export declare interface JobPageData {
  readonly jobOverviewData: JobOverviewData | null;
  readonly actions?: JobActions;
  readonly error?: string;
}
