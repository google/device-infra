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

package com.google.wireless.qa.mobileharness.shared.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_EMAILS;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.SPLITTER;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.IntStream;

/** Common helper methods for AndroidAccountDecorator and IosGoogleAccountDecorator. */
public final class GoogleAccountDecoratorUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** AutoValue class for LST (Login Scope Token) and obfuscated Gaia ID. */
  @AutoValue
  public abstract static class LstAndObfuscatedGaiaId {
    public static LstAndObfuscatedGaiaId create(String loginScopeToken, String obfuscatedGaiaId) {
      return new AutoValue_GoogleAccountDecoratorUtil_LstAndObfuscatedGaiaId(
          loginScopeToken, obfuscatedGaiaId);
    }

    public abstract String loginScopeToken();

    public abstract String obfuscatedGaiaId();
  }

  /** Log given error message and set the given TestInfo property. */
  public static void logAndUpdateTestInfoPropertiesOnErrors(
      TestInfo testInfo, PropertyName propertyName, String propertyValue, String errorMessage) {
    testInfo.log().atInfo().alsoTo(logger).log("%s", errorMessage);
    testInfo.properties().add(propertyName, propertyValue);
  }

  /**
   * Gets the test account emails, LSTs and obfuscated Gaia IDs from Test Property.
   *
   * <p>This is only for iOS tests.
   *
   * @return A map, the key is email, the value is a pair of LST and obfuscated Gaia ID.
   */
  public static LinkedHashMap<String, LstAndObfuscatedGaiaId>
      getIosTestAccountsLstAndObfuscatedGaiaId(TestInfo testInfo) {
    // Use root test properties to handle Mobly sub-tests.
    // Get the list of test account emails, LSTs, and Obfuscated Gaia ID from Test property.
    String emailsProperty =
        getPropertyValueFromRootTest(
            testInfo,
            PropertyName.Test.IosGoogleAccountDecorator.IOS_GOOGLE_ACCOUNT_DECORATOR_EMAILS);
    String lstsFromTasProperty =
        getPropertyValueFromRootTest(
            testInfo,
            PropertyName.Test.IosGoogleAccountDecorator.IOS_GOOGLE_ACCOUNT_DECORATOR_LSTS_FROM_TAS);
    String obfuscatedGaiaIdsProperty =
        getPropertyValueFromRootTest(
            testInfo,
            PropertyName.Test.IosGoogleAccountDecorator
                .IOS_GOOGLE_ACCOUNT_DECORATOR_OBFUSCATED_GAIA_IDS);

    // Use LinkedHashMap to keep the order of emails to sign in. The map stores test accounts and
    // their corresponding LST and Obfuscated GaiaID.
    // Format: (Email : (LST, Obfuscated GaiaID)).
    LinkedHashMap<String, LstAndObfuscatedGaiaId> testAccountsLstAndObfuscatedGaiaIdMap =
        new LinkedHashMap<>();
    // If accounts to be added is given as a Test property, we need to get LSTs also as Test
    // Property. If they are specified as a job parameter, the validation and LST
    // retrieval is done by IosGoogleAccountDecoratorValidator. Accounts will be added as test
    // property instead of job property when accounts has to be shared across multiple tests and
    // cannot be used by 2 tests simultaneously. Mainly for locking the accounts for each test run.
    if (!Strings.isNullOrEmpty(emailsProperty)) {
      if (!Strings.isNullOrEmpty(lstsFromTasProperty)
          && !Strings.isNullOrEmpty(obfuscatedGaiaIdsProperty)) {
        List<String> emails = SPLITTER.splitToList(emailsProperty);
        List<String> lsts = SPLITTER.splitToList(lstsFromTasProperty);
        List<String> obfuscatedGaiaIds = SPLITTER.splitToList(obfuscatedGaiaIdsProperty);
        if (emails.size() != lsts.size() || emails.size() != obfuscatedGaiaIds.size()) {
          GoogleAccountDecoratorUtil.logAndUpdateTestInfoPropertiesOnErrors(
              testInfo,
              PropertyName.Test.AndroidAndIosAccountDecorator
                  .TEST_PROPERTY_FOR_LST_OR_OBFUSCATED_GAIA_ID_MISSING,
              "Number of emails, lsts and Obfuscated Gaia IDs are not equal.",
              String.format(
                  "The LSTs and/or Obfuscated Gaia IDs from Test Account Service are not equal to"
                      + " emails specified. #Email=%d, #LST=%d, #ObfuscatedGaiaId=%d",
                  emails.size(), lsts.size(), obfuscatedGaiaIds.size()));
        } else {
          IntStream.range(0, emails.size())
              .forEach(
                  index ->
                      testAccountsLstAndObfuscatedGaiaIdMap.put(
                          emails.get(index),
                          LstAndObfuscatedGaiaId.create(
                              lsts.get(index), obfuscatedGaiaIds.get(index))));
        }
      } else {
        GoogleAccountDecoratorUtil.logAndUpdateTestInfoPropertiesOnErrors(
            testInfo,
            PropertyName.Test.AndroidAndIosAccountDecorator
                .TEST_PROPERTY_FOR_LST_OR_OBFUSCATED_GAIA_ID_MISSING,
            "LSTs and/or Obfuscated Gaia IDs are not provided.",
            "Accounts to be added is given as a Test property, "
                + "but LSTs and/or Obfuscated Gaia IDs are not provided.");
      }
    }
    return testAccountsLstAndObfuscatedGaiaIdMap;
  }

  private static String getPropertyValueFromRootTest(TestInfo testInfo, PropertyName propertyKey) {
    return testInfo.getRootTest().properties().get(propertyKey);
  }

  /**
   * Gets the test account emails for iOS tests from the Test Property.
   *
   * <p>This is only for iOS tests.
   */
  public static ImmutableList<String> getIosEmails(TestInfo testInfo) {
    // Use root test properties to handle Mobly sub-tests.
    String emailsProperty =
        testInfo
            .getRootTest()
            .properties()
            .get(
                Ascii.toLowerCase(
                    PropertyName.Test.IosGoogleAccountDecorator.IOS_GOOGLE_ACCOUNT_DECORATOR_EMAILS
                        .name()));
    List<String> emails;
    if (!Strings.isNullOrEmpty(emailsProperty)) {
      emails = SPLITTER.splitToList(emailsProperty);
    } else {
      emails = testInfo.jobInfo().params().getList(PARAM_EMAILS, ImmutableList.of());
    }
    return emails.stream()
        .map(email -> email.contains("@") ? email : email + "@gmail.com")
        .collect(toImmutableList());
  }

  private GoogleAccountDecoratorUtil() {}
}
