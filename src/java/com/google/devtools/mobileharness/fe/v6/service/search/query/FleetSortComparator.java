/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import com.google.common.base.Ascii;
import java.util.Comparator;
import java.util.List;

/**
 * Case-insensitive comparator family for fleet search sort keys, group headers, and value-picker
 * entries.
 *
 * <ul>
 *   <li>{@link #DECIMAL}: orders leading single-dot numbers ({@code -90}, {@code -1.5}, {@code
 *       0.15}, {@code +1.25}, {@code 1.5}, {@code 18.25}, {@code 2.76GB}, {@code 9.25%}) by exact
 *       signed decimal magnitude, while multi-dot strings ({@code 10.0.2.1} &lt; {@code 10.0.10.1})
 *       and hyphen-suffixed versions ({@code 5.4-android11} &lt; {@code 5.10-android12}) compare
 *       integer segments at arbitrary precision.
 *   <li>{@link #VERSION}: orders unsigned dotted numbers ({@code 0.9} &lt; {@code 0.10} &lt; {@code
 *       1.9} &lt; {@code 1.10}, {@code 17.4} &lt; {@code 17.4.1} &lt; {@code 17.5} &lt; {@code
 *       17.10}, {@code 17.9a} &lt; {@code 17.10a}, {@code 17.4, 18.0} &lt; {@code 17.5} &lt; {@code
 *       17.10, 18.0}) as integer version segments while keeping signed numbers ordered by decimal
 *       magnitude.
 *   <li>{@link #LEXICOGRAPHICAL}: compares case-insensitively by UTF-16 code units without
 *       splitting digit runs so fixed-width hexadecimal identifiers ({@code 09:} &lt; {@code 0a:}
 *       &lt; {@code 10:} &lt; {@code 1a:}) preserve prefix order.
 * </ul>
 *
 * <p>Strings with identical folded token streams tie-break by {@link String#compareTo(String)},
 * making every mode a strict total order consistent with {@link String#equals(Object)}. Empty
 * strings sort after all non-empty strings.
 */
public enum FleetSortComparator implements Comparator<String> {
  DECIMAL,
  VERSION,
  LEXICOGRAPHICAL;

  /**
   * Selects the comparator mode for {@code keyId} in {@code corpus}: {@link #LEXICOGRAPHICAL} for
   * opaque and hexadecimal identifier keys, {@link #VERSION} for version keys or value sets
   * containing multi-segment or hyphen-suffixed version strings, and {@link #DECIMAL} for all other
   * keys.
   */
  public static FleetSortComparator forKey(SearchCorpus corpus, String keyId) {
    if (corpus.isIdentifierKey(keyId)) {
      return LEXICOGRAPHICAL;
    }
    if (isVersionKeyId(keyId)
        || containsVersionFormattedValue(corpus.index().sortedValues(keyId))) {
      return VERSION;
    }
    return DECIMAL;
  }

  /** Returns true when {@code keyId} names a dotted-version field, dimension, or host property. */
  public static boolean isVersionKeyId(String keyId) {
    int sep = keyId.lastIndexOf("::");
    String bare = Ascii.toLowerCase(sep >= 0 ? keyId.substring(sep + 2) : keyId);
    if (bare.equals("sdk_version") || bare.equals("api_level")) {
      return false;
    }
    return bare.equals("version")
        || bare.endsWith("_version")
        || bare.equals("ver")
        || bare.endsWith("_ver");
  }

  /**
   * Returns true when any value in {@code values} begins with a multi-dot version or IP prefix
   * ({@code 17.4.1}, {@code 10.0.2.1}) or a hyphen-suffixed two-part version ({@code
   * 5.10-android12}).
   */
  public static boolean containsVersionFormattedValue(List<String> values) {
    for (String value : values) {
      if (startsWithMultiSegmentOrHyphenVersion(value)) {
        return true;
      }
    }
    return false;
  }

  static boolean startsWithMultiSegmentOrHyphenVersion(String s) {
    int len = s.length();
    int i = 0;
    while (i < len && isDigit(s.charAt(i))) {
      i++;
    }
    if (i == 0 || i + 1 >= len || s.charAt(i) != '.') {
      return false;
    }
    int fracEnd = i + 1;
    while (fracEnd < len && isDigit(s.charAt(fracEnd))) {
      fracEnd++;
    }
    if (fracEnd == i + 1 || fracEnd + 1 >= len) {
      return false;
    }
    char next = s.charAt(fracEnd);
    char afterNext = s.charAt(fracEnd + 1);
    if (next == '.') {
      return isDigit(afterNext);
    }
    if (next == '-') {
      return isDigit(afterNext) || isAsciiAlpha(afterNext);
    }
    return false;
  }

