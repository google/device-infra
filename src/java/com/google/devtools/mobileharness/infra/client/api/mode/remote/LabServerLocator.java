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

package com.google.devtools.mobileharness.infra.client.api.mode.remote;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import java.util.Optional;

/**
 * Locator where MH provides lab-side test runner controlling related services, e.g.,
 * PrepareTestService and FileTransferService.
 */
@AutoValue
public abstract class LabServerLocator {

  public static LabServerLocator longRunningLabServer(LabLocator labLocator) {
    return new AutoValue_LabServerLocator(labLocator.hostName(), labLocator);
  }

  @SuppressWarnings("unused")
  public static LabServerLocator perTestLabServer(LabLocator labLocator, TestLocator testLocator) {
    return new AutoValue_LabServerLocator(
        getPerTestLabServerCloudRpcShardName(labLocator.hostName(), testLocator.id()), labLocator);
  }

  public abstract String cloudRpcShardName();

  abstract LabLocator labLocator();

  @Memoized
  @Override
  public String toString() {
    return String.format("lab_server_locator[%s]", labLocator().toFullString());
  }

  /**
   * Note that {@link #perTestLabServer} may not provide correct services based on IP and ports, and
   * these methods is just for interface compatibility.
   */
  public String ip() {
    return labLocator().ip();
  }

  public Optional<String> masterDetectedIp() {
    return labLocator().masterDetectedIp();
  }

  public String hostName() {
    return labLocator().hostName();
  }

  /**
   * Note that {@link #perTestLabServer} may not provide correct services based on IP and ports, and
   * these methods is just for interface compatibility.
   */
  public int rpcPort() {
    return labLocator().ports().getNonNull(PortType.LAB_SERVER_RPC);
  }

  /**
   * Note that {@link #perTestLabServer} may not provide correct services based on IP and ports, and
   * these methods is just for interface compatibility.
   */
  public int socketPort() {
    return labLocator().ports().getNonNull(PortType.LAB_SERVER_SOCKET);
  }

  public int grpcPort() {
    return labLocator().ports().getNonNull(PortType.LAB_SERVER_GRPC);
  }

  public Lab.LabLocator labLocatorProto() {
    return labLocator().toProto();
  }

  private static String getPerTestLabServerCloudRpcShardName(String labHostName, String testId) {
    return String.format("%s_%s", labHostName, testId);
  }
}
