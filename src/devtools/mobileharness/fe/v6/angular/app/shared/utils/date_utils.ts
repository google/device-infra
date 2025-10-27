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