  @Override
  public int compare(String a, String b) {
    if (a.equals(b)) {
      return 0;
    }
    if (a.isEmpty() != b.isEmpty()) {
      return a.isEmpty() ? 1 : -1;
    }
    return compareNonEmpty(a, b);
  }

  /**
   * Compares two non-empty strings under this comparator's mode, breaking ties via {@link
   * String#compareTo(String)}.
   */
  public int compareNonEmpty(String a, String b) {
    return switch (this) {
      case LEXICOGRAPHICAL -> compareLexicographicalNonEmpty(a, b);
      case DECIMAL -> compareTokenizedNonEmpty(a, b, /* decimalMode= */ true);
      case VERSION -> compareTokenizedNonEmpty(a, b, /* decimalMode= */ false);
    };
  }

  /**
   * Compares two non-empty strings case-insensitively by UTF-16 code unit order without splitting
   * digit runs, breaking ties on identical lowercased text via {@link String#compareTo(String)}.
   */
  public static int compareLexicographicalNonEmpty(String a, String b) {
    if (a.equals(b)) {
      return 0;
    }
    int minLen = Math.min(a.length(), b.length());
    for (int i = 0; i < minLen; i++) {
      char ca = Ascii.toLowerCase(a.charAt(i));
      char cb = Ascii.toLowerCase(b.charAt(i));
      if (ca != cb) {
        return Character.compare(ca, cb);
      }
    }
    if (a.length() != b.length()) {
      return Integer.compare(a.length(), b.length());
    }
    return a.compareTo(b);
  }

  private static int compareTokenizedNonEmpty(String a, String b, boolean decimalMode) {
    if (a.equals(b)) {
      return 0;
    }
    int lenA = a.length();
    int lenB = b.length();
    int posA = 0;
    int posB = 0;

    while (posA < lenA && posB < lenB) {
      boolean numA = startsNumberToken(a, posA);
      boolean numB = startsNumberToken(b, posB);

      if (!numA && !numB) {
        char ca = Ascii.toLowerCase(a.charAt(posA));
        char cb = Ascii.toLowerCase(b.charAt(posB));
        if (ca != cb) {
          return Character.compare(ca, cb);
        }
        posA++;
        posB++;
        continue;
      }

      if (numA != numB) {
        if (numA) {
          char cb = Ascii.toLowerCase(b.charAt(posB));
          return cb < '0' ? 1 : -1;
        }
        char ca = Ascii.toLowerCase(a.charAt(posA));
        return ca < '0' ? -1 : 1;
      }

      int endA = tokenEnd(a, posA, decimalMode);
      int endB = tokenEnd(b, posB, decimalMode);
      int cmp = compareNumberTokens(a, posA, endA, b, posB, endB);
      if (cmp != 0) {
        return cmp;
      }
      posA = endA;
      posB = endB;
    }

    if (posA < lenA || posB < lenB) {
      return posA < lenA ? 1 : -1;
    }
    return a.compareTo(b);
  }

  private static boolean startsNumberToken(String s, int pos) {
    char c = s.charAt(pos);
    if (isDigit(c)) {
      return true;
    }
    return pos == 0 && (c == '-' || c == '+') && s.length() > 1 && isDigit(s.charAt(1));
  }

  /**
   * Returns the exclusive end index of the number token starting at {@code pos}.
   *
   * <p>At index 0, a single fractional {@code .digits} suffix (not followed by another {@code '.'})
   * is included in the token when the number carries an explicit sign or when {@code decimalMode}
   * is active without a hyphen version suffix.
   */
  private static int tokenEnd(String s, int pos, boolean decimalMode) {
    int len = s.length();
    boolean signed = pos == 0 && (s.charAt(0) == '-' || s.charAt(0) == '+');
    int i = signed ? 1 : pos;
    while (i < len && isDigit(s.charAt(i))) {
      i++;
    }
    if (pos == 0 && i < len && s.charAt(i) == '.') {
      int fracEnd = i + 1;
      while (fracEnd < len && isDigit(s.charAt(fracEnd))) {
        fracEnd++;
      }
      if (fracEnd > i + 1 && (fracEnd == len || s.charAt(fracEnd) != '.')) {
        boolean hyphenDelimited = fracEnd < len && s.charAt(fracEnd) == '-';
        if (signed || (decimalMode && !hyphenDelimited)) {
          return fracEnd;
        }
      }
    }
    return i;
  }

