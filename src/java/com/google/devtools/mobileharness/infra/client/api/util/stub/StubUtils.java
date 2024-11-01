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

package com.google.devtools.mobileharness.infra.client.api.util.stub;

import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.LabServerLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.shared.util.flags.Flags;

/** Utils to help create stubs. */
public final class StubUtils {

  public static String getLabServerGrpcTarget(Lab.LabLocator labLocator) {
    return getLabServerGrpcTarget(LabServerLocator.longRunningLabServer(LabLocator.of(labLocator)));
  }

  public static String getLabServerGrpcTarget(LabServerLocator labServerLocator) {
    if (Flags.instance().connectToLabServerUsingIp.getNonNull()) {
      if (Flags.instance().connectToLabServerUsingMasterDetectedIp.getNonNull()
          && labServerLocator.masterDetectedIp().isPresent()) {
        return getGrpcTargetByIp(
            labServerLocator.masterDetectedIp().get(), labServerLocator.grpcPort());
      } else {
        return getGrpcTargetByIp(labServerLocator.ip(), labServerLocator.grpcPort());
      }
    } else {
      return getGrpcTargetByHostName(labServerLocator.hostName(), labServerLocator.grpcPort());
    }
  }

  public static String getTestEngineGrpcTarget(
      LabServerLocator labServerLocator, TestEngineLocator testEngineLocator) {
    if (Flags.instance().connectToLabServerUsingIp.getNonNull()) {
      if (Flags.instance().connectToLabServerUsingMasterDetectedIp.getNonNull()
          && labServerLocator.masterDetectedIp().isPresent()) {
        return getGrpcTargetByIp(
            labServerLocator.masterDetectedIp().get(),
            testEngineLocator.getGrpcLocator().getGrpcPort());
      } else {
        return getGrpcTargetByIp(
            testEngineLocator.getGrpcLocator().getHostIp(),
            testEngineLocator.getGrpcLocator().getGrpcPort());
      }
    } else {
      return getGrpcTargetByHostName(
          testEngineLocator.getGrpcLocator().getHostName(),
          testEngineLocator.getGrpcLocator().getGrpcPort());
    }
  }

  private static String getGrpcTargetByIp(String ip, int grpcPort) {
    return String.format(
        "%s:%s",
        Flags.instance().reverseTunnelingLabServer.getNonNull() ? "localhost" : ip, grpcPort);
  }

  private static String getGrpcTargetByHostName(String hostName, int grpcPort) {
    return String.format(
        "dns:///%s:%s",
        Flags.instance().reverseTunnelingLabServer.getNonNull() ? "localhost" : hostName, grpcPort);
  }

  private StubUtils() {}
}