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

package com.google.devtools.mobileharness.fe.v6.service.search.refresh;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.DeviceDimensionSummary;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsResponse;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.util.Modules;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetRefreshExecutors} and {@link FleetRefreshExecutor} bindings. */
@RunWith(JUnit4.class)
public final class FleetRefreshExecutorsTest {

  private ListeningExecutorService refreshExecutor;
  private ListeningScheduledExecutorService scheduledExecutor;

  @After
  public void tearDown() {
    if (refreshExecutor != null) {
      refreshExecutor.shutdownNow();
    }
    if (scheduledExecutor != null) {
      scheduledExecutor.shutdownNow();
    }
  }

  @Test
  public void create_runsTasksAndParallelStreamsInsideNamedDaemonRefreshPool() throws Exception {
    refreshExecutor = FleetRefreshExecutors.create();

    ImmutableSet<String> observedThreads =
        refreshExecutor
            .submit(
                () -> {
                  Thread caller = Thread.currentThread();
                  assertThat(caller.getUncaughtExceptionHandler())
                      .isNotInstanceOf(ThreadGroup.class);
                  return Collections.nCopies(64, "fleet-search-refresh-worker-").parallelStream()
                      .map(
                          expectedPrefix -> {
                            Thread current = Thread.currentThread();
                            assertThat(current.isDaemon()).isTrue();
                            assertThat(current.getName()).startsWith(expectedPrefix);
                            return current.getName();
                          })
                      .collect(toImmutableSet());
                })
            .get();

    assertThat(observedThreads).isNotEmpty();
    assertThat(observedThreads.size()).isAtMost(2);
    for (String threadName : observedThreads) {
      assertThat(threadName).startsWith("fleet-search-refresh-worker-");
    }
  }

  @Test
  public void fleetSearchDataModule_providesSingletonRefreshExecutorDistinctFromDefaultExecutor()
      throws Exception {
    ListeningExecutorService defaultExecutor = newDirectExecutorService();
    scheduledExecutor = ThreadPools.createStandardScheduledThreadPool("test-module-timeout", 1);
    LabInfoProvider labInfoProvider = mock(LabInfoProvider.class);
    when(labInfoProvider.getAllDeviceDimensionsAsync(any(), any()))
        .thenReturn(immediateFuture(GetAllDeviceDimensionsResponse.getDefaultInstance()));

    Injector injector =
        Guice.createInjector(
            new FleetSearchDataModule(),
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(LabInfoProvider.class).toInstance(labInfoProvider);
                bind(ConfigurationProvider.class).toInstance(mock(ConfigurationProvider.class));
                bind(ListeningExecutorService.class).toInstance(defaultExecutor);
                bind(ListeningScheduledExecutorService.class).toInstance(scheduledExecutor);
              }
            });

    Key<ListeningExecutorService> refreshKey =
        Key.get(ListeningExecutorService.class, FleetRefreshExecutor.class);
    refreshExecutor = injector.getInstance(refreshKey);
    assertThat(refreshExecutor).isSameInstanceAs(injector.getInstance(refreshKey));
    assertThat(refreshExecutor).isNotSameInstanceAs(defaultExecutor);
    String workerThreadName = refreshExecutor.submit(() -> Thread.currentThread().getName()).get();
    assertThat(workerThreadName).startsWith("fleet-search-refresh-worker-");
  }

  @Test
  public void refreshers_injectFleetRefreshExecutorInsteadOfUnqualifiedExecutor() {
    AtomicBoolean unqualifiedExecutorUsed = new AtomicBoolean(false);
    ListeningExecutorService trackingRequestExecutor =
        onExecute(newDirectExecutorService(), () -> unqualifiedExecutorUsed.set(true));
    Set<String> observedRefreshThreads = ConcurrentHashMap.newKeySet();
    refreshExecutor = FleetRefreshExecutors.create();
    ListeningExecutorService recordingRefreshExecutor =
        onExecute(
            refreshExecutor, () -> observedRefreshThreads.add(Thread.currentThread().getName()));
    scheduledExecutor = ThreadPools.createStandardScheduledThreadPool("test-refresh-timeout", 1);

    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev-0"))
                                            .setDeviceStatus(DeviceStatus.IDLE)))))
            .build();
    LabInfoProvider labInfoProvider = mock(LabInfoProvider.class);
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(
            immediateFuture(GetLabInfoResponse.newBuilder().setLabQueryResult(labResult).build()));
    when(labInfoProvider.getAllDeviceDimensionsAsync(any(), any()))
        .thenReturn(
            immediateFuture(
                GetAllDeviceDimensionsResponse.newBuilder()
                    .addDimensions(
                        DeviceDimensionSummary.newBuilder().setName("model").setDeviceCount(1))
                    .build()));
    ConfigurationProvider configurationProvider = mock(ConfigurationProvider.class);
    when(configurationProvider.getDeviceConfigs(any(), any()))
        .thenReturn(immediateFuture(ImmutableList.of()));

    Injector injector =
        Guice.createInjector(
            Modules.override(new FleetSearchDataModule())
                .with(
                    new AbstractModule() {
                      @Override
                      protected void configure() {
                        bind(LabInfoProvider.class).toInstance(labInfoProvider);
                        bind(ConfigurationProvider.class).toInstance(configurationProvider);
                        bind(ListeningExecutorService.class).toInstance(trackingRequestExecutor);
                        bind(ListeningExecutorService.class)
                            .annotatedWith(FleetRefreshExecutor.class)
                            .toInstance(recordingRefreshExecutor);
                        bind(ListeningScheduledExecutorService.class).toInstance(scheduledExecutor);
                        bind(InstantSource.class)
                            .toInstance(InstantSource.fixed(Instant.ofEpochSecond(1_700_000_000L)));
                      }
                    }));

    injector.getInstance(CoreFleetDataRefresher.class).refreshOnce();
    injector.getInstance(DimensionCatalogRefresher.class).refreshOnce();

    assertThat(injector.getInstance(FleetSnapshotStore.class).get(Fleet.FLEET_SELF).deviceCount())
        .isEqualTo(1);
    assertThat(
            injector.getInstance(DimensionCatalogStore.class).getDimensionCounts(Fleet.FLEET_SELF))
        .containsEntry("model", 1);
    assertThat(unqualifiedExecutorUsed.get()).isFalse();
    assertThat(observedRefreshThreads).isNotEmpty();
    for (String threadName : observedRefreshThreads) {
      assertThat(threadName).startsWith("fleet-search-refresh-worker-");
    }
  }

  private static ListeningExecutorService onExecute(
      ExecutorService delegate, Runnable beforeExecute) {
    return MoreExecutors.listeningDecorator(
        new ForwardingExecutorService() {
          @Override
          protected ExecutorService delegate() {
            return delegate;
          }

          @Override
          public void execute(Runnable command) {
            delegate.execute(
                () -> {
                  beforeExecute.run();
                  command.run();
                });
          }
        });
  }
}
