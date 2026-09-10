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

    it('should parse timestamp with UTC suffix', () => {
      const parsed = dateUtils.parseUtcTimestamp('2025-07-09 10:11:15 UTC');
      expect(parsed.toISOString()).toBe('2025-07-09T10:11:15.000Z');
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


  describe('pacificToUtc', () => {
    it('should convert Pacific Time datetime-local string to UTC ISO string during PDT and PST', () => {
      // PDT test (July, UTC-7)
      expect(dateUtils.pacificToUtc('2026-07-15T14:30')).toBe(
        '2026-07-15T21:30:00.000Z',
      );

      // PST test (January, UTC-8)
      expect(dateUtils.pacificToUtc('2026-01-15T14:30')).toBe(
        '2026-01-15T22:30:00.000Z',
      );
    });

    it('should handle space separator, timezone suffixes, and date-only inputs', () => {
      expect(dateUtils.pacificToUtc('2026-07-15 14:30')).toBe(
        '2026-07-15T21:30:00.000Z',
      );
      expect(dateUtils.pacificToUtc('2026-07-15 14:30 (PDT)')).toBe(
        '2026-07-15T21:30:00.000Z',
      );
      expect(dateUtils.pacificToUtc('2026-07-15')).toBe(
        '2026-07-15T07:00:00.000Z',
      );
    });

    it('returns empty string for missing or invalid inputs', () => {
      expect(dateUtils.pacificToUtc('')).toBe('');
      expect(dateUtils.pacificToUtc(null)).toBe('');
      expect(dateUtils.pacificToUtc('invalid')).toBe('');
      expect(dateUtils.pacificToUtc('2026-99-99')).toBe('');
    });

    it('returns valid ISO string if input is already a UTC ISO string with Z', () => {
      expect(dateUtils.pacificToUtc('2026-07-15T21:30:00.000Z')).toBe(
        '2026-07-15T21:30:00.000Z',
      );
      expect(dateUtils.pacificToUtc('2026-07-15T21:30:00Z')).toBe(
        '2026-07-15T21:30:00.000Z',
      );
    });
  });

  describe('formatDetailedLocal', () => {
    it('should format Date to detailed local string', () => {
      const date = new Date('2025-07-09T10:11:15Z');
      const result = dateUtils.formatDetailedLocal(date);
      expect(result).toBeTruthy();
      expect(result).toContain('2025');
    });
  });

  describe('formatFileTimestamp', () => {
    it('should format Date or ISO string into YYYYMMDD_hhmm', () => {
      const date = new Date(2026, 6, 15, 14, 30);
      expect(dateUtils.formatFileTimestamp(date)).toBe('20260715_1430');
      expect(dateUtils.formatFileTimestamp('2026-07-15T14:30:00')).toBe(
        '20260715_1430',
      );
    });
  });

  describe('formatDateRange', () => {
    it('should format start and end date range', () => {
      const start = new Date(2026, 0, 1);
      const end = new Date(2026, 0, 15);
      const formatted = dateUtils.formatDateRange(start, end);
      expect(formatted).toContain('Jan 1, 2026 - Jan 15, 2026');
    });
  });

  describe('formatTimeAgo', () => {
    it('should format relative elapsed time strings', () => {
      expect(dateUtils.formatTimeAgo(null)).toBe('');
      expect(dateUtils.formatTimeAgo('')).toBe('');
      expect(dateUtils.formatTimeAgo('invalid')).toBe('unknown time ago');

      const now = Date.now();
      expect(dateUtils.formatTimeAgo(new Date(now - 10000).toISOString())).toBe(
        'just now',
      );
      expect(
        dateUtils.formatTimeAgo(new Date(now - 10 * 60 * 1000).toISOString()),
      ).toBe('10m ago');
      expect(
        dateUtils.formatTimeAgo(
          new Date(now - 3 * 3600 * 1000).toISOString(),
        ),
      ).toBe('3h ago');
      expect(
        dateUtils.formatTimeAgo(
          new Date(now - 2 * 24 * 3600 * 1000).toISOString(),
        ),
      ).toBe('2d ago');
    });
  });

  describe('toGoogleDate', () => {
    it('should convert string and Date objects to GoogleDate format', () => {
      expect(dateUtils.toGoogleDate('2026-07-15')).toEqual({
        year: 2026,
        month: 7,
        day: 15,
      });

      const date = new Date(2026, 6, 15);
      expect(dateUtils.toGoogleDate(date)).toEqual({
        year: 2026,
        month: 7,
        day: 15,
      });
    });
  });

  describe('utcToPacific', () => {
    it('should normalize various UTC formats into Pacific Time datetime-local string', () => {
      expect(dateUtils.utcToPacific('')).toBe('');
      expect(dateUtils.utcToPacific(null)).toBe('');
      expect(dateUtils.utcToPacific(undefined)).toBe('');
      expect(dateUtils.utcToPacific('2026-01-01T12:00')).toBe('2026-01-01T12:00');
      expect(dateUtils.utcToPacific('2026-01-01 12:00')).toBe('2026-01-01T12:00');
      expect(dateUtils.utcToPacific('2026-01-01')).toBe('2026-01-01T00:00');
      expect(dateUtils.utcToPacific('2026-07-15T21:30:00.000Z')).toBe(
        '2026-07-15T14:30',
      );
      expect(dateUtils.utcToPacific('2026-07-15 21:30:00 UTC')).toBe(
        '2026-07-15T14:30',
      );
      // Epoch millisecond timestamp
      expect(dateUtils.utcToPacific(1784151000000)).toBe('2026-07-15T14:30');
      expect(dateUtils.utcToPacific('1784151000000')).toBe('2026-07-15T14:30');
      // Date object
      expect(dateUtils.utcToPacific(new Date('2026-07-15T21:30:00.000Z'))).toBe(
        '2026-07-15T14:30',
      );
    });
  });
});
