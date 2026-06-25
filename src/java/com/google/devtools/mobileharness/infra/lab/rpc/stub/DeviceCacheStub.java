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

package com.google.devtools.mobileharness.infra.lab.rpc.stub;

import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.LeaseDeviceCacheRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.LeaseDeviceCacheResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.ReleaseDeviceCacheRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.ReleaseDeviceCacheResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.Nullable;

/** Stub interface of {@code DeviceCacheService}-like operations. */
public interface DeviceCacheStub {

  /** Leases or extends the lease of the device cache in the lab. */
  @CanIgnoreReturnValue
  LeaseDeviceCacheResponse leaseDeviceCache(LeaseDeviceCacheRequest request)
      throws RpcExceptionWithErrorId;

  /** Leases or extends the lease of the device cache in the lab with the impersonation user. */
  @CanIgnoreReturnValue
  default LeaseDeviceCacheResponse leaseDeviceCache(
      LeaseDeviceCacheRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return leaseDeviceCache(request);
  }

  /** Releases the device cache in the lab. */
  @CanIgnoreReturnValue
  ReleaseDeviceCacheResponse releaseDeviceCache(ReleaseDeviceCacheRequest request)
      throws RpcExceptionWithErrorId;

  /** Releases the device cache in the lab with the impersonation user. */
  @CanIgnoreReturnValue
  default ReleaseDeviceCacheResponse releaseDeviceCache(
      ReleaseDeviceCacheRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return releaseDeviceCache(request);
  }
}
