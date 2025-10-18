/**
 * String utility functions.
 */
export const stringUtils = {
  /** Capitalizes the first letter of a string. (e.g., "hello" → "Hello") */
  capitalize: (str: string): string => {
    return str ? str.charAt(0).toUpperCase() + str.slice(1).toLowerCase() : str;
  },

  /**
   * Normalizes a string by capitalizing the first letter of each word and
   * inserting spaces between words.
   *
   * Example: "helloWorld" → "Hello World"
   */
  normalize: (str: string): string => {
    if (!str) return str; // Handle empty input

    // Step 1: Insert spaces between words (handles camelCase/PascalCase)
    // - Adds space before uppercase letters that follow lowercase letters
    const withSpaces = str.replace(/(?<=[a-z])([A-Z])/g, ' $1');

    // Step 2: Split into words (split on spaces, filter out empty strings)
    const words = withSpaces.split(/\s+/).filter((word) => word);

    // Step 3: Capitalize first letter of each word, lowercase the rest
    const capitalizedWords = words.map((word) => {
      if (!word) return '';
      return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
    });

    // Step 4: Join with single spaces
    return capitalizedWords.join(' ');
  },

  /** Converts camelCase to snake_case (e.g., userName → user_name). */
  camelToUnderline: (str: string): string => {
    return str.replace(/[A-Z]/g, (match) => `_${match.toLowerCase()}`);
  },

  /** Converts snake_case to camelCase (e.g., user_name → userName). */
  underlineToCamel: (str: string): string => {
    return str.replace(/_(\w)/g, (_, char) => char.toUpperCase());
  },

  /** Removes all spaces from a string. */
  trimAll: (str: string): string => {
    return str.replace(/\s+/g, '');
  },

  /** Checks if a string is empty (including all-whitespace strings). */
  isEmpty: (str: string): boolean => {
    return str == null || str.trim() === '';
  },
};
