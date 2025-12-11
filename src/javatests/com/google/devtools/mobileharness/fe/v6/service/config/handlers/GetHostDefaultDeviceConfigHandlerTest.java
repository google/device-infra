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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigResponse;
import com.google.inject.Guice;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GetHostDefaultDeviceConfigHandlerTest {

  @Inject private GetHostDefaultDeviceConfigHandler getHostDefaultDeviceConfigHandler;

  @Before
  public void setUp() {
    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void getHostDefaultDeviceConfig_success() throws Exception {
    GetHostDefaultDeviceConfigRequest request =
        GetHostDefaultDeviceConfigRequest.getDefaultInstance();
    assertThat(getHostDefaultDeviceConfigHandler.getHostDefaultDeviceConfig(request).get())
        .isEqualTo(GetHostDefaultDeviceConfigResponse.getDefaultInstance());
  }
}
