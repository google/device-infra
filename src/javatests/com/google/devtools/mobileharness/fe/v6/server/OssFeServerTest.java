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

import com.google.devtools.mobileharness.fe.v6.server.Annotations.ServerPort;
import com.google.devtools.mobileharness.fe.v6.service.config.ConfigServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.device.DeviceServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.host.HostServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.shared.OssStubsModule;
import com.google.devtools.mobileharness.fe.v6.shared.util.concurrent.OssExecutorModule;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.InstantSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OssFeServerTest {

  @Test
  public void startAndStop() throws Exception {
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
                bind(Integer.class).annotatedWith(ServerPort.class).toInstance(0);
                bind(InstantSource.class).toInstance(InstantSource.system());
              }
            });
    OssFeServer server = injector.getInstance(OssFeServer.class);

    server.start();
    server.stopServer();
  }
}
