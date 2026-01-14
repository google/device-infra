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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidAccountDecorator.ANDROID_ACCOUNT_DECORATOR_AUTHCODES;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidAccountDecorator.ANDROID_ACCOUNT_DECORATOR_EMAILS;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidAccountDecorator.ANDROID_ACCOUNT_DECORATOR_LSTS_FROM_TAS;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidAccountDecorator.ANDROID_ACCOUNT_DECORATOR_PASSWORDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.accountmanager.AccountCredentialType;
import com.google.devtools.mobileharness.platform.android.accountmanager.AndroidAccountSetting;
import com.google.devtools.mobileharness.platform.android.accountmanager.AndroidGoogleAccountType;
import com.google.devtools.mobileharness.platform.android.lightning.accountmanager.AddAccountArgs;
import com.google.devtools.mobileharness.platform.android.lightning.accountmanager.AndroidAccountManager;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.WaitArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAccountDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import com.google.wireless.qa.mobileharness.shared.util.GoogleAccountDecoratorUtil;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

/** Driver decorator for setting Google accounts to the Android device. */
@DecoratorAnnotation(
    help =
        "For removing all other Google accounts, "
            + "and set the given accounts(if specified) as the Google accounts on device.")
public class AndroidAccountDecorator extends BaseDecorator
    implements GoogleAccountDecoratorSpec, SpecConfigable<AndroidAccountDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String AUTH_SUPPORT_APK_SERVICED_COMPONENT_NAME =
      "com.google.android.apps.auth.test.support/.services.AccountService";

  /** Max attempts to set account. */
  @VisibleForTesting static final int ATTEMPTS = 10;

  @VisibleForTesting static final String VALIDATOR_NAME = "AndroidAccountDecoratorValidator";

  private final Sleeper sleeper;

  private final AndroidProcessUtil androidProcessUtil;

  private final SystemStateManager systemStateManager;

  private final AndroidAccountManager androidAccountManager;

  private AndroidAccountDecoratorSpec spec;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the lab server
   * framework.
   */
  public AndroidAccountDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new SystemStateManager(),
        new AndroidProcessUtil(),
        new AndroidAccountManager(),
        Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  public AndroidAccountDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      SystemStateManager systemStateManager,
      AndroidProcessUtil androidProcessUtil,
      AndroidAccountManager androidAccountManager,
      Sleeper sleeper) {
    super(decoratedDriver, testInfo);
    this.systemStateManager = systemStateManager;
    this.androidProcessUtil = androidProcessUtil;
    this.androidAccountManager = androidAccountManager;
    this.sleeper = sleeper;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    ImmutableList<String> accountsToAdd;

    // With mobly mobile harness tests, it is possible to define multiple
    // devices(sub-devices) and use separate accounts on different devices. This is done by defining
    // different decorator parameters on different devices.
    // If any sub device scoped spec has the emails parameter, use only spec params (not test
    // properties) to get the emails to add into the device. Emails from test properties contains
    // all of the emails used on all of the devices, not only the device specific ones.
    //
    // e.g: We want to add email1 into device1 and email2 into device2. The test property
    // ANDROID_ACCOUNT_DECORATOR_EMAILS will be "email1,email2" in this case.
    boolean hasSubDeviceSpec =
        testInfo.jobInfo().subDeviceSpecs().getAllSubDevices().stream()
            .map(SubDeviceSpec::scopedSpecs)
            .map(
                scopedSpecs -> {
                  try {
                    return scopedSpecs.getSpec(AndroidAccountDecoratorSpec.class);
                  } catch (MobileHarnessException e) {
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .map(AndroidAccountDecoratorSpec::getEmails)
            .anyMatch(emails -> !isNullOrEmpty(emails));
    if (hasSubDeviceSpec) {
      accountsToAdd = getAccountsToAddBySpec();
    } else {
      accountsToAdd = getAccountsToAdd(testInfo);
    }

    ImmutableList<AccountCredentialType> credentialTypes = getAccountCredentialTypes();

    boolean removeAfterTest =
        DeviceUtil.inSharedLab()
            || !spec.hasRemoveAccountAfterTest()
            || (spec.hasRemoveAccountAfterTest() && spec.getRemoveAccountAfterTest());

    boolean forceRemove = spec.getForceRemoveAccount();
    boolean forceInstallSignedVersion = spec.getForceInstallSignedApks();
    if (forceInstallSignedVersion) {
      androidAccountManager.allowForceInstallSignedAccountHelperApks(testInfo.log());
    }

    if (!isCredentialTypesSupported(testInfo, credentialTypes)) {
      // CredentialType from user input cannot be supported by device.
      testInfo
          .resultWithCause()
          .setNonPassing(
              FAIL,
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_USE_LST_ON_RELEASE_KEYS_DEVICE_ERROR,
                  "LOGIN_SCOPED_TOKEN can not be supported by release-key device"));
      return;
    }
    removeExistingAndGetAccountsToUpdate(device, forceRemove, accountsToAdd, testInfo.log());

    try {
      tryStartAuthAccountService(deviceId, testInfo);
      loginAccounts(testInfo, accountsToAdd, credentialTypes);
      // And empty line to separate the log.
      testInfo.log().atInfo().alsoTo(logger).log("\n");
      getDecorated().run(testInfo);
    } finally {
      try {
        if (removeAfterTest) {
          if (systemStateManager.isOnline(deviceId)) {
            removeExistingAndGetAccountsToUpdate(
                device, forceRemove, /* newAccounts= */ ImmutableList.of(), testInfo.log());
          } else {
            testInfo
                .warnings()
                .addAndLog(
                    new MobileHarnessException(
                        AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_DEVICE_NOT_FOUND,
                        "Skip to remove account(s) because device "
                            + deviceId
                            + " is disconnected."),
                    logger);
          }
        }
      } catch (MobileHarnessException e) {
        testInfo.warnings().addAndLog(e, logger);
      }
    }
  }

  private void tryStartAuthAccountService(String deviceId, TestInfo testInfo)
      throws InterruptedException {
    // Proactively start the service to avoid login error. See b/466982406#comment9
    // Wait for auth support apk to be ready. removeExistingAndGetAccountsToUpdate installs the
    // auth_test_support_debug.apk. However, the service may not be ready to start immediately.
    boolean authServiceStarted =
        AndroidAdbUtil.waitForDeviceReady(
            UtilArgs.builder().setSerial(deviceId).build(),
            utilArgs -> {
              try {
                androidProcessUtil.startService(
                    utilArgs,
                    IntentArgs.builder()
                        .setComponent(AUTH_SUPPORT_APK_SERVICED_COMPONENT_NAME)
                        .build());
                return true;
              } catch (MobileHarnessException e) {
                logger.atInfo().log(
                    "Failed to start auth support service on device %s, will retry...",
                    utilArgs.serial());
                return false;
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
              }
            },
            WaitArgs.builder()
                .setSleeper(sleeper)
                .setClock(Clock.systemUTC())
                .setCheckReadyInterval(Duration.ofSeconds(5))
                .setCheckReadyTimeout(Duration.ofSeconds(30))
                .build());
    if (!authServiceStarted) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Auth support service failed to start on device %s after multiple retries.",
              deviceId);
    }
  }

  private void loginAccounts(
      TestInfo testInfo, List<String> newAccounts, List<AccountCredentialType> credentialTypes)
      throws MobileHarnessException, InterruptedException {
    if (newAccounts.isEmpty()) {
      return;
    }
    ImmutableList<AndroidGoogleAccountType> accountTypes = getAccountTypes();
    // Try to login with TaS credentials first, if fails and password/passwords are provided in the
    // parameters, fallback to login with supplied password/passwords.
    boolean succeed = false;
    MobileHarnessException oldException = null;
    try {
      succeed = loginWithTasCredentials(testInfo, newAccounts, accountTypes);
    } catch (MobileHarnessException e) {
      oldException = e;
    }
    if (succeed) {
      return;
    }

    ImmutableList<String> newPasswords = getNewPasswords();
    // fallback
    if (!newPasswords.isEmpty()) {
      if (oldException != null) {
        GoogleAccountDecoratorUtil.logAndUpdateTestInfoPropertiesOnErrors(
            testInfo,
            PropertyName.Test.AndroidAndIosAccountDecorator
                .ADD_ACCOUNT_USING_LST_FROM_TAS_ERROR_MESSAGE,
            oldException.getMessage(),
            "Adding accounts using credentials from TestaccountService Failed. This will not fail"
                + " the test as we fall back to adding  accounts using job/test param");
      }
      addAccountsFallback(
          testInfo, toListOfAccountArgs(newAccounts, newPasswords, credentialTypes, accountTypes));
    } else { // cannot fall back, throw exception
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_LOGIN_WITH_TAS_FAILURE,
          String.format(
              "Failed to login with Tas Credentials for accounts %s on device %s. \n"
                  + "originalMessage: %s",
              String.join(",", newAccounts),
              getDevice().getDeviceId(),
              (oldException == null ? "" : oldException.getMessage())),
          oldException);
    }
  }

  /**
   * Tries to login with credentials supplied by the TestaccountService (TaS) given that all of the
   * accounts have LST,password or authcode by the TaS. If the LST login type is not supported (i.e
   * release-key Android device) the provided LSTs are ignored; hence, in this case all the accounts
   * must have a password from TaS to try to login with TaS credentials.
   *
   * @return {@code true} if successfully logged in with the TaS credentials. {@code false} if not
   *     all of the accounts have TaS credential or an error occurred during the login.
   * @throws MobileHarnessException thrown in Android devices when it is impossible to determine
   *     whether the device is rooted or not. Should be propagated to the caller of the {@link #run}
   *     method for monitoring.
   * @throws InterruptedException thrown when login process or checking whether the device is rooted
   *     is interrupted.
   */
  private boolean loginWithTasCredentials(
      TestInfo testInfo, List<String> emails, List<AndroidGoogleAccountType> accountTypes)
      throws MobileHarnessException, InterruptedException {

    ImmutableMap<String, String> accountToLstFromTaS = getTestAccountsLstFromTaS(testInfo);
    ImmutableMap<String, String> accountToPasswordFromTaS =
        getTestAccountsPasswordFromTaS(testInfo);
    ImmutableMap<String, String> accountToAuthCodeFromTaS =
        getTestAccountsAuthCodeFromTaS(testInfo);

    ImmutableList.Builder<String> credentials = ImmutableList.builder();
    ImmutableList.Builder<AccountCredentialType> credentialTypes = ImmutableList.builder();
    boolean lstSupported =
        isCredentialTypesSupported(
            testInfo, ImmutableList.of(AccountCredentialType.LOGIN_SCOPED_TOKEN));
    for (String email : emails) {
      if (accountToAuthCodeFromTaS.containsKey(email)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("account %s has authcode %s", email, accountToAuthCodeFromTaS.get(email));
        credentials.add(accountToAuthCodeFromTaS.get(email));
        credentialTypes.add(AccountCredentialType.AUTHORIZATION_CODE);
      } else if (lstSupported && accountToLstFromTaS.containsKey(email)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("account %s has lst %s", email, accountToLstFromTaS.get(email));
        credentials.add(accountToLstFromTaS.get(email));
        credentialTypes.add(AccountCredentialType.LOGIN_SCOPED_TOKEN);
      } else if (accountToPasswordFromTaS.containsKey(email)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("account %s has password %s", email, accountToPasswordFromTaS.get(email));
        credentials.add(accountToPasswordFromTaS.get(email));
        credentialTypes.add(AccountCredentialType.PASSWORD);
      } else {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "TestaccountService does not have LST (Login Scoped Token), password or authcode"
                    + " for account %s",
                email);
        return false;
      }
    }

    addAccounts(
        testInfo,
        toListOfAccountArgs(emails, credentials.build(), credentialTypes.build(), accountTypes));
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Successfully used credentials from TestaccountService "
                + "to log in to the following test accounts: %s",
            emails);
    return true;
  }

  private ImmutableList<String> getAccountsToAdd(TestInfo testInfo) {
    // Use root test properties to handle mobly sub-tests
    String emailsProperty =
        testInfo.getRootTest().properties().get(ANDROID_ACCOUNT_DECORATOR_EMAILS);
    if (!isNullOrEmpty(emailsProperty)) {
      return ImmutableList.copyOf(SPLITTER.splitToList(emailsProperty));
    }

    return getAccountsToAddBySpec();
  }

  private ImmutableList<String> getAccountsToAddBySpec() {
    String emailParam = spec.getEmail();
    if (!isNullOrEmpty(emailParam)) {
      return ImmutableList.of(emailParam);
    }
    String emailsParam = spec.getEmails();
    if (!isNullOrEmpty(emailsParam)) {
      return ImmutableList.copyOf(SPLITTER.splitToList(emailsParam));
    }
    return ImmutableList.of();
  }

  private ImmutableList<String> getNewPasswords() {
    String passwordParam = spec.getPassword();
    if (!isNullOrEmpty(passwordParam)) {
      return ImmutableList.of(passwordParam);
    }
    String passwordsParam = spec.getPasswords();
    if (!isNullOrEmpty(passwordsParam)) {
      return ImmutableList.copyOf(SPLITTER.splitToList(passwordsParam));
    }
    return ImmutableList.of();
  }

  private ImmutableList<AndroidGoogleAccountType> getAccountTypes() {
    String accountTypeParam = spec.getAccountType();
    if (!isNullOrEmpty(accountTypeParam)) {
      return ImmutableList.of(AndroidGoogleAccountType.valueOf(accountTypeParam));
    }
    String accountTypesParam = spec.getAccountTypes();
    if (!isNullOrEmpty(accountTypesParam)) {
      return SPLITTER
          .splitToStream(accountTypesParam)
          .map(AndroidGoogleAccountType::valueOf)
          .collect(toImmutableList());
    }
    return ImmutableList.of(AndroidGoogleAccountType.DEFAULT);
  }

  private ImmutableList<AccountCredentialType> getAccountCredentialTypes() {
    String credentialTypeParam = spec.getAccountCredentialType();
    if (!isNullOrEmpty(credentialTypeParam)) {
      return ImmutableList.of(
          AccountCredentialType.valueOf(Ascii.toUpperCase(credentialTypeParam)));
    }
    String credentialTypesParam = spec.getAccountCredentialTypes();
    if (!isNullOrEmpty(credentialTypesParam)) {
      return SPLITTER
          .splitToStream(credentialTypesParam)
          .map(Ascii::toUpperCase)
          .map(AccountCredentialType::valueOf)
          .collect(toImmutableList());
    }
    return ImmutableList.of(AccountCredentialType.PASSWORD);
  }

  private static ImmutableMap<String, String> getTestAccountsLstFromTaS(TestInfo testInfo) {
    ImmutableList<String> lsts =
        getPropertyAsList(testInfo, ANDROID_ACCOUNT_DECORATOR_LSTS_FROM_TAS);
    if (lsts.isEmpty()) {
      return ImmutableMap.of();
    }

    ImmutableList<String> emails = getPropertyAsList(testInfo, ANDROID_ACCOUNT_DECORATOR_EMAILS);
    if (emails.size() == lsts.size()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Build map of test account to LST from emails property and LSTs from TaS property.");
      return toMap(emails, lsts);
    }

    GoogleAccountDecoratorUtil.logAndUpdateTestInfoPropertiesOnErrors(
        testInfo,
        PropertyName.Test.AndroidAndIosAccountDecorator
            .TEST_PROPERTY_FOR_LST_OR_OBFUSCATED_GAIA_ID_MISSING,
        "Number of email and lsts are not equal.",
        "Number of emails and lsts in the test properties do not match. This  will not"
            + " fail the test, as we fallback to sign-is using credentials by test params.");

    return ImmutableMap.of();
  }

  private static ImmutableMap<String, String> getTestAccountsPasswordFromTaS(TestInfo testInfo) {
    ImmutableList<String> passwords =
        getPropertyAsList(testInfo, ANDROID_ACCOUNT_DECORATOR_PASSWORDS);
    if (passwords.isEmpty()) {
      return ImmutableMap.of();
    }

    ImmutableList<String> emails = getPropertyAsList(testInfo, ANDROID_ACCOUNT_DECORATOR_EMAILS);
    if (emails.size() == passwords.size()) {
      return toMap(emails, passwords);
    }

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Number of emails and passwords in the test properties do not match. This will not"
                + " fail the test, as we fallback to sign-is using credentials by test params.");
    return ImmutableMap.of();
  }

  private static ImmutableMap<String, String> getTestAccountsAuthCodeFromTaS(TestInfo testInfo) {
    ImmutableList<String> authCodes =
        getPropertyAsList(testInfo, ANDROID_ACCOUNT_DECORATOR_AUTHCODES);
    if (authCodes.isEmpty()) {
      return ImmutableMap.of();
    }

    ImmutableList<String> emails = getPropertyAsList(testInfo, ANDROID_ACCOUNT_DECORATOR_EMAILS);
    if (emails.size() == authCodes.size()) {
      return toMap(emails, authCodes);
    }

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Number of emails and authcodes in the test properties do not match. This will not"
                + " fail the test, as we fallback to sign-is using credentials by test params.");
    return ImmutableMap.of();
  }

  private ImmutableList<AddAccountArgs> toListOfAccountArgs(
      List<String> emails,
      List<String> passwords,
      List<AccountCredentialType> credentialTypes,
      List<AndroidGoogleAccountType> accountTypes)
      throws MobileHarnessException {
    boolean autoSync = spec.getAccountSync();

    try {
      return IntStream.range(0, emails.size())
          .mapToObj(
              i -> {
                String email = emails.get(i);
                String credential = passwords.get(i);
                AccountCredentialType credentialType =
                    credentialTypes.size() == 1 ? credentialTypes.get(0) : credentialTypes.get(i);
                AndroidGoogleAccountType accountType =
                    accountTypes.size() == 1 ? accountTypes.get(0) : accountTypes.get(i);
                return AddAccountArgs.builder()
                    .setAccountSetting(
                        AndroidAccountSetting.builder()
                            .setEmail(email)
                            .setPassword(credential)
                            .setCredentialType(credentialType)
                            .setAccountType(accountType)
                            .setAutoSync(autoSync)
                            .build())
                    .setMaxAttempts(ATTEMPTS)
                    .build();
              })
          .collect(toImmutableList());
    } catch (IndexOutOfBoundsException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_INVALID_PARAMS,
          String.format(
              "Please ensure numbers of emails, passwords, credentialTypes, accountTypes match.%n#"
                  + " of emails:%d%n# of passwords:%d%n# of credentialTypes:%d%n# of"
                  + " accountTypes:%d.%nIf you are using an owned test account(OTA) please do not"
                  + " specify passwords as test params and make sure Shared Credential Access is"
                  + " enabled for your OTA. Mobile Harness retrieves OTA credentials"
                  + " automatically.",
              emails.size(), passwords.size(), credentialTypes.size(), accountTypes.size()),
          e);
    }
  }

  private void addAccountsFallback(TestInfo testInfo, List<AddAccountArgs> accountsArgs)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Fall back to add accounts using provided accounts info.");
    try {
      addAccounts(testInfo, accountsArgs);
    } catch (MobileHarnessException e) {
      // Moscar/AET services will always skip the check before they rebuild with client api.
      String androidAccountPreCheck =
          testInfo.jobInfo().properties().get(JOB_PROPERTY_ANDROID_ACCOUNT_PRE_CHECK_ERROR);
      if (isNullOrEmpty(androidAccountPreCheck)
          && !e.getErrorId()
              .equals(AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_ACCOUNT_WRONG_PASSWORD)) {
        throw e;
      }
      if (!isNullOrEmpty(androidAccountPreCheck)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Android account pre-check failed. The email(s) are not existing or "
                    + "not marked as testing account.");
      }
      // #addAccounts ignores MobileHarnessException in login if the user sets ignore_login_error.
      // If not, any caught exception will be re-thrown out and interrupt the test.
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_ACCOUNT_PRE_CHECK_ERROR, e.getMessage(), e);
    }
  }

  private void addAccounts(TestInfo testInfo, List<AddAccountArgs> accountsArgs)
      throws MobileHarnessException, InterruptedException {
    boolean ignoreLoginError = spec.getIgnoreLoginError();
    for (AddAccountArgs accountArgs : accountsArgs) {
      addAccount(testInfo, accountArgs, ignoreLoginError);
      // And empty line to separate the log.
      testInfo.log().atInfo().alsoTo(logger).log("\n");
    }
  }

  private void addAccount(TestInfo testInfo, AddAccountArgs accountArgs, boolean ignoreLoginError)
      throws InterruptedException, MobileHarnessException {
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    String email = accountArgs.accountSetting().email();
    try {
      androidAccountManager.addAccount(device, accountArgs, testInfo.log());
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Successfully added Google account %s with credential type %s",
              email, accountArgs.accountSetting().credentialType());
    } catch (MobileHarnessException e) {
      MobileHarnessException newException =
          new MobileHarnessException(
              AndroidErrorId.ANDROID_ACCOUNT_MANAGER_ACCOUNT_WRONG_PASSWORD.equals(e.getErrorId())
                  ? AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_ACCOUNT_WRONG_PASSWORD
                  : AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_ADD_ACCOUNT_ERROR,
              String.format("Failed to add Google account %s on device %s.", email, deviceId),
              e);
      if (ignoreLoginError) {
        testInfo.warnings().addAndLog(newException, logger);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("PARAM_SKIP_LOGIN_ERROR is true. Skip login error and continue the test.");
      } else {
        throw newException;
      }
    }
  }

  private boolean isCredentialTypesSupported(
      TestInfo testInfo, List<AccountCredentialType> credentialTypes)
      throws MobileHarnessException, InterruptedException {
    Device device = getDevice();

    // To save one extra adb transfer, check isRooted instead of AndroidUtil.isProductionBuild.
    if (device instanceof AndroidDevice) {
      boolean deviceIsRooted = false;
      AndroidDevice androidDevice = ((AndroidDevice) device);
      try {
        deviceIsRooted = androidDevice.isRooted();
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_CHECK_DEVICE_ROOTED_ERROR, e.getMessage());
      }

      if (!deviceIsRooted && credentialTypes.contains(AccountCredentialType.LOGIN_SCOPED_TOKEN)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("LOGIN_SCOPED_TOKEN can not be supported by release-key device");
        return false;
      }
    }

    return true;
  }

  private void removeExistingAndGetAccountsToUpdate(
      Device device, boolean forceRemove, @Nullable List<String> newAccounts, LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    try {
      androidAccountManager.removeExistingAndGetAccountsToUpdate(
          device, forceRemove, newAccounts, log);
    } catch (MobileHarnessException e) {
      String apkInstallErrorMsg =
          String.format(
              "Failed to install required apks on device %s before removing android account.",
              deviceId);
      String removeAccountErrorMsg =
          String.format("Failed to remove existing account(s) on device %s.", deviceId);
      ErrorId errId = e.getErrorId();
      if (AndroidErrorId.ANDROID_ACCOUNT_MANAGER_APK_INSTALL_INSUFFICIENT_STORAGE.equals(errId)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_APK_INSTALL_INSUFFICIENT_STORAGE,
            apkInstallErrorMsg,
            e);
      } else if (AndroidErrorId.ANDROID_ACCOUNT_MANAGER_APK_INSTALL_ERROR.equals(errId)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_APK_INSTALL_ERROR, apkInstallErrorMsg, e);
      } else if (AndroidErrorId.ANDROID_ACCOUNT_MANAGER_REMOVE_ACCOUNT_ERROR.equals(errId)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ACCOUNT_DECORATOR_REMOVE_ACCOUNT_ERROR,
            removeAccountErrorMsg,
            e);
      }
      throw e;
    }
    if (newAccounts.isEmpty()) {
      log.atInfo().alsoTo(logger).log("All Google accounts cleared");
    }
  }

  private static ImmutableList<String> getPropertyAsList(
      TestInfo testInfo, PropertyName propertyName) {
    // Use root test properties to handle mobly sub-tests
    String propertyValue = testInfo.getRootTest().properties().get(propertyName);
    if (!isNullOrEmpty(propertyValue)) {
      return ImmutableList.copyOf(SPLITTER.splitToList(propertyValue));
    }
    return ImmutableList.of();
  }

  private static ImmutableMap<String, String> toMap(
      ImmutableList<String> list1, ImmutableList<String> list2) {
    return IntStream.range(0, list1.size()).boxed().collect(toImmutableMap(list1::get, list2::get));
  }
}
