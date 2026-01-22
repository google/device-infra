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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.common.base.Splitter;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/**
 * Spec for Google Account Decorators.
 *
 * <p>In order to make the Mobile Harness front end recognize the annotated test params, the
 * decorators must inherit from this interface. That is the reason composition over inheritance
 * principal is not applied.
 */
@SuppressWarnings("InterfaceWithOnlyStatics")
public interface GoogleAccountDecoratorSpec {

  @Deprecated
  @ParamAnnotation(
      required = false,
      help =
          "Deprecated, use emails instead. "
              + "Email address of a Google accounts to set on the device.")
  String PARAM_EMAIL = "email";

  @Deprecated
  @ParamAnnotation(
      required = false,
      help = "Deprecated, use passwords instead. " + "Password of the given Google accounts.")
  String PARAM_PASSWORD = "password";

  @ParamAnnotation(
      required = false,
      help = "Comma separated email addresses of the Google accounts to set on the device.")
  String PARAM_EMAILS = "emails";

  @ParamAnnotation(
      required = false,
      help =
          "Comma separated passwords of given accounts in the order of given accounts in emails "
              + "param. \n[NOTE: Avoid comma in password because this param is comma separated.]")
  String PARAM_PASSWORDS = "passwords";

  @Deprecated
  @ParamAnnotation(
      required = false,
      help =
          "Deprecated, use account_credential_types instead. "
              + "The type of credential. By default it is password.")
  String PARAM_ACCOUNT_CREDENTIAL_TYPE = "account_credential_type";

  @ParamAnnotation(
      required = false,
      help =
          "Comma separated credential type of given accounts in the order of given accounts in "
              + "emails param. By default it is password.")
  String PARAM_ACCOUNT_CREDENTIAL_TYPES = "account_credential_types";

  @Deprecated
  @ParamAnnotation(
      required = false,
      help =
          "Deprecated, use account_types instead. "
              + "The type of account. By default it is com.google.")
  String PARAM_ACCOUNT_TYPE = "account_type";

  @ParamAnnotation(
      required = false,
      help =
          "Comma separated type of given accounts in the order of given accounts in emails param. "
              + "By default it is com.google.")
  String PARAM_ACCOUNT_TYPES = "account_types";

  @ParamAnnotation(
      required = false,
      help =
          "Boolean value to skip the automated credential retrieval from the test account server."
              + " This can be used for avoiding the unnecessary call to the test account service"
              + " when testing with non-OTA accounts or for the local development cases when"
              + " retrieving credential is either not desired or impossible. (For instance for"
              + " the TVCs who does not have access to stubby.)")
  String PARAM_SKIP_CREDENTIAL_RETRIEVAL_FROM_TAS = "skip_credential_retrieval_from_tas";

  @ParamAnnotation(
      required = false,
      help =
          "Comma separated values of obfuscated gaia ids of the given accounts in the order of"
              + " given accounts in emails param. This will be ignored unless the"
              + " skip_credential_retrieval_from_tas is set to true.")
  String PARAM_OBFUSCATED_GAIA_IDS = "obfuscated_gaia_ids";

  @ParamAnnotation(
      required = false,
      help =
          "Boolean value to skip the default internet connectivity checks for"
              + " AndroidAccountDecorator.")
  String PARAM_HAS_CUSTOM_INTERNET_CONNECTION_IN_LAB = "has_custom_internet_connection_in_lab";

  /** Splitter for params which are defined as comma separated values. */
  Splitter SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();

  /** Job property for whether either emails or passwords are missing. */
  String JOB_PROPERTY_EMAILS_OR_PASSWORDS_MISSING = "emails_or_passwords_missing";

  /** Job property for whether the number of emails and passwords do not match. */
  String JOB_PROPERTY_EMAILS_PASSWORDS_NUMBER_MISMATCH = "emails_passwords_number_mismatch";

  /** Job property for whether the accounts error the pre-check. */
  String JOB_PROPERTY_ANDROID_ACCOUNT_PRE_CHECK_ERROR = "android_account_pre_check_error";

  /** Job property to hold test account passwords obtained from TestaccountService. */
  String JOB_PROPERTY_ANDROID_TEST_ACCOUNT_TO_PASSWORD_MAP = "android_test_account_to_password_map";

  /** Job property to hold test account validation status. */
  String JOB_PROPERTY_ANDROID_TEST_ACCOUNT_VALIDATION_STATUS =
      "android_test_account_validation_status";
}
