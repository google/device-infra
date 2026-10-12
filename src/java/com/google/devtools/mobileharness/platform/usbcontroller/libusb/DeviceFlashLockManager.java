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

package com.google.devtools.mobileharness.platform.usbcontroller.libusb;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.flogger.FluentLogger;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Manages in-memory locks for devices being flashed and the USB detector. */
public class DeviceFlashLockManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final DeviceFlashLockManager instance = new DeviceFlashLockManager();

  // Controls host-level mutual exclusion: multiple flashes (Read) vs one detector (Write)
  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

  // Controls device-level mutual exclusion
  private final Set<String> flashingDevices = ConcurrentHashMap.newKeySet();

  private static final int LOCK_TIMEOUT_SECONDS = 30;

  private DeviceFlashLockManager() {}

  public static DeviceFlashLockManager getInstance() {
    return instance;
  }

  /** Represents an acquired lock, to be used with try-with-resources. */
  public interface LockResource extends AutoCloseable {
    @Override
    void close(); // Removes "throws Exception" from the signature
  }

  /**
   * Attempts to acquire a lock for the given device ID. Atomically fails if the LibusbDetector is
   * running, or if the specific device is already flashing.
   */
  public Optional<LockResource> tryLockDevice(String deviceId) {
    boolean readLockAcquired = false;
    try {
      try {
        readLockAcquired = rwLock.readLock().tryLock(LOCK_TIMEOUT_SECONDS, SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.atWarning().withCause(e).log(
            "Interrupted while acquiring read lock for device %s", deviceId);
        return Optional.empty();
      }

      if (!readLockAcquired) {
        logger.atInfo().log("Timeout acquiring read lock for device %s", deviceId);
        return Optional.empty();
      }

      // 2. Lock the specific device
      if (!flashingDevices.add(deviceId)) {
        logger.atInfo().log("Device %s is already flashing", deviceId);
        rwLock.readLock().unlock(); // Release the read lock if device is already flashing
        return Optional.empty();
      }

      // Return autocloseable lambda that safely releases both
      return Optional.of(
          () -> {
            flashingDevices.remove(deviceId);
            rwLock.readLock().unlock();
          });
    } finally {
      // This finally block is primarily a safeguard, the main unlock logic is in the lambda.
    }
  }

  /**
   * Attempts to acquire the global detector lock. Atomically fails if ANY device is currently
   * flashing.
   */
  public Optional<LockResource> tryLockDetector() {
    try {
      // Acquire exclusive lock (fails immediately if ANY flash readLock is held)
      if (!rwLock.writeLock().tryLock(LOCK_TIMEOUT_SECONDS, SECONDS)) {
        logger.atWarning().log(
            "Timeout acquiring write lock for detector, likely because a device is being flashed.");
        return Optional.empty();
      }

      // Return autocloseable lambda
      return Optional.of(() -> rwLock.writeLock().unlock());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.atWarning().withCause(e).log("Interrupted while acquiring write lock for detector");
      return Optional.empty();
    }
  }
}
