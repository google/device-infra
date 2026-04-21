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

package com.google.devtools.mobileharness.shared.labinfo;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceTempRequiredDimensions;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class DeviceTempRequiredDimensionManagerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final DeviceKey DEVICE_KEY = new DeviceKey("lab", "uuid");
  private static final ImmutableListMultimap<String, String> DIMENSIONS =
      ImmutableListMultimap.of("key1", "value1");

  @Bind private ListeningExecutorService threadPool;
  @Bind private ListeningScheduledExecutorService scheduledThreadPool;
  @Bind private InstantSource instantSource;

  @Mock private DeviceTempRequiredDimensionManager.Listener listener;

  @Inject private DeviceTempRequiredDimensionManager manager;

  private Instant currentInstant;

  @Before
  public void setUp() {
    threadPool = newDirectExecutorService();
    scheduledThreadPool = ThreadPools.createStandardScheduledThreadPool("testing", 1);

    currentInstant = Instant.now();
    instantSource = () -> currentInstant;

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    manager.setListener(listener);
    manager.start();
  }

  @After
  public void tearDown() {
    threadPool.shutdownNow();
    scheduledThreadPool.shutdownNow();
  }

  @Test
  public void addOrRemoveDimensions_negativeDuration_removesDimensions() {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(10));
    assertThat(manager.getDimensions(DEVICE_KEY)).isPresent();

    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(-1));

    assertThat(manager.getDimensions(DEVICE_KEY)).isEmpty();
  }

  @Test
  public void addOrRemoveDimensions_overwrite() {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(10));

    ImmutableListMultimap<String, String> newDimensions =
        ImmutableListMultimap.of("key2", "value2");
    manager.addOrRemoveDimensions(DEVICE_KEY, newDimensions, Duration.ofMinutes(20));

    Optional<DeviceTempRequiredDimensions> dimensions = manager.getDimensions(DEVICE_KEY);
    assertThat(dimensions).isPresent();
    assertThat(dimensions.get().dimensions()).isEqualTo(newDimensions);
  }

  @Test
  public void addOrRemoveDimensions_overwriteWithLongerExpiry_doesNotRemovePrematurely()
      throws Exception {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMillis(100));

    ImmutableListMultimap<String, String> newDimensions =
        ImmutableListMultimap.of("key2", "value2");
    manager.addOrRemoveDimensions(DEVICE_KEY, newDimensions, Duration.ofSeconds(10));

    Thread.sleep(300); // Wait for the first task to run (100ms)

    Optional<DeviceTempRequiredDimensions> dimensions = manager.getDimensions(DEVICE_KEY);
    assertThat(dimensions).isPresent();
    assertThat(dimensions.get().dimensions()).isEqualTo(newDimensions);
  }

  @Test
  public void addOrRemoveDimensions_overwrite_notifiesListener() {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(10));
    verify(listener).onDeviceTempRequiredDimensionChanged(DEVICE_KEY);

    ImmutableListMultimap<String, String> newDimensions =
        ImmutableListMultimap.of("key2", "value2");
    manager.addOrRemoveDimensions(DEVICE_KEY, newDimensions, Duration.ofMinutes(20));

    verify(listener, times(2)).onDeviceTempRequiredDimensionChanged(DEVICE_KEY);
  }

  @Test
  public void addOrRemoveDimensions_success() {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(10));

    Optional<DeviceTempRequiredDimensions> dimensions = manager.getDimensions(DEVICE_KEY);
    assertThat(dimensions).isPresent();
    assertThat(dimensions.get().dimensions()).isEqualTo(DIMENSIONS);

    verify(listener).onDeviceTempRequiredDimensionChanged(DEVICE_KEY);
  }

  @Test
  public void addOrRemoveDimensions_zeroDuration_notifiesListener() {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(10));
    verify(listener).onDeviceTempRequiredDimensionChanged(DEVICE_KEY);

    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ZERO);

    verify(listener, times(2)).onDeviceTempRequiredDimensionChanged(DEVICE_KEY);
  }

  @Test
  public void addOrRemoveDimensions_zeroDuration_removesDimensions() {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(10));
    assertThat(manager.getDimensions(DEVICE_KEY)).isPresent();

    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ZERO);

    assertThat(manager.getDimensions(DEVICE_KEY)).isEmpty();
  }

  @Test
  public void addOrRemoveDimensions_zeroDuration_success() {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(10));
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ZERO);

    Optional<DeviceTempRequiredDimensions> dimensions = manager.getDimensions(DEVICE_KEY);
    assertThat(dimensions).isEmpty();
  }

  @Test
  public void automaticExpiry_notifiesListener() throws Exception {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMillis(100));
    verify(listener).onDeviceTempRequiredDimensionChanged(DEVICE_KEY);

    currentInstant = currentInstant.plusMillis(200L);
    Thread.sleep(500); // Wait for background task

    verify(listener, times(2)).onDeviceTempRequiredDimensionChanged(DEVICE_KEY);
  }

  @Test
  public void getDimensions_expired_returnsEmpty() {
    manager.addOrRemoveDimensions(DEVICE_KEY, DIMENSIONS, Duration.ofMinutes(10));

    currentInstant = currentInstant.plus(Duration.ofMinutes(11));

    Optional<DeviceTempRequiredDimensions> dimensions = manager.getDimensions(DEVICE_KEY);
    assertThat(dimensions).isEmpty();
  }
}
