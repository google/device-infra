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

/**
 * Date utility functions.
 */
export const dateUtils = {
  /**
   * Parses a UTC timestamp string (from API data) into a Date object.
   */
  parseUtcTimestamp: (val: string | null): Date => {
    if (!val) return new Date(NaN);

    // For formatted human-readable date strings (e.g. "Jul 9, 2025, 11:30:00 AM")
    if (!val.includes('-') && !val.includes('T')) {
      const cleanVal = /GMT|UTC|PDT|PST/i.test(val) ? val : `${val} UTC`;
      return new Date(cleanVal);
    }

    // For ISO-8601 strings (e.g. "2025-07-09 10:11:15" or "2025-07-09T10:11:15Z")
    let cleanVal = val.endsWith('Z') ? val.slice(0, -1) : val;
    cleanVal = cleanVal.replace(' ', 'T');
    return new Date(`${cleanVal}Z`);
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
   * Formats a date object to a YYYY-MM-DD string.
   *
   * @param date The date object to format.
   * @return The formatted date string.
   */
  formatDate: (date: Date): string => {
    const year = date.getFullYear();
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');
    return `${year}-${month}-${day}`;
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
   * Formats duration milliseconds into a readable scale representation (h/m/s).
   */
  formatDuration: (val: string | number | undefined | null): string | null => {
    if (!val) return null;
    const num = Number(val);
    if (isNaN(num)) return String(val) || null;
    const sec = Math.round(num / 1000);
    if (sec < 60) return `${sec}s`;
    const min = Math.floor(sec / 60);
    const remSec = sec % 60;
    if (min < 60) return remSec > 0 ? `${min}m ${remSec}s` : `${min}m`;
    const hrs = Math.floor(min / 60);
    const remMin = min % 60;
    return remMin > 0 ? `${hrs}h ${remMin}m` : `${hrs}h`;
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
   * @returns 'PDT' or 'PST'.
   */
  getPacificTimezoneName: (dateOrMs: Date | number = Date.now()): string => {
    const d = typeof dateOrMs === 'number' ? new Date(dateOrMs) : dateOrMs;
    const str = d.toLocaleTimeString('en-US', {
      timeZone: 'America/Los_Angeles',
      timeZoneName: 'short',
    });
    const match = str.match(/\b(PDT|PST)\b/i);
    return match ? match[1].toUpperCase() : 'PDT';
  },

  /**
   * Formats millisecond timestamp into datetime-local HTML input format YYYY-MM-DDTHH:MM in Pacific Time (America/Los_Angeles).
   *
   * @param ms Epoch timestamp in milliseconds.
   * @returns Formatted datetime-local string in Pacific Time.
   */
  toDateTimeLocalString: (ms: number): string => {
    const d = new Date(ms);
    const dateStr = d.toLocaleDateString('en-US', {
      timeZone: 'America/Los_Angeles',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    const timeStr = d.toLocaleTimeString('en-US', {
      timeZone: 'America/Los_Angeles',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
    const [mm, dd, yyyy] = dateStr.split('/');
    let [hh, min] = timeStr.split(':');
    if (hh === '24') hh = '00';
    return `${yyyy}-${mm.padStart(2, '0')}-${dd.padStart(2, '0')}T${hh.padStart(2, '0')}:${min.padStart(2, '0')}`;
  },

  /**
   * Converts a datetime-local string in Pacific Time (America/Los_Angeles) to UTC ISO-8601 string.
   *
   * @param dateTimeStr Datetime string formatted as YYYY-MM-DDTHH:MM.
   * @returns UTC ISO-8601 formatted string (e.g. 2026-07-15T21:30:00.000Z).
   */
  pdtDateTimeToUtcIso: (dateTimeStr: string): string => {
    if (!dateTimeStr) return '';
    const [datePart, timePart] = dateTimeStr.split('T');
    if (!datePart || !timePart) return '';
    const [y, m, d] = datePart.split('-').map(Number);
    const [h, min] = timePart.split(':').map(Number);

    const approxUtcMs = Date.UTC(y, m - 1, d, h, min);
    const dt = new Date(approxUtcMs);
    const laStr = dt.toLocaleString('en-US', {
      timeZone: 'America/Los_Angeles',
      hour12: false,
    });
    const localMatch = laStr.match(/(\d+)\/(\d+)\/(\d+),\s+(\d+):(\d+):(\d+)/);
    if (!localMatch) {
      return new Date(Date.UTC(y, m - 1, d, h, min)).toISOString();
    }
    const [_, lM, lD, lY, lH, lMin, lS] = localMatch.map(Number);
    const laAsUtc = Date.UTC(lY, lM - 1, lD, lH, lMin, lS);
    const offsetMs = approxUtcMs - laAsUtc;
    const realUtcMs = approxUtcMs + offsetMs;
    return new Date(realUtcMs).toISOString();
  },
};
