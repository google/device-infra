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

package com.google.devtools.mobileharness.shared.util.keymanager.secretmanager;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** Blocking client to use Secret Manager service. */
public interface SecretManagerClient {

  /**
   * Accesses the secret version.
   *
   * @param projectId the project ID of the secret
   * @param secretName the name of the secret
   * @param version the version of the secret
   * @return the secret value
   * @throws MobileHarnessException if the secret cannot be accessed
   */
  String accessSecretVersion(String projectId, String secretName, String version)
      throws MobileHarnessException;

  /**
   * Accesses the latest secret version.
   *
   * @param projectId the project ID of the secret
   * @param secretName the name of the secret
   * @return the latest secret value
   * @throws MobileHarnessException if the secret cannot be accessed
   */
  default String accessLatestSecretVersion(String projectId, String secretName)
      throws MobileHarnessException {
    return accessSecretVersion(projectId, secretName, "latest");
  }
}
