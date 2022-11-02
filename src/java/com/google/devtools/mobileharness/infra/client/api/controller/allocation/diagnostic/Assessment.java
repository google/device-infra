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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic;

/** Assessment evaluates the ability of a particular resource to fulfill job requirements. */
public interface Assessment<T> {

  /** Adds a resource to be considered for scoring */
  Assessment<T> addResource(T resource);

  /**
   * Returns an integer reflecting how well a resource or group of resources satisfies the job
   * requirements.
   */
  int getScore();
}
