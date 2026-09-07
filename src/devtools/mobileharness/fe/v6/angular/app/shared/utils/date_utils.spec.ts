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

  describe('parseUtcTimestamp', () => {
    it('should parse UTC timestamp with space separator', () => {
      const parsed = dateUtils.parseUtcTimestamp('2025-07-09 10:11:15');
      expect(parsed.toISOString()).toBe('2025-07-09T10:11:15.000Z');
    });

    it('should parse UTC timestamp with T separator and Z suffix', () => {
      const parsed = dateUtils.parseUtcTimestamp('2025-07-09T10:11:15Z');
      expect(parsed.toISOString()).toBe('2025-07-09T10:11:15.000Z');
    });

    it('should parse timestamp during winter time', () => {
      const parsed = dateUtils.parseUtcTimestamp('2025-01-15 10:11:15');
      expect(parsed.toISOString()).toBe('2025-01-15T10:11:15.000Z');
    });

    it('should parse formatted timestamp without hyphen', () => {
      const parsed = dateUtils.parseUtcTimestamp('Jul 9, 2025, 11:30:00 AM');
      expect(parsed.toISOString()).toBe('2025-07-09T11:30:00.000Z');
    });

    it('should return invalid date for invalid inputs', () => {
      expect(isNaN(dateUtils.parseUtcTimestamp(null).getTime())).toBeTrue();
      expect(
        isNaN(dateUtils.parseUtcTimestamp('invalid').getTime()),
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

  describe('formatDetailedUtc', () => {
    it('should format Date to detailed UTC string', () => {
      const date = new Date('2025-07-09T10:11:15Z');
      expect(dateUtils.formatDetailedUtc(date)).toMatch(
        /Jul 9, 2025, 10:11:15\s+AM\s+UTC/,
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

  describe('getPacificTimezoneName', () => {
    it('should return PDT in summer and PST in winter', () => {
      expect(dateUtils.getPacificTimezoneName(Date.UTC(2026, 6, 15))).toBe(
        'PDT',
      );
      expect(dateUtils.getPacificTimezoneName(Date.UTC(2026, 0, 15))).toBe(
        'PST',
      );
    });
  });

  describe('toDateTimeLocalString', () => {
    it('should format ms to Pacific Time datetime-local string', () => {
      // 2026-01-15 22:30 UTC is 2026-01-15 14:30 PST (UTC-8)
      const pstMs = Date.UTC(2026, 0, 15, 22, 30);
      expect(dateUtils.toDateTimeLocalString(pstMs)).toBe('2026-01-15T14:30');

      // 2026-07-15 21:30 UTC is 2026-07-15 14:30 PDT (UTC-7)
      const pdtMs = Date.UTC(2026, 6, 15, 21, 30);
      expect(dateUtils.toDateTimeLocalString(pdtMs)).toBe('2026-07-15T14:30');
    });
  });

  describe('pdtDateTimeToUtcIso', () => {
    it('should convert Pacific Time datetime-local string to UTC ISO string during PDT and PST', () => {
      // PDT test (July, UTC-7)
      expect(dateUtils.pdtDateTimeToUtcIso('2026-07-15T14:30')).toBe(
        '2026-07-15T21:30:00.000Z',
      );

      // PST test (January, UTC-8)
      expect(dateUtils.pdtDateTimeToUtcIso('2026-01-15T14:30')).toBe(
        '2026-01-15T22:30:00.000Z',
      );
    });

    it('returns empty string for missing or invalid inputs without T separator', () => {
      expect(dateUtils.pdtDateTimeToUtcIso('')).toBe('');
      expect(dateUtils.pdtDateTimeToUtcIso('invalid')).toBe('');
      expect(dateUtils.pdtDateTimeToUtcIso('2026-07-15')).toBe('');
      expect(dateUtils.pdtDateTimeToUtcIso('T14:30')).toBe('');
      expect(dateUtils.pdtDateTimeToUtcIso('2026-07-15T')).toBe('');
    });

    it('falls back to approx UTC ISO string if toLocaleString format does not match regex (line 321)', () => {
      const origToLocaleString = Date.prototype.toLocaleString;
      spyOn(Date.prototype, 'toLocaleString').and.returnValue(
        'non-matching-format',
      );
      try {
        const result = dateUtils.pdtDateTimeToUtcIso('2026-07-15T14:30');
        expect(result).toBe(
          new Date(Date.UTC(2026, 6, 15, 14, 30)).toISOString(),
        );
      } finally {
        Date.prototype.toLocaleString = origToLocaleString;
      }
    });
  });
});
