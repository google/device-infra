/** One pass-through flag split into its name and value. */
export interface ParsedPassThroughFlag {
  /** The flag name, such as `--foo`. */
  readonly name: string;
  /**
   * The value with its leading `=` or whitespace separator, such as `=1` or
   * ` bar`, or `''` for a flag without a value.
   */
  readonly value: string;
}

/**
 * Matches a balanced quoted string, a run of whitespace, a run of other
 * characters, or a lone quote character, so the tokens always add up to the
 * whole input.
 */
const FLAG_TOKEN = /"[^"]*"|'[^']*'|\s+|[^\s"']+|["']/g;

/**
 * Splits a raw pass-through flags string into one string per flag. A new flag
 * starts at whitespace followed by `--` outside quotes, so `--foo bar` stays
 * one flag and a quoted value may contain ` --`.
 */
export function splitPassThroughFlags(
  rawFlags: string | null | undefined,
): string[] {
  const text = (rawFlags ?? '').trim();
  const flags: string[] = [];
  let current = '';
  let end = 0;
  for (const [token] of text.matchAll(FLAG_TOKEN)) {
    end += token.length;
    if (/^\s/.test(token) && text.startsWith('--', end)) {
      flags.push(current);
      current = '';
    } else {
      current += token;
    }
  }
  if (current) {
    flags.push(current);
  }
  return flags;
}

/** Splits one flag at its first `=` or whitespace into its name and value. */
export function parsePassThroughFlag(flag: string): ParsedPassThroughFlag {
  const separator = flag.search(/[=\s]/);
  if (separator <= 0) {
    return {name: flag, value: ''};
  }
  return {name: flag.slice(0, separator), value: flag.slice(separator)};
}

/** Parses a raw pass-through flags string into flag names and values. */
export function parsePassThroughFlags(
  rawFlags: string | null | undefined,
): ParsedPassThroughFlag[] {
  return splitPassThroughFlags(rawFlags).map((flag) =>
    parsePassThroughFlag(flag),
  );
}
