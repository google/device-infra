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

package com.google.wireless.qa.mobileharness.tool.android.language;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.Instrumentation;
import android.app.backup.BackupManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import com.google.common.base.Strings;
import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Switch language main activity for switching language and country of the device.
 *
 * <p>Usage: Usage: -e language LANGUAGE [-e country COUNTRY]
 *
 * <ul>
 *   <li>LANGUAGE: An ISO 639 alpha-2 or alpha-3 language code, or a language subtag up to 8
 *       characters in length. See the {@link Locale} class description about valid language values.
 *   <li>COUNTRY: An ISO 3166 alpha-2 country code or a UN M.49 numeric-3 area code. See the {@link
 *       Locale} class description about valid country values.
 * </ul>
 *
 * @see Locale
 */
public class SwitchLanguageUtil extends Instrumentation {

  private static final String LOG_TAG = "SwitchLanguage";

  protected Bundle arguments;

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    this.arguments = arguments;
    start();
  }

  @Override
  public void onStart() {
    super.onStart();
    final Bundle result = new Bundle();

    // Parses parameters.
    int sdkVersion = 17;
    String sdkVersionStr = Strings.nullToEmpty(arguments.getString("sdk_version"));
    String language = Strings.nullToEmpty(arguments.getString("language"));
    String country = Strings.nullToEmpty(arguments.getString("country"));
    Locale locale = new Locale.Builder().setLanguage(language).setRegion(country).build();

    // Switches language and country.
    Log.i(LOG_TAG, "Change locale to [" + locale.getDisplayName() + "]");
    IActivityManager am = ActivityManagerNative.getDefault();
    try {
      if (!Strings.isNullOrEmpty(sdkVersionStr)) {
        sdkVersion = Integer.parseInt(sdkVersionStr);
      }

      Configuration config = am.getConfiguration();

      if (sdkVersion < 17) {
        config.locale = locale;
        try {
          Field userUpdateField = config.getClass().getDeclaredField("userSetLocale");
          userUpdateField.setAccessible(true);
          userUpdateField.setBoolean(config, true);
        } catch (IllegalAccessException | NoSuchFieldException e) {
          fail("Failed to set userSetLocale field: " + e);
        }
      } else {
        config.setLocale(locale);
      }

      if (sdkVersion >= 24) {
        // For Android N device, updateConfiguration was deperacated, check bug 28448266 for detail
        // information
        am.updatePersistentConfiguration(config);
      } else {
        am.updateConfiguration(config);
      }

      Log.i(LOG_TAG, "Trigger setting change notify");
      BackupManager.dataChanged("com.android.providers.settings");

      // Prints this log to check execution result, so do not change it.
      Log.i(LOG_TAG, "Successfully switch language");

      result.putString("result", "succeed");
      finish(Activity.RESULT_OK, result);
    } catch (NumberFormatException | RemoteException e) {
      fail("Change locale error: " + e);
    } catch (Exception e) {
      fail(e.toString());
    }
  }

  /** Fails an instrumentation request. */
  private void fail(String errorMessage) {
    Log.e(LOG_TAG, errorMessage);
    Bundle result = new Bundle();
    result.putString("error", errorMessage);
    finish(Activity.RESULT_CANCELED, result);
  }
}
