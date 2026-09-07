import {Indicator} from '../../../core/models/search';
import {getRouterLink, getStatusClass} from './search_cell_utils';

describe('search_cell_utils', () => {
  describe('getRouterLink', () => {
    it('builds device, host, job, session, test router links', () => {
      expect(getRouterLink({device: {id: 'd1'}})).toBe('/devices/d1');
      expect(getRouterLink({host: {hostName: 'h1'}})).toBe('/hosts/h1');
      expect(getRouterLink({job: {jobId: 'j1'}})).toBe('/jobs/j1');
      expect(getRouterLink({session: {sessionId: 's1'}})).toBe('/sessions/s1');
      expect(getRouterLink({test: {jobId: 'j1', testId: 't1'}})).toBe(
        '/jobs/j1/tests/t1',
      );
      expect(getRouterLink(undefined)).toBeNull();
    });
  });

  describe('getStatusClass', () => {
    it('evaluates status class from protobuf Indicator enum or number', () => {
      expect(getStatusClass(Indicator.INDICATOR_OK)).toBe('status-ok');
      expect(getStatusClass(Indicator.INDICATOR_ACTIVE)).toBe('status-active');
      expect(getStatusClass(Indicator.INDICATOR_ERROR)).toBe('status-error');
      expect(getStatusClass(Indicator.INDICATOR_NEUTRAL)).toBe(
        'status-neutral',
      );
      expect(getStatusClass(Indicator.INDICATOR_UNSPECIFIED)).toBe(
        'status-neutral',
      );
      expect(getStatusClass(undefined)).toBe('status-neutral');
    });
  });
});
