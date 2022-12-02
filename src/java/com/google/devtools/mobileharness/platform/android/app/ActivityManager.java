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
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility methods for controling the activity manager (am) of Android devices/emulators. */
public class ActivityManager {
  /** Short timeout for quick operations. */
  @VisibleForTesting static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);

  /** Adb shell command to get config. */
  @VisibleForTesting static final String ADB_SHELL_GET_CONFIG = "am get-config";

  private final Adb adb;

  /** Structured locale information contains language and region. */
  @AutoValue
  public abstract static class Locale {

    public static Locale create(String locale) {
      List<String> langRegion = Splitter.on('-').splitToList(locale);
      String language = langRegion.get(0);
      String region = langRegion.get(1);
      return new AutoValue_ActivityManager_Locale(locale, language, region);
    }

    /** Locale in this setting is <language>-<region>, for example: en-US. */
    public abstract String locale();

    public abstract String language();

    public abstract String region();
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
   * Runs "adb shell command: adb -s serial shell am get-config" and fetches locale and language.
   * This complements the method to fetch locale and language from getProperty in AndroidUtil in
   * case the original approach fails. This is response to the bug b/79925037.
   */
  public Locale getLocale(String serial) throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_GET_CONFIG, SHORT_TIMEOUT).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AM_GET_LOCALE_ERROR,
          "Fail to exec adb shell am get-config command",
          e);
    }
    return parseLocale(output);
  }

  private static class SingletonHolder {
    /** The pattern for config information of android devices. */
    public static final Pattern CONFIG_CODE_HEAD_PATTERN =
        Pattern.compile("(?m)^config:\\s+(mcc\\d+-)?(mnc\\d+-)?([a-z]{2}-r[A-Z]{2})");
  }

  /**
   * @param text Only three cases for text: en-rUS-., mccxxx-en-rUS-., mccxxx-mncxxx-en-rUS-. This
   *     is guaranteed by the implementation
   *     //third_party/java_src/apktools/src/brut/androlib/res/data/ResConfigFlags.java
   * @return String example "en-US"
   */
  @VisibleForTesting
  protected static Locale parseLocale(String text) throws MobileHarnessException {
    try {
      Preconditions.checkNotNull(text);
      /*
       * Example: retrieve a line "config:
       * mcc321-en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround" from text and parse the
       * locale information "en-US"
       */
      Matcher match = SingletonHolder.CONFIG_CODE_HEAD_PATTERN.matcher(text);
      String loc = "";
      if (match.find()) {
        loc = match.group(3);
        loc = loc.replaceFirst("-r", "-");
      }
      Preconditions.checkArgument(!loc.isEmpty(), "%s, contains no valid config information", text);
      return Locale.create(loc);
    } catch (NullPointerException | IllegalArgumentException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR, "Failed to parse locale from am", e);
    }
  }
}
