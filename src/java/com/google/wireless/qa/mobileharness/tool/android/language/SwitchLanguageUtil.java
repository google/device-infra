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
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import com.google.common.base.Strings;
import java.util.Locale;
import java.util.Optional;

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

  private Optional<UiAutomation> uiAutomation = Optional.empty();

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    this.arguments = arguments;

    // Force the instrumentation process to execute under ADB Shell's elevated security context.
    // This assumes the UID of the shell process and bypasses the certain security checks. The
    // WRITE_SETTINGS permission is appop level permission that is required for changing the system
    // locale. There's certain amount of flakiness in the time required for with appop permission
    // propagation and hence we use UI Automation to adopt the shell permission identity.
    // This is available on API 29+.
    uiAutomation = tryGetUiAutomation();
    if (uiAutomation.isPresent()
        && VERSION.SDK_INT >= VERSION_CODES.Q /* Check needed  again for linter */) {
      uiAutomation
          .get()
          .adoptShellPermissionIdentity(
              "android.permission.WRITE_SETTINGS", "android.permission.CHANGE_CONFIGURATION");
    }
    start();
  }

  private Optional<UiAutomation> tryGetUiAutomation() {
    try {
      if (VERSION.SDK_INT >= VERSION_CODES.Q) {
        return Optional.of(getUiAutomation());
      }
    } catch (RuntimeException e) {
      Log.w(LOG_TAG, "Could not get UiAutomation", e);
    }
    Log.i(LOG_TAG, "UiAutomation not available, trying without accessibility mode.");
    try {
      // FLAG_DONT_USE_ACCESSIBILITY allows you to obtain a UiAutomation instance
      // to adopt shell permissions if there is an existing UiAutomation connection to another
      // process.
      if (VERSION.SDK_INT >= VERSION_CODES.S) {
        return Optional.of(getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY));
      }
    } catch (RuntimeException e) {
      Log.w(LOG_TAG, "Could not get UiAutomation without accessibility", e);
    }
    return Optional.empty();
  }

  @Override
  public void onStart() {
    super.onStart();
    final Bundle result = new Bundle();

    // Parses parameters.
    String language = Strings.nullToEmpty(arguments.getString("language"));
    String country = Strings.nullToEmpty(arguments.getString("country"));
    Locale locale = new Locale.Builder().setLanguage(language).setRegion(country).build();

    try {
      if (isSameAsSystemLocale(locale)) {
        Log.i(LOG_TAG, "System locale same as supplied locale.");
        finishSuccessfully(result);
        return;
      }

      // Switches language and country.
      Log.i(LOG_TAG, "Change locale to [" + locale.getDisplayName() + "]");
      IActivityManager am = getActivityManager();
      Configuration config = am.getConfiguration();
      config.setLocale(locale);

      if (VERSION.SDK_INT >= VERSION_CODES.N /* API Level 24 */) {
        // For Android N device, updateConfiguration was deperacated, check bug 28448266 for detail
        // information
        am.updatePersistentConfiguration(config);
      } else {
        am.updateConfiguration(config);
      }
      finishSuccessfully(result);
    } catch (NumberFormatException | RemoteException e) {
      fail("Change locale error: " + e);
    } catch (Exception e) {
      fail(e.toString());
    } finally {
      // Clean up and drop the shell identity before exiting
      if (uiAutomation.isPresent()
          && VERSION.SDK_INT >= VERSION_CODES.Q /* Check needed  again for linter */) {
        uiAutomation.get().dropShellPermissionIdentity();
      }
    }
  }

  private void finishSuccessfully(Bundle result) {
    // This is a load bearing line.
    // Prints this log to check execution result, so do not change it.
    Log.i(LOG_TAG, "Successfully switch language");

    result.putString("result", "succeed");
    finish(Activity.RESULT_OK, result);
  }

  @SuppressWarnings("Deprecation")
  private boolean isSameAsSystemLocale(Locale suppliedLocale) {
    Locale currentSystemLocale;
    if (VERSION.SDK_INT < VERSION_CODES.N) {
      currentSystemLocale = getContext().getResources().getConfiguration().locale;
    } else {
      currentSystemLocale = getContext().getResources().getConfiguration().getLocales().get(0);
    }
    Log.i(LOG_TAG, "Current system locale is [" + currentSystemLocale.getDisplayName() + "]");
    return currentSystemLocale.getCountry().equals(suppliedLocale.getCountry())
        && currentSystemLocale.getLanguage().equals(suppliedLocale.getLanguage());
  }

  private IActivityManager getActivityManager() {
    if (VERSION.SDK_INT >= VERSION_CODES.O /* API Level 26 */) {
      ActivityManager am =
          (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
      try {
        var getService = am.getClass().getMethod("getService");
        return (IActivityManager) getService.invoke(am);
      } catch (Exception e) {
        Log.e(LOG_TAG, "Could not invoke ActivityManager.getService() method", e);
      }
    }
    return ActivityManagerNative.getDefault();
  }

  /** Fails an instrumentation request. */
  private void fail(String errorMessage) {
    Log.e(LOG_TAG, errorMessage);
    Bundle result = new Bundle();
    result.putString("error", errorMessage);
    finish(Activity.RESULT_CANCELED, result);
  }
}
