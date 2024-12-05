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

package com.google.devtools.mobileharness.platform.android.accountmanager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.email.EmailValidationUtil;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utility class to manage accounts on Android devices/emulators.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class AndroidAccountManagerUtil {

  /** Package names of the blacklisted apps that should never get installed. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * AM instrument arguments template for adding a Google account. Should fill with: email, psw,
   * type, if_sync.
   */
  private static final String ADB_SHELL_ARGS_TEMPLATE_ADD_GOOGLE_ACCOUNT =
      "-e account %s -e credentialType %d -e password %s -e accountType %s -e action add "
          + "-e sync %s -e flow %s "
          + "-r -w com.google.wireless.qa.mobileharness.tool.android.account/.AccountManagerApp";

  /** ADB shell command to run instrumentation test. Should be followed with test specific args. */
  private static final String ADB_SHELL_INSTRUMENT = "am instrument";

  /** ADB shell template for removing a Google account. Should fill the email. */
  @VisibleForTesting
  static final String ADB_SHELL_TEMPLATE_REMOVE_GOOGLE_ACCOUNT =
      "am instrument "
          + "-e account %s -e action remove -e flow %s "
          + "-r -w com.google.wireless.qa.mobileharness.tool.android.account/.AccountManagerApp";

  /** ADB shell command to get accounts in the device */
  @VisibleForTesting
  static final String ADB_SHELL_GET_ACCOUNT =
      "am instrument "
          + "-e action get "
          + "-r -w com.google.wireless.qa.mobileharness.tool.android.account/.AccountManagerApp";

  /** Output with instrumentation code -1. */
  @VisibleForTesting
  static final String OUTPUT_INSTRUMENTATION_RESULT_OK = "INSTRUMENTATION_CODE: -1";

  private static final RetryStrategy UNIFORM_DELAY_STRATEGY_WITH_RANDOMNESS =
      RetryStrategy.uniformDelay(Duration.ofMillis(3), 10).withRandomization(0.7);

  /** Android SDK ADB command line tools executor. */
  private final Adb adb;

  private final AndroidAdbUtil adbUtil;

  private final Sleeper sleeper;

  /** Creates a util for Android device operations. */
  public AndroidAccountManagerUtil() {
    this(new Adb(), new AndroidAdbUtil(), Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  AndroidAccountManagerUtil(Adb adb, AndroidAdbUtil adbUtil, Sleeper sleeper) {
    this.adb = adb;
    this.adbUtil = adbUtil;
    this.sleeper = sleeper;
  }

  /**
   * Adds the given Google account to the device with retrying. Note you must install the
   * account_manager.apk before using this method.
   *
   * <p>Please make sure {@link
   * com.google.wireless.qa.mobileharness.shared.api.AndroidApiUtil#prepareAddAndroidAccount} or
   * similar method is called before adding accounts.
   *
   * @param serial serial number of the device
   * @param accountSetting android account setting for adding account on device
   * @param attempts max retrying times for adding account
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void addGoogleAccount(String serial, AndroidAccountSetting accountSetting, int attempts)
      throws MobileHarnessException, InterruptedException {
    addGoogleAccount(serial, accountSetting, attempts, /* userId= */ null);
  }

  /**
   * Adds the given Google account to the device with retrying. Note you must install the
   * account_manager.apk before using this method.
   *
   * <p>Please make sure {@link
   * com.google.wireless.qa.mobileharness.shared.api.AndroidApiUtil#prepareAddAndroidAccount} or
   * similar method is called before adding accounts.
   *
   * @param serial serial number of the device
   * @param accountSetting android account setting for adding account on device
   * @param attempts max retrying times for adding account
   * @param userId user that the account is going to be added into. To retrieve a list of available
   *     users, please run "adb shell pm list users".
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void addGoogleAccount(
      String serial, AndroidAccountSetting accountSetting, int attempts, @Nullable Integer userId)
      throws MobileHarnessException, InterruptedException {
    String email = accountSetting.email();
    String accountUtilOutput = "";
    String instrumentCommand =
        userId == null ? ADB_SHELL_INSTRUMENT : String.format("am instrument --user %d", userId);
    // Escape special chars in psk if needed
    String password = ShellUtils.shellEscape(accountSetting.password());
    for (int i = 1; i <= attempts; i++) {
      try {
        accountUtilOutput =
            adb.runShell(
                serial,
                instrumentCommand
                    + " "
                    + String.format(
                        ADB_SHELL_ARGS_TEMPLATE_ADD_GOOGLE_ACCOUNT,
                        email,
                        accountSetting.credentialType().ordinal() + 1,
                        password,
                        accountSetting.accountType().getName(),
                        accountSetting.autoSync(),
                        "accountutil"),
                Duration.ofSeconds(20));
        if (accountUtilOutput.contains(OUTPUT_INSTRUMENTATION_RESULT_OK)) {
          return;
        }
      } catch (MobileHarnessException e) {
        accountUtilOutput = e.getMessage();
      }
      logger.atInfo().log(
          "Failed to add Google account %s by AccountUtil.%nAttempt = %d%nReturn = %s",
          email, i, accountUtilOutput);
      if (i < attempts) {
        logger.atInfo().log("Will retry adding %s by AccountUtil.", email);
      }
    }

    if (accountSetting.credentialType() != AccountCredentialType.PASSWORD) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ACCOUNT_MNGR_UTIL_ADD_ACCOUNT_BY_ACCOUNT_UTIL_ERROR,
          "Failed to add Google account "
              + email
              + " by AccountUtil.\n"
              + accountUtilOutput
              + "\nSkip AccountManager because credential type is not password.\n");
    }
    String accountManagerOutput = "";
    for (int i = 1; i <= attempts; i++) {
      try {
        accountManagerOutput =
            adb.runShell(
                serial,
                instrumentCommand
                    + " "
                    + String.format(
                        ADB_SHELL_ARGS_TEMPLATE_ADD_GOOGLE_ACCOUNT,
                        email,
                        accountSetting.credentialType().ordinal() + 1,
                        password,
                        accountSetting.accountType().getName(),
                        accountSetting.autoSync(),
                        "accountmanager"),
                Duration.ofSeconds(20));
        if (accountManagerOutput.contains(OUTPUT_INSTRUMENTATION_RESULT_OK)) {
          logger.atInfo().log(
              "Account %s added to device %s%n%s", email, serial, accountManagerOutput);
          break;
        } else {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_ACCOUNT_MNGR_UTIL_ADD_ACCOUNT_BY_ACCOUNT_MNGR_ERROR,
              accountManagerOutput);
        }
      } catch (MobileHarnessException e) {
        /**
         * According to gmscore authentication status definition in
         * /java/com/google/android/gmscore/integ/client/auth/src/com/google/android/gms/auth/\
         * firstparty/shared/Status.java BAD_AUTHENTICATION: Wrong password ( or username ) -
         * LoginActivity. User needs to enter a new password.
         */
        if (e.getMessage().contains("BAD_AUTHENTICATION")) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_ACCOUNT_MNGR_UTIL_WRONG_PASSWORD,
              "Failed to add Google account "
                  + email
                  + " because "
                  + e.getMessage()
                  + "\n"
                  + "BAD_AUTHENTICATION: please check your password/username, or try verify your"
                  + " account from website.",
              e);
        }
        if (i >= attempts) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_ACCOUNT_MNGR_UTIL_ADD_ACCOUNT_BY_ACCOUNT_MNGR_ERROR,
              "Failed to add Google account " + email + " for " + i + " times:\n" + e.getMessage(),
              e);
        } else {
          logger.atInfo().withCause(e).log(
              "Failed to add account (attempt %d of %d), retry...", i, attempts);
        }
      }
    }
  }

  /**
   * Enables the underlying Adb object to have its command output logged to the class logger.
   *
   * <p>WARNING: This will log ALL command output for Adb commands from this instance of
   * AndroidAccountManagerUtil. Take caution to make sure this won't unintentionally spam your log.
   */
  public void enableCommandOutputLogging() {
    adb.enableCommandOutputLogging();
  }

  /**
   * Extracts the Google accounts.
   *
   * @param commandOutput: the command including Account name and type
   * @return the extracted Google accounts
   */
  private static List<String> extractGoogleAccounts(String commandOutput) {
    List<String> accounts = new ArrayList<>();
    Matcher matcher =
        Pattern.compile("\\{name=([^@^\\}]+@[^,^\\}]+), type=com.google\\}(\\r\\n|\\n)")
            .matcher(commandOutput);
    while (matcher.find()) {
      String email = matcher.group(1);
      if (EmailValidationUtil.isValidShortEmail(email)) {
        accounts.add(email);
      }
    }
    return accounts;
  }

  /**
   * Gets all Google accounts. Note you must install the account_manager.apk before using this
   * method.
   *
   * <p>If there is only one available it should be the currently active one.
   *
   * <p>The output looks like:
   *
   * <pre>
   * Accounts: 4
   *   Account {name=foo@gmail.com, type=com.google}
   *   Account {name=bar@gmail.com, type=com.google}
   *   Account {name=bar@gmail.com, type=com.facebook.auth.login}
   *   Account {name=foo, type=com.skype.contacts.sync}
   * ...
   * </pre>
   *
   * @param serial serial number of the device
   * @return all the google accounts, empty if not available
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public List<String> getNoObfuscatingGoogleAccounts(String serial)
      throws MobileHarnessException, InterruptedException {
    String output = adb.runShell(serial, ADB_SHELL_GET_ACCOUNT, Duration.ofSeconds(20));
    logger.atInfo().log("Get Google Account using AccountManagerApp: %s", output);
    return extractGoogleAccounts(output);
  }

  /**
   * Gets all Google accounts by "dumpsys account".
   *
   * <p>If there is only one available it should be the currently active one.
   *
   * <p>The dumpsys output looks like:
   *
   * <p>
   *
   * <pre>
   * Accounts: 4
   *   Account {name=foo@gmail.com, type=com.google}
   *   Account {name=bar@gmail.com, type=com.google}
   *   Account {name=bar@gmail.com, type=com.facebook.auth.login}
   *   Account {name=foo, type=com.skype.contacts.sync}
   * ...
   * </pre>
   *
   * @param serial serial number of the device
   * @return all the google accounts, empty if not available
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public List<String> getGoogleAccounts(String serial)
      throws MobileHarnessException, InterruptedException {
    String output = adbUtil.dumpSys(serial, DumpSysType.ACCOUNT, "all");
    return extractGoogleAccounts(output);
  }

  /**
   * Removes the given Google account from the device. Note you must install the account_manager.apk
   * before using this method.
   *
   * @param serial serial number of the device
   * @param email email address of the Google account
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  private void removeGoogleAccountInternal(String serial, String email)
      throws MobileHarnessException, InterruptedException {
    String accountUtilOutput = "";
    try {
      accountUtilOutput =
          adb.runShell(
              serial,
              String.format(ADB_SHELL_TEMPLATE_REMOVE_GOOGLE_ACCOUNT, email, "accountutil"),
              Duration.ofSeconds(20));
      if (accountUtilOutput.contains(OUTPUT_INSTRUMENTATION_RESULT_OK)) {
        logger.atInfo().log(
            "Removed Account %s from the device %s by AccountUtil.%n%s",
            email, serial, accountUtilOutput);
        return;
      }
    } catch (MobileHarnessException e) {
      accountUtilOutput = "error message=" + e.getMessage();
    }
    logger.atInfo().log(
        "Failed to remove account %s by AccountUtil:%n%s%n", email, accountUtilOutput);

    String accountManagerOutput = "";
    Exception exception = null;
    try {
      accountManagerOutput =
          adb.runShellWithRetry(
              serial,
              String.format(ADB_SHELL_TEMPLATE_REMOVE_GOOGLE_ACCOUNT, email, "accountmanager"));
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !accountManagerOutput.contains(OUTPUT_INSTRUMENTATION_RESULT_OK)) {
      AndroidErrorId errorId = AndroidErrorId.ANDROID_ACCOUNT_MNGR_UTIL_REMOVE_ACCOUNT_ERROR;
      if (exception != null && exception.getMessage().contains("shortMsg=Process crashed")) {
        errorId = AndroidErrorId.ANDROID_ACCOUNT_MNGR_UTIL_REMOVE_ACCOUNT_BY_ACCOUNT_UTIL_ERROR;
      }
      throw new MobileHarnessException(
          errorId,
          String.format(
              "Failed to remove Google account %s:%n%s",
              email, (exception == null ? accountManagerOutput : exception.getMessage())),
          exception);
    }
    logger.atInfo().log(
        "Removed Account %s from the device %s by AccountManager.%n%s",
        email, serial, accountManagerOutput);
  }

  /**
   * Removes the given Google account from the device with retry. Note you must install the
   * account_manager.apk before using this method.
   *
   * @param serial serial number of the device
   * @param email email address of the Google account
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void removeGoogleAccount(String serial, String email)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessException exception = null;
    for (Duration delay : UNIFORM_DELAY_STRATEGY_WITH_RANDOMNESS.delays()) {
      try {
        removeGoogleAccountInternal(serial, email);
        return;
      } catch (MobileHarnessException e) {
        exception = e;
        sleeper.sleep(delay);
      }
    }
    throw exception;
  }
}
