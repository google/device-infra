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

package com.google.devtools.mobileharness.fe.v6.service.admin;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.admin.handlers.ListWifiCredentialsHandler;
import com.google.devtools.mobileharness.fe.v6.service.admin.handlers.SetWifiCredentialsHandler;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.ListWifiCredentialsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.ListWifiCredentialsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.SetWifiCredentialsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.SetWifiCredentialsResponse;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Implementation of {@link AdminServiceLogic}. */
@Singleton
public final class AdminServiceLogicImpl implements AdminServiceLogic {

  private final SetWifiCredentialsHandler setWifiCredentialsHandler;
  private final ListWifiCredentialsHandler listWifiCredentialsHandler;

  @Inject
  AdminServiceLogicImpl(
      SetWifiCredentialsHandler setWifiCredentialsHandler,
      ListWifiCredentialsHandler listWifiCredentialsHandler) {
    this.setWifiCredentialsHandler = setWifiCredentialsHandler;
    this.listWifiCredentialsHandler = listWifiCredentialsHandler;
  }

  @Override
  public ListenableFuture<SetWifiCredentialsResponse> setWifiCredentials(
      SetWifiCredentialsRequest request) {
    return setWifiCredentialsHandler.setWifiCredentials(request);
  }

  @Override
  public ListenableFuture<ListWifiCredentialsResponse> listWifiCredentials(
      ListWifiCredentialsRequest request) {
    return listWifiCredentialsHandler.listWifiCredentials(request);
  }
}
