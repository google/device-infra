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

package com.google.devtools.mobileharness.api.testrunner.driver;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import javax.annotation.Nullable;

/**
 * Exception indicating an issue rooted in a customer-provided asset (e.g., APK, IPA, test bundle).
 *
 * <p>This exception type is used to wrap a more specific { @link MobileHarnessException}, providing
 * a clear signal in the exception chain that the error is asset-related.
 *
 * <p>Always use the static factory methods to create instances of this class.
 */
public class CustomerAssetInvalidException extends MobileHarnessException {

  /**
   * Internal constructor. Forces use of static factory methods.
   *
   * @param message The internal debug message for this wrapper.
   * @param specificCause The underlying, more specific MobileHarnessException.
   */
  private CustomerAssetInvalidException(String message, MobileHarnessException specificCause) {
    super(BasicErrorId.CUSTOMER_ASSET_INVALID_EXCEPTION_WRAPPER, message, specificCause);
  }

  /**
   * Creates a new CustomerAssetInvalidException.
   *
   * @param specificErrorId The detailed ErrorId describing the asset failure.
   * @param internalMessage The detailed internal debug message.
   * @param cause The underlying cause for the specific exception, if any.
   */
  public static CustomerAssetInvalidException create(
      ErrorId specificErrorId, String internalMessage, @Nullable Throwable cause) {
    // Create the specific exception which will become the cause of the
    // CustomerAssetInvalidException.
    MobileHarnessException specificException =
        new MobileHarnessException(specificErrorId, internalMessage, cause);

    return new CustomerAssetInvalidException(
        "Customer Asset Invalid Error: " + internalMessage, specificException);
  }

  /**
   * Creates a new CustomerAssetInvalidException without an underlying cause for the specific error.
   *
   * @param specificErrorId The detailed ErrorId describing the asset failure.
   * @param internalMessage The detailed internal debug message.
   */
  public static CustomerAssetInvalidException create(
      ErrorId specificErrorId, String internalMessage) {
    return create(specificErrorId, internalMessage, null);
  }
}
