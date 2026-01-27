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

package com.google.devtools.mobileharness.platform.android.deviceadmin.cli;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.KeyManagementServiceSettings;
import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.integration.gcpkms.GcpKmsPublicKeySign;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;

/** Guice Module for Device Admin CLI. */
final class DeviceAdminCliModule extends AbstractModule {

  @FlagSpec(
      name = "kms_key_name",
      help =
          "Name of the Key stored in Google Cloud KMS used for signing message. The format is:"
              + " projects/*/locations/*/keyRings/*/cryptoKeys/*/cryptoKeyVersions/*")
  private static final Flag<String> kmsKeyName = Flag.value("");

  @FlagSpec(
      name = "credentials_path",
      help = "Path to the credential file used to access Google Cloud KMS")
  private static final Flag<String> credPath = Flag.value("");

  @Provides
  @Singleton
  private PublicKeySign providePublicKeySign()
      throws FileNotFoundException, IOException, GeneralSecurityException {
    if (kmsKeyName.get().isEmpty()) {
      throw new IllegalArgumentException("--kms_key_name must be specified");
    }
    if (credPath.get().isEmpty()) {
      throw new IllegalArgumentException("--credentials_path must be specified");
    }

    if (!new File(credPath.get()).exists()) {
      throw new IllegalArgumentException(
          String.format("Credentials file %s does not exist", credPath.get()));
    }

    CredentialsProvider credentialsProvider =
        FixedCredentialsProvider.create(
            ServiceAccountCredentials.fromStream(new FileInputStream(credPath.get())));

    KeyManagementServiceSettings settings =
        KeyManagementServiceSettings.newBuilder()
            .setCredentialsProvider(credentialsProvider)
            .build();

    PublicKeySign kmsSigner =
        GcpKmsPublicKeySign.builder()
            .setKeyManagementServiceClient(KeyManagementServiceClient.create(settings))
            .setKeyName(kmsKeyName.get())
            .build();

    return kmsSigner;
  }
}
