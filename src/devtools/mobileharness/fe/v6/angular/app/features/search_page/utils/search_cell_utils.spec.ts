import {Indicator} from '../../../core/models/search';
import {getStatusClass} from './search_cell_utils';

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
});
