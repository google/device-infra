import {Column, Indicator, Row} from '../../../core/models/search';
import {
  getCell,
  getCellType,
  getRouterLink,
  getRowId,
  getStatusClass,
  getTextValue,
  isTimeColumn,
} from './search_cell_utils';

describe('search_cell_utils', () => {
  describe('isTimeColumn', () => {
    it('identifies time-related columns correctly', () => {
      expect(isTimeColumn('timestamp')).toBeTrue();
      expect(isTimeColumn('start_time')).toBeTrue();
      expect(isTimeColumn('created_at')).toBeTrue();
      expect(isTimeColumn('device_id')).toBeFalse();
      expect(isTimeColumn(undefined)).toBeFalse();
    });
  });

  describe('getCell', () => {
    const columns: Column[] = [
      {key: 'id', displayName: 'ID'},
      {key: 'status', displayName: 'Status'},
    ];

    it('returns cell matching column index', () => {
      const row: Row = {
        id: 'row-1',
        cells: [
          {text: {value: 'dev-123'}},
          {status: {text: 'IDLE'}},
        ],
      };
      expect(getCell(row, 'id', columns)).toEqual({text: {value: 'dev-123'}});
      expect(getCell(row, 'status', columns)).toEqual({status: {text: 'IDLE'}});
      expect(getCell(row, 'unknown', columns)).toBeNull();
      expect(getCell(undefined, 'id', columns)).toBeNull();
    });
  });

  describe('getTextValue', () => {
    it('extracts string from text cell', () => {
      expect(getTextValue({text: {value: 'hello'}})).toBe('hello');
      expect(getTextValue({text: {value: 'hello_obj'}})).toBe('hello_obj');
    });

    it('extracts string from link, status, chips, multilink', () => {
      expect(getTextValue({link: {text: 'link_txt'}})).toBe('link_txt');
      expect(getTextValue({status: {text: 'PASS'}})).toBe('PASS');
      expect(getTextValue({chips: {values: ['a', 'b']}})).toBe('a, b');
      expect(
        getTextValue({
          multiLink: {entries: [{text: 'x'}, {text: 'y'}]},
        }),
      ).toBe('x, y');
      expect(getTextValue(undefined)).toBeNull();
    });
  });

  describe('getCellType', () => {
    it('identifies cell types correctly', () => {
      expect(getCellType({link: {text: 'l'}})).toBe('link');
      expect(getCellType({status: {text: 's'}})).toBe('status');
      expect(getCellType({chips: {values: []}})).toBe('chips');
      expect(getCellType({multiLink: {entries: []}})).toBe('multilink');
      expect(getCellType({text: {value: 't'}})).toBe('text');
      expect(getCellType(undefined)).toBe('unknown');
    });
  });

  describe('getRouterLink', () => {
    it('builds device, host, job, session, test router links', () => {
      expect(getRouterLink({target: {device: {id: 'd1'}}})).toEqual(['/devices', 'd1']);
      expect(getRouterLink({target: {host: {hostName: 'h1'}}})).toEqual(['/hosts', 'h1']);
      expect(getRouterLink({target: {job: {jobId: 'j1'}}})).toEqual(['/jobs', 'j1']);
      expect(getRouterLink({target: {session: {sessionId: 's1'}}})).toEqual(['/sessions', 's1']);
      expect(
        getRouterLink({target: {test: {jobId: 'j1', testId: 't1'}}}),
      ).toEqual(['/jobs', 'j1', 'tests', 't1']);
      expect(getRouterLink(undefined)).toBeNull();
    });
  });

  describe('getStatusClass', () => {
    it('evaluates status class from indicator or text', () => {
      expect(getStatusClass({status: {text: 'OK', indicator: Indicator.INDICATOR_OK}})).toBe('status-ok');
      expect(getStatusClass({status: {text: 'RUNNING'}})).toBe('status-active');
      expect(getStatusClass({status: {text: 'FAIL'}})).toBe('status-error');
      expect(getStatusClass({status: {text: 'UNKNOWN'}})).toBe('status-neutral');
      expect(getStatusClass(undefined)).toBe('status-neutral');
    });
  });

  describe('getRowId', () => {
    it('returns valid raw ID', () => {
      expect(getRowId({id: 'device-abc-123'})).toBe('device-abc-123');
    });

    it('filters protobuf placeholder all-zeros ID and falls back to link target or cell text', () => {
      const rowWithZeroIdAndLink: Row = {
        id: '000000000000',
        cells: [{link: {target: {device: {id: 'real-device-id'}}}}],
      };
      expect(getRowId(rowWithZeroIdAndLink)).toBe('real-device-id');

      const rowWithGuidPlaceholder: Row = {
        id: '00000000-0000-0000-0000-000000000000',
        cells: [{text: {value: 'text-fallback-id'}}],
      };
      expect(getRowId(rowWithGuidPlaceholder)).toBe('text-fallback-id');
    });

    it('handles undefined row', () => {
      expect(getRowId(undefined)).toBe('');
    });
  });
});