  private static boolean isAsciiAlpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static int compareNumberTokens(
      String a, int posA, int endA, String b, int posB, int endB) {
    int subGroupA = numberSubGroup(a, posA, endA);
    int subGroupB = numberSubGroup(b, posB, endB);
    if (subGroupA != subGroupB) {
      return Integer.compare(subGroupA, subGroupB);
    }
    return switch (subGroupA) {
      case -1 -> -compareDecimalMagnitudes(a, posA, endA, b, posB, endB);
      case 0 -> 0;
      default -> compareDecimalMagnitudes(a, posA, endA, b, posB, endB);
    };
  }

  private static int numberSubGroup(String s, int pos, int end) {
    boolean negative = (pos == 0 && s.charAt(0) == '-');
    int digitStart = (pos == 0 && (s.charAt(0) == '-' || s.charAt(0) == '+')) ? 1 : pos;
    int dot = pos == 0 ? indexOfDot(s, digitStart, end) : end;

    int sigIntStart = skipZeros(s, digitStart, dot);
    int fracEnd = dot < end ? trimTrailingZeros(s, dot + 1, end) : end;
    if (sigIntStart == dot && (dot == end || fracEnd == dot + 1)) {
      return 0;
    }
    return negative ? -1 : 1;
  }

  private static int compareDecimalMagnitudes(
      String a, int posA, int endA, String b, int posB, int endB) {
    int startA = (posA == 0 && (a.charAt(0) == '-' || a.charAt(0) == '+')) ? 1 : posA;
    int startB = (posB == 0 && (b.charAt(0) == '-' || b.charAt(0) == '+')) ? 1 : posB;
    int dotA = posA == 0 ? indexOfDot(a, startA, endA) : endA;
    int dotB = posB == 0 ? indexOfDot(b, startB, endB) : endB;

    int intStartA = skipZeros(a, startA, dotA);
    int intStartB = skipZeros(b, startB, dotB);
    int intCmp = compareDigitSlicesAsInteger(a, intStartA, dotA, b, intStartB, dotB);
    if (intCmp != 0) {
      return intCmp;
    }

    int fracStartA = dotA < endA ? dotA + 1 : endA;
    int fracEndA = dotA < endA ? trimTrailingZeros(a, fracStartA, endA) : endA;
    int fracStartB = dotB < endB ? dotB + 1 : endB;
    int fracEndB = dotB < endB ? trimTrailingZeros(b, fracStartB, endB) : endB;
    return compareDigitSlicesLexicographically(a, fracStartA, fracEndA, b, fracStartB, fracEndB);
  }

  private static int compareDigitSlicesAsInteger(
      String a, int startA, int endA, String b, int startB, int endB) {
    int lenA = endA - startA;
    int lenB = endB - startB;
    if (lenA != lenB) {
      return Integer.compare(lenA, lenB);
    }
    for (int i = 0; i < lenA; i++) {
      char ca = a.charAt(startA + i);
      char cb = b.charAt(startB + i);
      if (ca != cb) {
        return Character.compare(ca, cb);
      }
    }
    return 0;
  }

  private static int compareDigitSlicesLexicographically(
      String a, int startA, int endA, String b, int startB, int endB) {
    int lenA = endA - startA;
    int lenB = endB - startB;
    int minLen = Math.min(lenA, lenB);
    for (int i = 0; i < minLen; i++) {
      char ca = a.charAt(startA + i);
      char cb = b.charAt(startB + i);
      if (ca != cb) {
        return Character.compare(ca, cb);
      }
    }
    return Integer.compare(lenA, lenB);
  }

  private static int indexOfDot(String s, int start, int end) {
    for (int i = start; i < end; i++) {
      if (s.charAt(i) == '.') {
        return i;
      }
    }
    return end;
  }

  private static int skipZeros(String s, int start, int end) {
    int i = start;
    while (i < end && s.charAt(i) == '0') {
      i++;
    }
    return i;
  }

  private static int trimTrailingZeros(String s, int start, int end) {
    int i = end;
    while (i > start && s.charAt(i - 1) == '0') {
      i--;
    }
    return i;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }
}
