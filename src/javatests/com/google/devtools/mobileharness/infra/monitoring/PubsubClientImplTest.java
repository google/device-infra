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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.monitoring.proto.MonitoredRecordProto.Attribute;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitoredRecordProto.MonitoredEntry;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitoredRecordProto.MonitoredRecord;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Timestamps;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PubsubClientImplTest {

  @Test
  public void serialize_success() {
    MonitoredRecord monitoredRecord =
        MonitoredRecord.newBuilder()
            .setTimestamp(Timestamps.fromSeconds(1234567890))
            .setHostEntry(
                MonitoredEntry.newBuilder()
                    .putIdentifier("host_key", "host_value")
                    .addAttribute(
                        Attribute.newBuilder().setName("host_name").setValue("host_value")))
            .addDeviceEntry(
                MonitoredEntry.newBuilder()
                    .putIdentifier("device_key", "device_value")
                    .addAttribute(
                        Attribute.newBuilder().setName("device_name").setValue("device_value")))
            .build();
    Optional<ByteString> serializedData = PubsubClientImpl.serialize(monitoredRecord);
    assertThat(serializedData).isPresent();
    assertThat(serializedData.get().toStringUtf8())
        .isEqualTo(
            """
            {
              "timestamp": "2009-02-13T23:31:30Z",
              "host_entry": {
                "identifier": {
                  "host_key": "host_value"
                },
                "attribute": [{
                  "name": "host_name",
                  "value": "host_value"
                }]
              },
              "device_entry": [{
                "identifier": {
                  "device_key": "device_value"
                },
                "attribute": [{
                  "name": "device_name",
                  "value": "device_value"
                }]
              }]
            }\
            """);
  }
}
