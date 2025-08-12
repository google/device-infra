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

package com.google.devtools.mobileharness.infra.lab.rpc.service;

import static com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools.createStandardThreadPool;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSenderFinder;
import com.google.devtools.mobileharness.infra.controller.messaging.MessagingManager;
import com.google.devtools.mobileharness.infra.controller.messaging.MessagingManagerHolder;
import com.google.devtools.mobileharness.infra.controller.test.manager.LabDirectTestRunnerUtil;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.lab.rpc.service.Annotations.TestServiceThreadPool;
import com.google.devtools.mobileharness.shared.file.resolver.AbstractFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.AtsFileServerFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.CacheFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.GcsFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.LocalFileResolver;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import java.time.InstantSource;

/** Bindings for PrepareTestService and ExecTestService. */
public class TestServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new FactoryModuleBuilder().build(ExecTestServiceImpl.ExecTestServiceImplFactory.class));

    bind(ListeningExecutorService.class)
        .annotatedWith(TestServiceThreadPool.class)
        .toInstance(createStandardThreadPool("test-service-thread-pool"));
  }

  @Provides
  @Singleton
  FileResolver provideFileResolver(
      @TestServiceThreadPool ListeningExecutorService threadPool,
      LocalFileUtil localFileUtil,
      InstantSource instantSource) {
    // LocalFileResolver.
    AbstractFileResolver localFileResolver = new LocalFileResolver(threadPool, localFileUtil);

    // CacheFileResolver.
    AbstractFileResolver cacheFileResolver =
        new CacheFileResolver(threadPool, localFileUtil, instantSource);
    localFileResolver.setSuccessor(cacheFileResolver);

    // AtsFileServerFileResolver.
    AbstractFileResolver atsFileServerFileResolver =
        new AtsFileServerFileResolver(threadPool, localFileUtil);
    cacheFileResolver.setSuccessor(atsFileServerFileResolver);

    // GcsFileResolver.
    AbstractFileResolver gcsFileResolver = new GcsFileResolver(threadPool);
    atsFileServerFileResolver.setSuccessor(gcsFileResolver);

    return localFileResolver;
  }

  @Provides
  @Singleton
  MessageSenderFinder provideMessageSenderFinder(ProxyTestManager testManager) {
    return messageSend -> LabDirectTestRunnerUtil.getMessageSender(testManager, messageSend);
  }

  @Provides
  @Singleton
  ExecTestServiceImpl provideExecTestService(
      ExecTestServiceImpl.ExecTestServiceImplFactory factory,
      ProxyTestManager testManager,
      MessagingManager messagingManager,
      @TestServiceThreadPool ListeningExecutorService mainThreadPool) {
    TestMessageManager.createInstance(
        testId -> LabDirectTestRunnerUtil.getTestMessagePoster(testManager, testId));
    MessagingManagerHolder.initialize(messagingManager);
    return factory.create(mainThreadPool);
  }
}
