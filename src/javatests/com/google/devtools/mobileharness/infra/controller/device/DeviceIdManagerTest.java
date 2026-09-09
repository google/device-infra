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

package com.google.devtools.mobileharness.infra.controller.device;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceIdManagerTest {

  private DeviceIdManager manager;

  @Before
  public void setUp() {
    manager = DeviceIdManager.getInstance();
    manager.clearAll();
  }

  @Test
  public void add_mapsIdsCorrectly() {
    DeviceId deviceId = DeviceId.of("control1", "uuid1");
    DeviceIdManagerShim.add(manager, deviceId);

    assertThat(manager.containsControlId("control1")).isTrue();
    assertThat(manager.containsUuid("uuid1")).isTrue();
    assertThat(manager.getDeviceIdFromControlId("control1").get()).isEqualTo(deviceId);
    assertThat(manager.getDeviceIdFromUuid("uuid1").get()).isEqualTo(deviceId);
  }

  @Test
  public void remove_matchingUuid_purgesMappings() {
    DeviceId deviceId = DeviceId.of("control1", "uuid1");
    DeviceIdManagerShim.add(manager, deviceId);

    // Call eviction with matching uuid
    DeviceIdManagerShim.remove(manager, "control1", "uuid1");

    assertThat(manager.containsControlId("control1")).isFalse();
    assertThat(manager.containsUuid("uuid1")).isFalse();
  }

  @Test
  public void remove_mismatchedUuid_retainsMappings() {
    DeviceId deviceIdOld = DeviceId.of("control1", "uuid1");
    DeviceIdManagerShim.add(manager, deviceIdOld);

    // Simulate a reboot cycle rapidly registering a new runner: control1 -> uuid2
    DeviceId deviceIdNew = DeviceId.of("control1", "uuid2");
    DeviceIdManagerShim.add(manager, deviceIdNew);

    // Now, the obsolete runner's asynchronous teardown runs and calls remove using the old uuid
    DeviceIdManagerShim.remove(manager, "control1", "uuid1");

    // The mappings must NOT be evicted! They belong to uuid2 now!
    assertThat(manager.containsControlId("control1")).isTrue();
    assertThat(manager.containsUuid("uuid2")).isTrue();
    assertThat(manager.getDeviceIdFromControlId("control1").get()).isEqualTo(deviceIdNew);

    // Old uuid map entry should be unconditionally removed
    assertThat(manager.containsUuid("uuid1")).isFalse();
  }

  @Test
  public void remove_concurrentAddAndRemove_retainsNewMapping() throws Exception {
    final DeviceId deviceIdOld = DeviceId.of("control-concurrent", "uuid1");
    final DeviceId deviceIdNew = DeviceId.of("control-concurrent", "uuid2");

    // Pre-seed old mapping
    DeviceIdManagerShim.add(manager, deviceIdOld);

    final java.util.concurrent.CountDownLatch readLatch =
        new java.util.concurrent.CountDownLatch(1);
    final java.util.concurrent.CountDownLatch writeLatch =
        new java.util.concurrent.CountDownLatch(1);

    // Reflectively spy the ConcurrentHashMap instance
    java.util.concurrent.ConcurrentHashMap<String, DeviceId> spyMap =
        new java.util.concurrent.ConcurrentHashMap<String, DeviceId>() {
          @Override
          public DeviceId get(Object key) {
            DeviceId val = super.get(key);
            if ("control-concurrent".equals(key) && val != null && "uuid1".equals(val.uuid())) {
              readLatch.countDown();
              try {
                writeLatch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            return val;
          }
        };

    // Populate all existing mappings inside the spy map first to preserve manager state
    java.lang.reflect.Field field = DeviceIdManager.class.getDeclaredField("controlIdToDeviceId");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, DeviceId> originalMap =
        (java.util.Map<String, DeviceId>) field.get(manager);
    spyMap.putAll(originalMap);
    field.set(manager, spyMap);

    try {
      Thread threadAdd =
          new Thread(
              () -> {
                try {
                  readLatch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                  DeviceIdManagerShim.add(manager, deviceIdNew);
                  writeLatch.countDown();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });

      Thread threadRemove =
          new Thread(
              () -> {
                DeviceIdManagerShim.remove(manager, "control-concurrent", "uuid1");
              });

      threadAdd.start();
      threadRemove.start();

      threadAdd.join();
      threadRemove.join();

      // Verify mapping is not corrupted (must remain mapped to uuid2)
      assertThat(manager.containsControlId("control-concurrent")).isTrue();
      assertThat(manager.getDeviceIdFromControlId("control-concurrent").get())
          .isEqualTo(deviceIdNew);
    } finally {
      // Restore original map to prevent test suite contamination
      field.set(manager, originalMap);
    }
  }
}
