/**
 * Date utility functions.
 */
export const dateUtils = {
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
};
