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

package com.google.devtools.mobileharness.fe.v6.server;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.google.devtools.mobileharness.fe.v6.server.Annotations.ServerPort;
import com.google.devtools.mobileharness.fe.v6.service.config.ConfigServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.device.DeviceServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.host.HostServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigServiceGrpc.ConfigServiceImplBase;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceServiceGrpc.DeviceServiceImplBase;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostServiceGrpc.HostServiceImplBase;
import com.google.devtools.mobileharness.fe.v6.service.shared.OssStubsModule;
import com.google.devtools.mobileharness.fe.v6.shared.util.concurrent.OssExecutorModule;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.InstantSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OssFeServerTest {

  private DeviceServiceImplBase deviceService;
  private HostServiceImplBase hostService;
  private ConfigServiceImplBase configService;
  private int port;

  @Before
  public void setUp() throws Exception {
    deviceService = mock(DeviceServiceImplBase.class);
    hostService = mock(HostServiceImplBase.class);
    configService = mock(ConfigServiceImplBase.class);
    port = PortProber.pickUnusedPort();
  }

  @Test
  public void startAndStop() throws Exception {
    OssFeServer server = new OssFeServer(deviceService, hostService, configService, port);

    server.start();
    server.stop();
  }

  @Test
  public void guiceInstantiation() throws Exception {
    Injector injector =
        Guice.createInjector(
            new OssExecutorModule(),
            new DeviceServiceModule(),
            new HostServiceModule(),
            new ConfigServiceModule(),
            new OssStubsModule(),
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class).annotatedWith(ServerPort.class).toInstance(port);
                bind(InstantSource.class).toInstance(InstantSource.system());
              }
            });
    OssFeServer server = injector.getInstance(OssFeServer.class);

    assertThat(server).isNotNull();
  }
}
