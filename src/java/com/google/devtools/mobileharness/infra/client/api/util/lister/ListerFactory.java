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

package com.google.devtools.mobileharness.infra.client.api.util.lister;

import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.lister.Lister;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;

/** Simple factory for creating {@link Lister} instances. */
public class ListerFactory {

  /**
   * Creates a new {@link Lister} instance.
   *
   * @return a new {@link Lister} instance for the given driver
   * @throws MobileHarnessException if fails to create a new {@link Lister} instance
   */
  public Lister createLister(Class<? extends Lister> listerClass) throws MobileHarnessException {
    try {
      return listerClass.newInstance();
    } catch (IllegalAccessException
        | InstantiationException
        | ExceptionInInitializerError
        | SecurityException e) {
      throw new MobileHarnessException(
          ErrorCode.TEST_LISTER_ERROR,
          "Failed to create new instant for " + listerClass.getSimpleName(),
          e);
    }
  }
}
