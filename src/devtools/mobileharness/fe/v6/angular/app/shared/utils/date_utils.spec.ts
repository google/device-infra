import {dateUtils} from './date_utils';

describe('dateUtils', () => {
  describe('format', () => {
    it('should return "Unknown" for a null date', () => {
      expect(dateUtils.format(null)).toBe('Unknown');
    });

    it('should format a date with custom options', () => {
      const date = '2025-01-15T20:30:00Z';
      const options: Intl.DateTimeFormatOptions = {
        month: 'long',
        day: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'UTC',
      };
      expect(dateUtils.format(date, options)).toBe(
        'January 15, 2025 at 08:30 PM UTC',
      );
    });

    it('should handle invalid date strings', () => {
      expect(dateUtils.format('invalid-date')).toBe('Invalid Date');
    });
  });
});
