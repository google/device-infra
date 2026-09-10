/**
 * Represents a date without time, corresponding to google.type.Date.
 */
export interface GoogleDate {
  year: number;
  month: number;
  day: number;
}

const PDT_FORMATTER = new Intl.DateTimeFormat('en-US', {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  second: '2-digit',
  hour12: true,
  timeZone: 'America/Los_Angeles',
  timeZoneName: 'short',
});

const DETAILED_LOCAL_FORMATTER = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  timeZoneName: 'short',
});

const DETAILED_UTC_FORMATTER = new Intl.DateTimeFormat('en-US', {
  weekday: 'short',
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  timeZone: 'UTC',
  timeZoneName: 'short',
});

const PACIFIC_TIMEZONE = 'America/Los_Angeles';

const PACIFIC_DATE_FORMATTER = new Intl.DateTimeFormat('en-CA', {
  timeZone: PACIFIC_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const PACIFIC_TIME_FORMATTER = new Intl.DateTimeFormat('en-GB', {
  timeZone: PACIFIC_TIMEZONE,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});


/**
 * Date utility functions.
 */
export const dateUtils = {
  /**
   * Parses a UTC timestamp string (from API data) into a Date object.
   */
  parseUtcTimestamp: (val: string | null): Date => {
    if (!val) return new Date(NaN);
    const trimmed = val.trim();
    if (!trimmed) return new Date(NaN);

    // Human-readable date strings (e.g. "Jul 9, 2025, 11:30:00 AM")
    if (!trimmed.includes('-') && !trimmed.includes('T')) {
      const withUtc = /GMT|UTC|PDT|PST/i.test(trimmed)
        ? trimmed
        : `${trimmed} UTC`;
      return new Date(withUtc);
    }

    // Standard ISO-8601 strings (e.g. "2025-07-09 10:11:15", "2025-07-09T10:11:15Z", "2025-07-09 10:11:15 UTC")
    const isoStr = trimmed
      .replace(/\s*(UTC|GMT)$/i, '')
      .replace(/Z$/i, '')
      .replace(' ', 'T');
    return new Date(`${isoStr}Z`);
  },

  /**
   * Formats a date object to PDT format: e.g. Jul 9, 2025, 10:11:15 AM PDT.
   */
  formatPdt: (date: Date): string => {
    return PDT_FORMATTER.format(date);
  },

  /**
   * Formats a date object to a detailed local string including weekday and timezone name.
   */
  formatDetailedLocal: (date: Date): string => {
    return DETAILED_LOCAL_FORMATTER.format(date);
  },

  /**
   * Formats a date object to a detailed UTC string including weekday and timezone name.
   */
  formatDetailedUtc: (date: Date): string => {
    return DETAILED_UTC_FORMATTER.format(date);
  },

  /**
   * Calculates the difference between two Dates and formats it for presentation as elapsed duration.
   */
  getElapsedTimeText: (
    date: Date,
    baseDate: Date | null,
    baseLabel: string,
  ): {durationText: string; elapsedHtml: string} => {
    if (!baseDate || isNaN(baseDate.getTime()) || isNaN(date.getTime())) {
      return {durationText: '', elapsedHtml: ''};
    }

    const diffMs = date.getTime() - baseDate.getTime();
    const diffSec = Math.round(diffMs / 1000);

    let durationText = '';
    let elapsedHtml = '';

    if (diffSec >= 0 && diffSec < 60) {
      durationText = `(+${diffSec}s)`;
      elapsedHtml = `${diffSec}s after ${baseLabel}`;
    } else if (diffSec >= 60 && diffSec < 3600) {
      const m = Math.floor(diffSec / 60);
      const s = diffSec % 60;
      durationText = `(+${m}m ${s}s)`;
      elapsedHtml = `${m}m ${s}s after ${baseLabel}`;
    } else if (diffSec >= 3600) {
      const h = Math.floor(diffSec / 3600);
      const m = Math.floor((diffSec % 3600) / 60);
      durationText = `(+${h}h ${m}m)`;
      elapsedHtml = `${h}h ${m}m after ${baseLabel}`;
    }

    return {durationText, elapsedHtml};
  },

  /**
   * Formats a date object to a string.
   *
   * @param date The date object to format.
   * @param options The format options to use.
   * @return The formatted date string.
   */
  format: (
    date: string | null,
    options: Intl.DateTimeFormatOptions = {},
  ): string => {
    if (!date) return 'Unknown';

    const formatOptions: Intl.DateTimeFormatOptions = {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      timeZoneName: 'short',
      ...options,
    };

    return new Date(date).toLocaleString('en-US', formatOptions);
  },

  /**
   * Formats a date object to a YYYYMMDD_hhmm string.
   * @param date The date object or string to format.
   * @return The formatted date string.
   */
  formatFileTimestamp: (date: Date | string): string => {
    const d = typeof date === 'string' ? new Date(date) : date;
    const YYYY = d.getFullYear();
    const MM = String(d.getMonth() + 1).padStart(2, '0');
    const DD = String(d.getDate()).padStart(2, '0');
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return `${YYYY}${MM}${DD}_${hh}${mm}`;
  },

  /**
   * Formats a date range.
   *
   * @param start The start date.
   * @param end The end date.
   * @param options The format options to use.
   * @return The formatted date range string.
   */
  formatDateRange: (
    start: Date,
    end: Date,
    options: Intl.DateTimeFormatOptions = {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    },
  ): string => {
    return `${start.toLocaleDateString('en-US', options)} - ${end.toLocaleDateString('en-US', options)}`;
  },

  /**
   * Formats a date object to a string.
   *
   * @param date The date object to format.
   * @return The formatted date string.
   */
  formatTimeAgo: (date: string | null): string => {
    if (!date) return '';

    const startTime = new Date(date);
    const diffMs = new Date().getTime() - startTime.getTime();
    if (isNaN(diffMs)) return 'unknown time ago';

    const diffSeconds = Math.round(diffMs / 1000);
    const diffMinutes = Math.round(diffSeconds / 60);
    const diffHours = Math.round(diffMinutes / 60);
    const diffDays = Math.round(diffHours / 24);

    if (diffSeconds < 60) return 'just now';
    if (diffMinutes < 60) return `${diffMinutes}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    return `${diffDays}d ago`;
  },

  /**
   * Converts a date string or object to a Google Date object (year, month, day).
   *
   * @param date The date object or string to convert.
   * @return The Google Date object.
   */
  toGoogleDate: (date: Date | string): GoogleDate => {
    if (typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
      const [year, month, day] = date.split('-').map(Number);
      return {year, month, day};
    }
    const d = typeof date === 'string' ? new Date(date) : date;
    return {
      year: d.getFullYear(),
      month: d.getMonth() + 1,
      day: d.getDate(),
    };
  },

  /**
   * Returns the active Pacific timezone abbreviation ('PDT' or 'PST') for a given date/timestamp.
   *
   * @param dateOrMs Date or epoch timestamp in milliseconds (defaults to now).
   * @return 'PDT' or 'PST'.
   */
  getPacificTimezoneName: (dateOrMs: Date | number = Date.now()): string => {
    const d = typeof dateOrMs === 'number' ? new Date(dateOrMs) : dateOrMs;
    const str = d.toLocaleTimeString('en-US', {
      timeZone: PACIFIC_TIMEZONE,
      timeZoneName: 'short',
    });
    const match = str.match(/\b(PDT|PST)\b/i);
    return match ? match[1].toUpperCase() : 'PDT';
  },


  /**
   * Converts a Pacific Time (America/Los_Angeles) datetime string to an RFC 3339 UTC ISO-8601 string.
   * Outbound transformation (UI presentation -> Backend RPC / URL state).
   *
   * @param dateTimeStr Pacific Time datetime string (e.g. "2026-07-15T14:30", "2026-07-15 14:30", "2026-07-15", or UTC ISO string).
   * @return UTC ISO-8601 formatted string (e.g. "2026-07-15T21:30:00.000Z"), or empty string on invalid input.
   */
  pacificToUtc: (dateTimeStr?: string | null): string => {
    if (!dateTimeStr) return '';
    const trimmed = String(dateTimeStr).trim();
    if (!trimmed) return '';

    // 1. If already in UTC ISO format (e.g. ending with Z)
    if (trimmed.endsWith('Z') || trimmed.endsWith('z')) {
      const d = new Date(trimmed);
      return isNaN(d.getTime()) ? '' : d.toISOString();
    }

    // 2. Parse "YYYY-MM-DD" or "YYYY-MM-DD[T| ]HH:MM(:SS)?" (ignoring optional timezone suffix)
    const match = trimmed.match(
      /^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2}))?)?/i,
    );
    if (!match) return '';

    const [, yStr, mStr, dStr, hStr = '0', minStr = '0', sStr = '0'] = match;
    const [y, m, d, h, min, s] = [
      Number(yStr),
      Number(mStr),
      Number(dStr),
      Number(hStr),
      Number(minStr),
      Number(sStr),
    ];

    if (m < 1 || m > 12 || d < 1 || d > 31 || h > 23 || min > 59 || s > 59) {
      return '';
    }

    // 3. Exact Pacific offset inversion
    const approxUtcMs = Date.UTC(y, m - 1, d, h, min, s);
    const pLocal = dateUtils.utcToPacific(approxUtcMs);
    const [pDate, pTime] = pLocal.split('T');
    const [py, pm, pd] = pDate.split('-').map(Number);
    const [ph, pmin] = pTime.split(':').map(Number);
    const pacificAsUtcMs = Date.UTC(py, pm - 1, pd, ph, pmin, s);
    const offsetMs = approxUtcMs - pacificAsUtcMs;
    return new Date(approxUtcMs + offsetMs).toISOString();
  },

  /**
   * Converts a UTC timestamp (ISO string, epoch ms, Date) to Pacific Time "YYYY-MM-DDTHH:MM".
   * Inbound transformation (Backend RPC / URL state -> UI presentation).
   *
   * @param raw UTC timestamp representation.
   * @return Pacific Time datetime string (YYYY-MM-DDTHH:MM), or empty string on invalid input.
   */
  utcToPacific: (raw?: string | number | Date | null): string => {
    if (!raw) return '';

    // If already in Pacific format ("YYYY-MM-DD" / "YYYY-MM-DDTHH:MM"), return directly without timezone shift
    if (typeof raw === 'string' && /^\d{4}-\d{2}-\d{2}([T\s]\d{2}:\d{2})?$/.test(raw.trim())) {
      const s = raw.trim().replace(' ', 'T');
      return s.length === 10 ? `${s}T00:00` : s.substring(0, 16);
    }

    let d: Date;
    if (raw instanceof Date) {
      d = raw;
    } else if (typeof raw === 'number' || /^\d{10,13}$/.test(String(raw).trim())) {
      d = new Date(Number(raw));
    } else {
      d = dateUtils.parseUtcTimestamp(String(raw));
    }

    if (isNaN(d.getTime())) return '';
    const date = PACIFIC_DATE_FORMATTER.format(d);
    const time = PACIFIC_TIME_FORMATTER.format(d).replace(/^24:/, '00:');
    return `${date}T${time}`;
  },
};
