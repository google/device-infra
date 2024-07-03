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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.pubsub.v1.TopicName;
import java.util.Optional;

/** Provides all bindings for monitoring. */
public final class MonitorModule {

  private final LabInfoProvider labInfoProvider;
  private final TopicName topicName;
  private final Optional<String> credentialFile;

  public MonitorModule(LabInfoProvider labInfoProvider) {
    this.labInfoProvider = labInfoProvider;
    this.topicName =
        TopicName.of(
            Flags.instance().cloudPubsubProjectId.getNonNull(),
            Flags.instance().cloudPubsubTopicId.getNonNull());
    this.credentialFile = Optional.ofNullable(Flags.instance().cloudPubsubCredFile.get());
  }

  /** Initializes the monitoring module and start the service. */
  public void init() throws MobileHarnessException {
    BatchPipelineService<LabData> service =
        new BatchPipelineService<>(
            LabDataPuller.create(labInfoProvider),
            DataPushers.cloudPubSubPublisher(topicName, credentialFile));
    service.startAsync().awaitRunning();
  }
}
