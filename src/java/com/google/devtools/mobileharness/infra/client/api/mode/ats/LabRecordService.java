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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.DeviceRecord;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.DeviceRecordQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.LabRecord;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.LabRecordQueryResult;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabRecordServiceGrpc;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabRecordServiceProto.GetDeviceRecordRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabRecordServiceProto.GetDeviceRecordResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabRecordServiceProto.GetLabRecordRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabRecordServiceProto.GetLabRecordResponse;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;

/** The service to export lab/device history records. */
@Singleton
class LabRecordService extends LabRecordServiceGrpc.LabRecordServiceImplBase {

  private final LabRecordManager labRecordManager;

  @Inject
  LabRecordService(LabRecordManager labRecordManager) {
    this.labRecordManager = labRecordManager;
  }

  @Override
  public void getLabRecord(
      GetLabRecordRequest request, StreamObserver<GetLabRecordResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doGetLabRecord,
        LabRecordServiceGrpc.getServiceDescriptor(),
        LabRecordServiceGrpc.getGetLabRecordMethod());
  }

  private GetLabRecordResponse doGetLabRecord(GetLabRecordRequest request) {
    ImmutableList<LabRecord> labRecords =
        labRecordManager.getLabRecords(request.getLabRecordQuery().getFilter().getHostName());
    return GetLabRecordResponse.newBuilder()
        .setLabRecordQueryResult(
            LabRecordQueryResult.newBuilder()
                .addAllLabRecord(labRecords)
                .setLabRecordTotalCount(labRecords.size()))
        .build();
  }

  @Override
  public void getDeviceRecord(
      GetDeviceRecordRequest request, StreamObserver<GetDeviceRecordResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doGetDeviceRecord,
        LabRecordServiceGrpc.getServiceDescriptor(),
        LabRecordServiceGrpc.getGetDeviceRecordMethod());
  }

  GetDeviceRecordResponse doGetDeviceRecord(GetDeviceRecordRequest request) {
    ImmutableList<DeviceRecord> deviceRecords =
        labRecordManager.getDeviceRecords(
            request.getDeviceRecordQuery().getFilter().getDeviceUuid());
    return GetDeviceRecordResponse.newBuilder()
        .setDeviceRecordQueryResult(
            DeviceRecordQueryResult.newBuilder()
                .addAllDeviceRecord(deviceRecords)
                .setDeviceRecordTotalCount(deviceRecords.size()))
        .build();
  }
}
