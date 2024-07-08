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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitorEntryProto.MonitorEntry;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.pubsub.v1.PublisherGrpc;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/** Monitor module for Cloud PubSub. */
public final class CloudPubsubMonitorModule extends AbstractModule {
  /** Global endpoint for the Cloud Pub/Sub gRPC API. */
  private static final String CLOUD_PUBSUB_GRPC_GLOBAL_ENDPOINT = "pubsub.googleapis.com";

  /** SSL port of the Cloud Pub/Sub gRPC endpoint */
  private static final int SSL_PORT = 443;

  /** Guice binding annotation for the Cloud Pub/Sub gRPC channel. */
  @Qualifier
  @Retention(RUNTIME)
  @Target({FIELD, PARAMETER, METHOD})
  public @interface CloudPubsubChannel {}

  /** Guice binding annotation for the Cloud Pub/Sub Topic Name. */
  @Qualifier
  @Retention(RUNTIME)
  @Target({FIELD, PARAMETER, METHOD})
  public @interface CloudPubsubTopic {}

  @Override
  protected void configure() {
    bind(new TypeLiteral<DataPuller<MonitorEntry>>() {}).to(LabInfoPullerImpl.class);
    bind(DataPusher.class).to(PubsubClientImpl.class);
  }

  @Provides
  @CloudPubsubTopic
  String provideCloudPubsubTopic() {
    return String.format(
        "projects/%s/topics/%s",
        Flags.instance().cloudPubsubProjectId.get(), Flags.instance().cloudPubsubTopicId.get());
  }

  @Provides
  @Singleton
  @CloudPubsubChannel
  Channel provideCloudPubsubChannel() {
    return ManagedChannelBuilder.forAddress(CLOUD_PUBSUB_GRPC_GLOBAL_ENDPOINT, SSL_PORT).build();
  }

  @Provides
  PublisherGrpc.PublisherBlockingStub providePublisherBlockingStub(
      @CloudPubsubChannel Channel channel) throws IOException {
    GoogleCredentials credentials =
        GoogleCredentials.fromStream(
            Files.newInputStream(Path.of(Flags.instance().cloudPubsubCredFile.getNonNull())));
    return PublisherGrpc.newBlockingStub(channel)
        .withCallCredentials(MoreCallCredentials.from(credentials));
  }

  @Provides
  @Singleton
  MonitorPipelineLauncher provideMonitorPipelineLauncher(
      BatchPipelineService<MonitorEntry> batchPipelineService) {
    return new MonitorPipelineLauncher(batchPipelineService);
  }
}
