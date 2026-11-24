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

package com.google.devtools.mobileharness.platform.android.app;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.time.Duration;
import java.util.IllformedLocaleException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Utility methods for controlling the activity manager (am) of Android devices/emulators. */
public class ActivityManager {
  /** Short timeout for quick operations. */
  @VisibleForTesting static final Duration ADB_SHELL_TIMEOUT = Duration.ofSeconds(30);

  /** Adb shell command to get config. */
  @VisibleForTesting static final String ADB_SHELL_GET_CONFIG = "am get-config";

  private static final Pattern CONFIG_LINE_PATTERN =
      Pattern.compile("(?m)^config:[ \\t]+([^\\r\\n]+)$");
  private static final Pattern MCC_MNC_PREFIX_PATTERN =
      Pattern.compile("^(?:mcc\\d+-)?(?:mnc\\d+-)?");
  private static final Pattern BCP47_LOCALE_HEAD_PATTERN =
      Pattern.compile("^(b\\+[A-Za-z0-9]+(?:\\+[A-Za-z0-9]+)*)(?:$|,[^,-]|-[A-Za-z0-9])");
  private static final Pattern TRADITIONAL_LOCALE_HEAD_PATTERN =
      Pattern.compile("^([a-z]{2}(?:-r[A-Z]{2})?)(?:$|,[^,-]|-(?:[A-Za-qs-z0-9]|r[a-z]))");

  private final Adb adb;

  /** Structured locale information containing canonical BCP-47 tag, language, and region. */
  @AutoValue
  public abstract static class Locale {

    private static final ImmutableMap<String, String> LEGACY_LANGUAGE_CODES =
        ImmutableMap.of(
            "iw", "he",
            "in", "id",
            "ji", "yi");

    public static Locale create(String locale) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(locale), "Locale must not be empty.");
      java.util.Locale parsed = new java.util.Locale.Builder().setLanguageTag(locale).build();
      String language = canonicalizeLanguage(parsed.getLanguage());
      Preconditions.checkArgument(
          !language.isEmpty() && !language.equals("und"), "Invalid locale: %s", locale);
      String region = Ascii.toUpperCase(parsed.getCountry());
      String canonicalTag =
          new java.util.Locale.Builder()
              .setLocale(parsed)
              .setLanguage(language)
              .build()
              .toLanguageTag();
      return new AutoValue_ActivityManager_Locale(canonicalTag, language, region);
    }

    /**
     * Canonicalized BCP-47 language tag (for example, {@code en-US}, {@code ja}, {@code
     * zh-Hans-CN}).
     */
    public abstract String locale();

    /**
     * Canonicalized lowercase ISO 639 language code (normalizing legacy {@code iw}, {@code in}, and
     * {@code ji} to {@code he}, {@code id}, and {@code yi}).
     */
    public abstract String language();

    /** Uppercase ISO 3166-1 / UN M.49 region code, or empty string if absent. */
    public abstract String region();

    /**
     * Returns whether this locale matches the requested {@code targetLanguage} and optional {@code
     * targetCountry}.
     */
    public boolean matches(String targetLanguage, @Nullable String targetCountry) {
      if (Strings.isNullOrEmpty(targetLanguage)) {
        return false;
      }
      if (!language().equals(canonicalizeLanguage(targetLanguage))) {
        return false;
      }
      return Strings.isNullOrEmpty(targetCountry)
          || Ascii.equalsIgnoreCase(region(), targetCountry);
    }

    private static String canonicalizeLanguage(String language) {
      String lower = Ascii.toLowerCase(language);
      return LEGACY_LANGUAGE_CODES.getOrDefault(lower, lower);
    }
  }

  public ActivityManager() {
    this(new Adb());
  }

  /** Constructor for unit tests only. */
  @VisibleForTesting
  protected ActivityManager(Adb adb) {
    this.adb = Preconditions.checkNotNull(adb);
  }

  /**
   * Runs "adb shell command: adb -s serial shell am get-config" and fetches the primary active
   * locale. This complements fetching locale and language from getProperty in AndroidUtil in case
   * the original approach fails or is stale on API 24+. This is response to the bug b/79925037.
   */
  public Locale getLocale(String serial) throws MobileHarnessException, InterruptedException {
    String output;
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_GET_CONFIG, ADB_SHELL_TIMEOUT).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AM_GET_LOCALE_ERROR,
          "Fail to exec adb shell am get-config command",
          e);
    }
    return parseLocale(output);
  }

  /**
   * Parses the primary active locale from {@code am get-config} output. Supports optional {@code
   * mcc}/{@code mnc} prefixes, traditional ({@code en-rUS}, {@code ja}) and BCP-47 {@code b+}
   * ({@code b+fil+PH}, {@code b+es+419}, {@code b+zh+Hans+CN}, {@code b+fil}) qualifiers,
   * comma-separated multi-locale lists, and legacy ISO 639-1 normalization.
   */
  @VisibleForTesting
  protected static Locale parseLocale(String text) throws MobileHarnessException {
    try {
      Preconditions.checkNotNull(text);
      Matcher lineMatcher = CONFIG_LINE_PATTERN.matcher(text);
      Preconditions.checkArgument(
          lineMatcher.find(), "%s, contains no valid config information", text);
      String configLine = lineMatcher.group(1).trim();
      Preconditions.checkArgument(
          !configLine.isEmpty(), "%s, contains no valid config information", text);
      return Locale.create(extractLocaleTag(configLine));
    } catch (NullPointerException | IllegalArgumentException | IllformedLocaleException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR, "Failed to parse locale from am", e);
    }
  }

  private static String extractLocaleTag(String configLine) {
    String remainder = MCC_MNC_PREFIX_PATTERN.matcher(configLine).replaceFirst("");
    if (remainder.startsWith("b+")) {
      Matcher bcp47Matcher = BCP47_LOCALE_HEAD_PATTERN.matcher(remainder);
      Preconditions.checkArgument(
          bcp47Matcher.find(), "%s, contains no valid config information", configLine);
      return bcp47Matcher.group(1).substring(2).replace('+', '-');
    }
    Matcher traditionalMatcher = TRADITIONAL_LOCALE_HEAD_PATTERN.matcher(remainder);
    Preconditions.checkArgument(
        traditionalMatcher.find(), "%s, contains no valid config information", configLine);
    return traditionalMatcher.group(1).replaceFirst("-r", "-");
  }
}
