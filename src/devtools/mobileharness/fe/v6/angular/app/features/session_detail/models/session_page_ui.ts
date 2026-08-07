import {SessionDetail} from '../../../core/models/session_overview';

/** UI View State for Session Detail page component. */
export declare interface SessionPageData {
  readonly sessionDetail: SessionDetail | null;
  readonly error?: string;
}
