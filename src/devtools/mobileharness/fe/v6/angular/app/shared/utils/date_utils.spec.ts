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

  describe('parsePdtTimestamp', () => {
    it('should parse PDT timestamp with space separator', () => {
      const parsed = dateUtils.parsePdtTimestamp('2025-07-09 10:11:15');
      expect(parsed.toISOString()).toBe('2025-07-09T17:11:15.000Z');
    });

    it('should parse PDT timestamp with T separator and Z suffix', () => {
      const parsed = dateUtils.parsePdtTimestamp('2025-07-09T10:11:15Z');
      expect(parsed.toISOString()).toBe('2025-07-09T17:11:15.000Z');
    });

    it('should parse PST timestamp during winter time', () => {
      const parsed = dateUtils.parsePdtTimestamp('2025-01-15 10:11:15');
      expect(parsed.toISOString()).toBe('2025-01-15T18:11:15.000Z');
    });

    it('should parse formatted PDT timestamp without hyphen', () => {
      const parsed = dateUtils.parsePdtTimestamp('Jul 9, 2025, 11:30:00 AM');
      expect(parsed.toISOString()).toBe('2025-07-09T18:30:00.000Z');
    });

    it('should return invalid date for invalid inputs', () => {
      expect(isNaN(dateUtils.parsePdtTimestamp(null).getTime())).toBeTrue();
      expect(
        isNaN(dateUtils.parsePdtTimestamp('invalid').getTime()),
      ).toBeTrue();
    });
  });

  describe('formatPdt', () => {
    it('should format Date to PDT string during summer time', () => {
      const date = new Date('2025-07-09T17:11:15Z');
      expect(dateUtils.formatPdt(date)).toMatch(
        /Jul 9, 2025, 10:11:15\s+AM\s+PDT/,
      );
    });

    it('should format Date to PST string during winter time', () => {
      const date = new Date('2025-01-15T18:11:15Z');
      expect(dateUtils.formatPdt(date)).toMatch(
        /Jan 15, 2025, 10:11:15\s+AM\s+PST/,
      );
    });
  });

  describe('getElapsedTimeText', () => {
    const baseDate = new Date('2025-07-09T10:00:00Z');

    it('should return empty values for invalid dates', () => {
      expect(
        dateUtils.getElapsedTimeText(new Date(NaN), baseDate, 'Start'),
      ).toEqual({
        durationText: '',
        elapsedHtml: '',
      });
      expect(dateUtils.getElapsedTimeText(new Date(), null, 'Start')).toEqual({
        durationText: '',
        elapsedHtml: '',
      });
    });

    it('should format duration in seconds when under 1 minute', () => {
      const date = new Date(baseDate.getTime() + 15400);
      expect(dateUtils.getElapsedTimeText(date, baseDate, 'Start')).toEqual({
        durationText: '(+15s)',
        elapsedHtml: '15s after Start',
      });
    });

    it('should format duration in minutes and seconds when between 1 minute and 1 hour', () => {
      const date = new Date(baseDate.getTime() + (5 * 60 + 30) * 1000);
      expect(dateUtils.getElapsedTimeText(date, baseDate, 'Start')).toEqual({
        durationText: '(+5m 30s)',
        elapsedHtml: '5m 30s after Start',
      });
    });

    it('should format duration in hours and minutes when 1 hour or more', () => {
      const date = new Date(
        baseDate.getTime() + (2 * 3600 + 15 * 60 + 40) * 1000,
      );
      expect(dateUtils.getElapsedTimeText(date, baseDate, 'Start')).toEqual({
        durationText: '(+2h 15m)',
        elapsedHtml: '2h 15m after Start',
      });
    });
  });
});
