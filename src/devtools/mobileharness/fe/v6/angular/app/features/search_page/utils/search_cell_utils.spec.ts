import {Indicator} from '../../../core/models/search';
import {
  formatPaginationRange,
  getRowsSelectionState,
  getStatusClass,
  isRowSelected,
} from './search_cell_utils';

describe('search_cell_utils', () => {
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

  describe('getRowsSelectionState', () => {
    it('returns {all: false, some: false} when rows array is empty', () => {
      expect(getRowsSelectionState([], new Set(['dev-1']))).toEqual({
        all: false,
        some: false,
      });
    });

    it('computes none, partial, and full selection states', () => {
      const rows = [{id: 'dev-1'}, {id: 'dev-2'}];
      expect(getRowsSelectionState(rows, new Set())).toEqual({
        all: false,
        some: false,
      });
      expect(getRowsSelectionState(rows, new Set(['dev-1']))).toEqual({
        all: false,
        some: true,
      });
      expect(getRowsSelectionState(rows, new Set(['dev-1', 'dev-2']))).toEqual({
        all: true,
        some: false,
      });
    });
  });

  describe('formatPaginationRange', () => {
    it('returns empty string when start, end, or total is missing or zero', () => {
      expect(formatPaginationRange(undefined, 10, 20)).toBe('');
      expect(formatPaginationRange(1, undefined, 20)).toBe('');
      expect(formatPaginationRange(1, 10, undefined)).toBe('');
      expect(formatPaginationRange(0, 10, 20)).toBe('');
    });

    it('formats range string with optional suffix', () => {
      expect(formatPaginationRange(1, 25, 1250)).toBe('1–25 of 1,250');
      expect(formatPaginationRange(1, 10, 142, ' groups')).toBe(
        '1–10 of 142 groups',
      );
    });
  });

  describe('isRowSelected and getRowsSelectionState with composite keys', () => {
    it('distinguishes duplicate deviceIds across different hosts', () => {
      const errorRowHost1 = {
        id: '000000000000',
        cells: [
          {
            link: {
              text: '000000000000',
              target: {device: {id: '000000000000', hostName: 'host-1.corp'}},
            },
          },
        ],
      };
      const errorRowHost2 = {
        id: '000000000000',
        cells: [
          {
            link: {
              text: '000000000000',
              target: {device: {id: '000000000000', hostName: 'host-2.corp'}},
            },
          },
        ],
      };

      const selected = new Map([
        [
          '000000000000@@host-1.corp',
          {deviceId: '000000000000', hostName: 'host-1.corp'},
        ],
      ]);

      expect(isRowSelected(errorRowHost1, selected)).toBeTrue();
      expect(isRowSelected(errorRowHost2, selected)).toBeFalse();
      expect(
        getRowsSelectionState([errorRowHost1, errorRowHost2], selected),
      ).toEqual({
        all: false,
        some: true,
      });
    });
  });
});
