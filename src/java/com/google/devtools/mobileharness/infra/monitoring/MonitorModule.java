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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitorEntryProto.MonitorEntry;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.pubsub.v1.TopicName;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;
import javax.inject.Qualifier;

/** Provides all bindings for monitoring. */
public final class MonitorModule extends AbstractModule {

  private final TopicName topicName;
  private final Optional<String> credentialFile;

  @Qualifier
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  @interface LabMonitoringService {}

  @Qualifier
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  @interface LabDataPusher {}

  public MonitorModule() {
    this.topicName =
        TopicName.of(
            Flags.instance().cloudPubsubProjectId.getNonNull(),
            Flags.instance().cloudPubsubTopicId.getNonNull());
    this.credentialFile = Optional.ofNullable(Flags.instance().cloudPubsubCredFile.getNonNull());
  }

  @Provides
  @LabMonitoringService
  BatchPipelineService<MonitorEntry> providesLabDataService(
      LabDataPuller puller, @LabDataPusher CloudPubSubPublisher pusher) {
    return new BatchPipelineService<>(puller, pusher);
  }

  @Provides
  LabDataPuller providesLabDataPuller(LabInfoProvider labInfoProvider) {
    return LabDataPuller.create(labInfoProvider);
  }

  @Provides
  @LabDataPusher
  CloudPubSubPublisher providesLabDataPusher() throws MobileHarnessException {
    return DataPushers.cloudPubSubPublisher(topicName, credentialFile);
  }
}
