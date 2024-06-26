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

package com.google.devtools.mobileharness.infra.monitoring;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.pubsub.v1.TopicName;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

/** Factory methods for {@link DataPusher}. */
public final class DataPushers {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting
  static class PublisherSupplier implements Supplier<Publisher> {
    private final TopicName topicName;
    private final CredentialsProvider credentialsProvider;
    private final Optional<FixedTransportChannelProvider> channelProvider;

    PublisherSupplier(
        TopicName topicName,
        CredentialsProvider credentialsProvider,
        Optional<FixedTransportChannelProvider> channelProvider) {
      this.topicName = topicName;
      this.credentialsProvider = credentialsProvider;
      this.channelProvider = channelProvider;
    }

    @Override
    public @Nullable Publisher get() {
      logger.atInfo().log(
          "Create publisher for topic %s, with credentials %s", topicName, credentialsProvider);
      Publisher.Builder builder =
          Publisher.newBuilder(topicName).setCredentialsProvider(credentialsProvider);
      channelProvider.ifPresent(builder::setChannelProvider);

      try {
        return builder.build();
      } catch (IOException e) {
        return null;
      }
    }
  }

  private static final ConcurrentMap<TopicName, CloudPubSubPublisher> cloudPubSubPublishers =
      new ConcurrentHashMap<>();

  public static CloudPubSubPublisher cloudPubSubPublisher(
      String projectId, String topicId, Optional<String> credentialFile)
      throws MobileHarnessException {
    return cloudPubSubPublisher(TopicName.of(projectId, topicId), credentialFile);
  }

  public static CloudPubSubPublisher cloudPubSubPublisher(
      TopicName topicName, Optional<String> credentialFile) throws MobileHarnessException {
    cloudPubSubPublishers.putIfAbsent(
        topicName, createCloudPubSubPublisher(topicName, credentialFile));
    return cloudPubSubPublishers.get(topicName);
  }

  private static CloudPubSubPublisher createCloudPubSubPublisher(
      TopicName topicName, Optional<String> credentialFile) throws MobileHarnessException {
    try {
      return new CloudPubSubPublisher(
          Suppliers.memoize(
              new PublisherSupplier(
                  topicName, provideCredentials(credentialFile), provideTransportChannel())));
    } catch (MobileHarnessException e) {
      if (e.getErrorId() == InfraErrorId.FAIL_TO_GET_SERVICE_ACCOUNT_CREDENTIAL_FROM_FILE) {
        throw new MobileHarnessException(
            InfraErrorId.CLOUD_PUB_SUB_PUBLISHER_GET_CREDENTIAL_ERROR,
            "Error for cloud pubsub credential",
            e);
      }
      throw e;
    }
  }

  private static CredentialsProvider provideCredentials(Optional<String> credentialFilePath)
      throws MobileHarnessException {
    if (credentialFilePath.isEmpty()) {
      // On Borg, we use the LOAS authentication for the Borg role. No credential needed.
      return FixedCredentialsProvider.create(null);
    }

    try (FileInputStream input = new FileInputStream(credentialFilePath.get())) {
      return FixedCredentialsProvider.create(
          ServiceAccountCredentials.fromStream(input)
              .createScoped(TopicAdminSettings.getDefaultServiceScopes()));
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.FAIL_TO_GET_SERVICE_ACCOUNT_CREDENTIAL_FROM_FILE,
          String.format("Failed to get local credential file %s", credentialFilePath),
          e);
    }
  }

  private DataPushers() {}
}
