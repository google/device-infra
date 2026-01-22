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

package com.google.wireless.qa.mobileharness.shared.api.validator.job;

import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_ACCOUNT_CREDENTIAL_TYPE;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_ACCOUNT_CREDENTIAL_TYPES;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_ACCOUNT_TYPE;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_ACCOUNT_TYPES;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_EMAIL;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_EMAILS;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_HAS_CUSTOM_INTERNET_CONNECTION_IN_LAB;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_PASSWORD;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.PARAM_PASSWORDS;
import static com.google.wireless.qa.mobileharness.shared.api.spec.GoogleAccountDecoratorSpec.SPLITTER;

import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.accountmanager.AccountCredentialType;
import com.google.devtools.mobileharness.platform.android.accountmanager.AndroidGoogleAccountType;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.List;
import java.util.Locale;

/** Job validator for the {@code AndroidAccountDecorator} driver decorator. */
public class AndroidAccountDecoratorJobValidator implements JobValidator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = Lists.newLinkedList();
    String emailParam = job.params().get(PARAM_EMAIL);
    String emailsParam = job.params().get(PARAM_EMAILS);
    String accountTypeParam = job.params().get(PARAM_ACCOUNT_TYPE);
    String accountTypesParam = job.params().get(PARAM_ACCOUNT_TYPES);
    boolean hasCustomInternetPlugin =
        job.params().isTrue(PARAM_HAS_CUSTOM_INTERNET_CONNECTION_IN_LAB);

    String internetDimension =
        job.dimensions().get(Ascii.toLowerCase(Dimension.Name.INTERNET.name()));

    if (!Strings.isNullOrEmpty(emailParam) && !Strings.isNullOrEmpty(emailsParam)) {
      errors.add("Please specify only one of email and emails, not both");
    }

    if (!Strings.isNullOrEmpty(accountTypeParam) && !Strings.isNullOrEmpty(accountTypesParam)) {
      errors.add("Please specify only one of account and accounts, not both");
    }

    List<String> newAccounts = null;
    if (!Strings.isNullOrEmpty(emailParam) || !Strings.isNullOrEmpty(emailsParam)) {
      if (!Strings.isNullOrEmpty(emailParam)) {
        newAccounts = Lists.newArrayList(emailParam);
      } else {
        newAccounts = SPLITTER.splitToList(emailsParam);
      }
    }

    List<String> accountTypeNames = null;
    if (!Strings.isNullOrEmpty(accountTypeParam)) {
      accountTypeNames = Lists.newArrayList(accountTypeParam);
    } else if (!Strings.isNullOrEmpty(accountTypesParam)) {
      accountTypeNames = SPLITTER.splitToList(accountTypesParam);
    }
    if (accountTypeNames != null) {
      for (String accountTypeName : accountTypeNames) {
        try {
          AndroidGoogleAccountType.valueOf(accountTypeName);
        } catch (IllegalArgumentException e) {
          errors.add("Unknown account type: " + accountTypeName);
        }
      }
      if (newAccounts != null
          && accountTypeNames.size() != 1
          && accountTypeNames.size() != newAccounts.size()) {
        errors.add("Please specify equal numbers of email and account types");
      }
    }

    if (newAccounts != null) {
      if (!hasCustomInternetPlugin) {
        // Step One: Checks future internet existence since add account needs internet connection.
        List<String> decoratorList = job.type().getDecoratorList();
        String setWifiDecorator = "AndroidSetWifiDecorator";
        String accountDecorator = "AndroidAccountDecorator";
        // Jobs may set Wi-Fi itself and do not require internet dimension as true.
        // Only exercise this logic if accountDecorator will be invoked from outside of
        // any decorator adapter. The non-presence of AndroidAccountDecorator in the main decorator
        // list means that we got here through one of the decorator adapters.
        if (decoratorList.contains(accountDecorator) && !hasSpecifiedDevice(job)) {
          if (!decoratorList.contains(setWifiDecorator)) {
            // Checks whether internet dimension is set as true.
            // AutoEmulator and LocalEmulator should keep adding dimension internet as true.
            if (Strings.isNullOrEmpty(internetDimension)) {
              job.dimensions()
                  .addIfAbsent(Ascii.toLowerCase(Dimension.Name.INTERNET.name()), "true");
            } else if (!Boolean.toString(true).equals(internetDimension)) {
              errors.add("Please set dimension internet as true for using AndroidAccountDecorator");
            }
          } else if (decoratorList.indexOf(setWifiDecorator)
              < decoratorList.indexOf(accountDecorator)) {
            errors.add("AndroidSetWifiDecorator must be put in front of AndroidAccountDecorator.");
          }
        }
      } else {
        logger.atInfo().log(
            "Skipping default internet connectivity checks for AndroidAccountDecorator "
                + "because 'has_custom_internet_connection_in_lab' is set to true.");
      }
      errors.addAll(validatePasswordAndCredentialTypes(job, newAccounts));
    }
    return errors;
  }

  private static ImmutableList<String> validatePasswordAndCredentialTypes(
      JobInfo job, List<String> newAccounts) {
    ImmutableList.Builder<String> errors = ImmutableList.builder();
    String passwordParam = job.params().get(PARAM_PASSWORD);
    String passwordsParam = job.params().get(PARAM_PASSWORDS);
    String credentialTypeParam = job.params().get(PARAM_ACCOUNT_CREDENTIAL_TYPE);
    String credentialTypesParam = job.params().get(PARAM_ACCOUNT_CREDENTIAL_TYPES);

    if (!Strings.isNullOrEmpty(passwordParam) && !Strings.isNullOrEmpty(passwordsParam)) {
      errors.add("Please specify only one of password and passwords, not both");
    }

    if (!Strings.isNullOrEmpty(credentialTypeParam)
        && !Strings.isNullOrEmpty(credentialTypesParam)) {
      errors.add("Please specify only one of credential and credentials, not both");
    }

    List<String> newPasswords = ImmutableList.of();
    if (!Strings.isNullOrEmpty(passwordParam)) {
      newPasswords = Lists.newArrayList(passwordParam);
    } else if (!Strings.isNullOrEmpty(passwordsParam)) {
      newPasswords = SPLITTER.splitToList(passwordsParam);
    }

    if (!newPasswords.isEmpty() && newPasswords.size() != newAccounts.size()) {
      errors.add(
          "Please specify equal numbers of email and passwords. If you are using an owned test"
              + " account(OTA) please do not specify passwords as test params and make sure Shared"
              + " Credential Access is enabled for your OTA. Mobile Harness retrieves OTA"
              + " credentials automatically.");
    }

    List<String> credentialTypeNames = null;
    if (!Strings.isNullOrEmpty(credentialTypeParam)) {
      credentialTypeNames = Lists.newArrayList(credentialTypeParam);
    } else if (!Strings.isNullOrEmpty(credentialTypesParam)) {
      credentialTypeNames = SPLITTER.splitToList(credentialTypesParam);
    }
    if (credentialTypeNames != null) {
      for (String credentialTypeName : credentialTypeNames) {
        try {
          // Checks if credential is legal
          AccountCredentialType.valueOf(credentialTypeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
          errors.add("Unknown credential type: " + credentialTypeName);
        }
      }
      if (!newAccounts.isEmpty()
          && credentialTypeNames.size() > 1
          && credentialTypeNames.size() != newAccounts.size()) {
        errors.add(
            "Please specify either a single credential type or equal numbers of email and "
                + "credential types");
      }
    }
    return errors.build();
  }

  // Returns true if the job using dimension control_id, uuid or serial to specify a device.
  private boolean hasSpecifiedDevice(JobInfo job) {
    return !Strings.isNullOrEmpty(job.dimensions().get(Dimension.Name.CONTROL_ID))
        || !Strings.isNullOrEmpty(job.dimensions().get(Dimension.Name.UUID))
        || !Strings.isNullOrEmpty(job.dimensions().get(Dimension.Name.SERIAL));
  }
}
