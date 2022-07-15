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

package com.google.devtools.common.metrics.stability.model;

/** Provider of {@link ErrorId}. */
public interface ErrorIdProvider<T extends ErrorId> {

  /**
   * Returns an ErrorId. The value is never null.
   *
   * <p>When implementing this interface, please never return null. If no concrete ErrorId provided,
   * you can return a default ErrorId with UNCLASSIFIED ErrorType. Then you should monitor the error
   * classification rate and keep specifying more concrete ErrorId for different scenarios, to
   * improve the classification rate.
   */
  T getErrorId();
}
