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

package com.google.wireless.qa.mobileharness.shared.android;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.AdbCommandRejectedException;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.AndroidDebugBridge;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.AndroidDebugBridge.IClientChangeListener;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.Client;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.ClientData;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.ClientData.HeapInfo;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.IDevice;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.RawImage;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.TimeoutException;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.internal.ClientImpl;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * A wrapper around Android's DDM library. This is used to do direct communication with a process
 * for commands which can't be done over ADB.
 */
public class DdmUtil implements IClientChangeListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The maximum number of times to try ADB (in case there are failures). */
  private static final int ADB_MAX_ATTEMPTS = 30;

  /** The amount of time to wait before each ADB retry. */
  private static final Duration ADB_RETRY_INTERVAL = Duration.ofMillis(333);

  /** The lock to protect checkDeviceAvailability */
  private static final Object DEVICE_INIT_LOCK = new Object();

  /** The device serial id */
  private final String serialId;

  private final Sleeper sleeper;

  /** The device that we're connected to. */
  private IDevice device;

  /** The Client List that we cached for MH usage, {ApplicationName, {@link Client}} mapping */
  private final ConcurrentHashMap<String, Client> allClients = new ConcurrentHashMap<>();

  /** A mapping of package name to the heap infos and times collected in the latest snapshot. */
  private final ConcurrentHashMap<String, HeapCollectionInfo> packageNameToHeapCollectionInfo =
      new ConcurrentHashMap<>();

  /**
   * The public constructor.
   *
   * @param serialId the device id to connect to
   */
  public DdmUtil(String serialId) {
    this(serialId, null, Sleeper.defaultSleeper());
  }

  /**
   * Constructor used for tests.
   *
   * @param serialId the device id to connect to
   * @param device the device object to use, can be null
   * @param sleeper the sleeper to use, must not be null
   */
  @VisibleForTesting
  DdmUtil(String serialId, @Nullable IDevice device, Sleeper sleeper) {
    this.serialId = serialId;
    this.device = device;
    this.sleeper = sleeper;

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                logger.atInfo().log("Device %s: Turning off the ADB bridge.", serialId);
                AndroidDebugBridge.disconnectBridge();
              }
            });
  }

  /**
   * Gets the most recent {@code Instant} which a collection callback received.
   *
   * @param applicationName the name for the process to fetch the time of
   * @return the Instant we got heap information, or null if we never did
   */
  @Nullable
  public Instant getLastCollectionTimestamp(String applicationName) {
    HeapCollectionInfo info = packageNameToHeapCollectionInfo.get(applicationName);
    return (info == null) ? null : info.collectionTime();
  }

  /**
   * Gets a single HeapInfo object representing the sum of all heaps for the given process.
   *
   * @param applicationName the name of the process to fetch the heaps for
   * @return the combined {@code HeapInfo}, or null if there is no heap information
   */
  @Nullable
  public HeapInfo getCombinedHeapInfo(String applicationName) {
    HeapCollectionInfo info = packageNameToHeapCollectionInfo.get(applicationName);
    return (info == null) ? null : info.getCombinedHeapInfo();
  }

  /**
   * Turns on collecting heap information for the given process.
   *
   * @param applicationName the name of the process to collect heap information for
   * @return {@code true} if enabled, {@code false} if the process was not found running on the
   *     device
   */
  @CanIgnoreReturnValue
  public boolean enableHeapCollection(String applicationName)
      throws MobileHarnessException, InterruptedException {
    AndroidDebugBridge.addClientChangeListener(this);

    Optional<Client> client = retrieveClient(applicationName);
    if (client.isEmpty()) {
      logger.atInfo().log(
          "Device %s: Process %s not found, nothing to enable for heap collection.",
          serialId, applicationName);
      return false;
    }

    if (!(client.get() instanceof ClientImpl)) {
      logger.atWarning().log(
          "Device %s: Client is not an instance of ClientImpl. Can't enable heap collection.",
          serialId);
      return false;
    }

    ClientImpl impl = (ClientImpl) client.get();
    if (!impl.isHeapUpdateEnabled()) {
      impl.setHeapUpdateEnabled(true);
      logger.atInfo().log(
          "Device %s: Process %s found, heap collection enabled.", serialId, applicationName);
    }
    return true;
  }

  /**
   * A function to trigger a garbage collection in the process given an application name.
   *
   * @param applicationName the application name for the process in which we will trigger a GC
   * @return true if a GC was issued, false otherwise
   * @throws MobileHarnessException if a device or emulator cannot be reached
   * @throws InterruptedException if our thread was interrupted while polling
   */
  @CanIgnoreReturnValue
  public boolean issueGarbageCollection(String applicationName)
      throws MobileHarnessException, InterruptedException {
    Optional<Client> client = retrieveClient(applicationName);
    if (client.isEmpty()) {
      logger.atInfo().log(
          "Device %s: Process %s not found, Skip the GC call.", serialId, applicationName);
      return false;
    }

    client.get().executeGarbageCollector();
    logger.atInfo().log("Device %s: Process %s found, Call GC on it.", serialId, applicationName);
    return true;
  }

  /**
   * Triggers a KILL command being sent to a process given its application name.
   *
   * @param applicationName the application name for the process in which we will trigger a KILL
   * @throws MobileHarnessException if a device or emulator cannot be reached
   * @throws InterruptedException if our thread was interrupted while polling
   */
  public void kill(String applicationName) throws MobileHarnessException, InterruptedException {
    Optional<Client> client = retrieveClient(applicationName);
    if (client.isEmpty()) {
      logger.atInfo().log(
          "Device %s: process %s not found. there's nothing to kill.", serialId, applicationName);
      return;
    }

    // Remove the entry from cache before kill it.
    allClients.remove(applicationName);
    client.get().kill();
    logger.atInfo().log("Device %s: Kill command sent to process %s", serialId, applicationName);
  }

  /**
   * Copied from java/com/android/ddmlib/AndroidDebugBridge.java to help understand the parameters.
   *
   * <p>Sent when an existing client information changed.
   *
   * <p>This is sent from a non UI thread.
   *
   * @param client the updated client.
   * @param changeMask the bit mask describing the changed properties. It can contain any of the
   *     following values: {@link Client#CHANGE_INFO}, {@link Client#CHANGE_DEBUGGER_STATUS}, {@link
   *     Client#CHANGE_THREAD_MODE}, {@link Client#CHANGE_THREAD_DATA}, {@link
   *     Client#CHANGE_HEAP_MODE}, {@link Client#CHANGE_HEAP_DATA}, {@link
   *     Client#CHANGE_NATIVE_HEAP_DATA}
   */
  @Override
  public void clientChanged(Client client, int changeMask) {
    // Ignore clients that we haven't cached which means no one expect its heap data.
    if (!allClients.containsValue(client)) {
      return;
    }

    // Only care about heap data change for now.
    if ((changeMask & Client.CHANGE_HEAP_DATA) == 0) {
      return;
    }

    // Get the client data and fetch the heap information from it.
    ClientData data = client.getClientData();
    String name = data.getClientDescription();
    if (name != null) {
      List<HeapInfo> heaps = new ArrayList<>();

      // Per documentation, we need to synchronize on the data object to iterate over it.
      synchronized (data) {
        Iterator<Integer> heapIds = data.getVmHeapIds();
        while (heapIds.hasNext()) {
          Integer heapId = heapIds.next();
          heaps.add(data.getVmHeapInfo(heapId));
        }
      }

      // Store away the list of heap infos and record the time.
      packageNameToHeapCollectionInfo.put(name, HeapCollectionInfo.create(Instant.now(), heaps));
    }
  }

  /* Check if device can be connected thru ddm ADB */
  @CanIgnoreReturnValue
  public boolean checkDeviceAvailability(boolean needClient)
      throws MobileHarnessException, InterruptedException {
    synchronized (DEVICE_INIT_LOCK) {
      if (device == null) {
        AndroidDebugBridge.initIfNeeded(needClient);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        device = retrieveDevice(bridge, serialId);
      }
    }

    return (device != null);
  }

  /**
   * Capture one screenshot from device through the programmatic ADB bridge.
   *
   * @param landscape whether or not to rotate image
   * @return BufferedImage image for screenshot
   * @throws MobileHarnessException if can not capture screenshot from a device or emulator
   */
  public BufferedImage getScreenshot(boolean landscape) throws MobileHarnessException {
    RawImage rawImage = null;
    BufferedImage image = null;

    if (device == null) {
      logger.atInfo().log("Device %s hasn't been initialized.", serialId);
      throw new MobileHarnessException(
          AndroidErrorId.DDM_UTIL_DEVICE_NOT_READY, "Not initialized device");
    }

    try {
      rawImage = device.getScreenshot();
    } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DDM_UTIL_TAKE_SCREENSHOT_ERROR, "Failed to capture screen", e);
    }

    // Convert RawImage to BufferedImage for general usage
    if (rawImage != null) {
      int width2 = landscape ? rawImage.height : rawImage.width;
      int height2 = landscape ? rawImage.width : rawImage.height;
      image = new BufferedImage(width2, height2, BufferedImage.TYPE_INT_RGB);

      int index = 0;
      int indexInc = rawImage.bpp >> 3;
      for (int y = 0; y < rawImage.height; y++) {
        for (int x = 0; x < rawImage.width; x++, index += indexInc) {
          int value = rawImage.getARGB(index);
          if (landscape) {
            image.setRGB(y, rawImage.width - x - 1, value);
          } else {
            image.setRGB(x, y, value);
          }
        }
      }
    }

    return image;
  }

  /**
   * Retrieves device from the list available through the programmatic ADB bridge.
   *
   * @param bridge the {@code AndroidDebugBridge} to use
   * @param serialId the device serial id
   * @return IDevice from {@code AndroidDebugBridge}
   * @throws MobileHarnessException if a device or emulator cannot be reached
   * @throws InterruptedException if our thread was interrupted while polling
   */
  @VisibleForTesting
  IDevice retrieveDevice(AndroidDebugBridge bridge, String serialId)
      throws MobileHarnessException, InterruptedException {
    // First, make sure there are devices listed.
    for (int retry = 0; !hasInitialDeviceList(bridge) && retry < ADB_MAX_ATTEMPTS; retry++) {
      sleeper.sleep(ADB_RETRY_INTERVAL);
    }

    // Get the list of devices.
    IDevice[] devices = getDevices(bridge);
    if (devices.length == 0) {
      throw new MobileHarnessException(
          AndroidErrorId.DDM_UTIL_DEVICE_NOT_FOUND, "No devices to list.");
    }

    for (IDevice possibleDevice : devices) {
      if (serialId.equals(possibleDevice.getSerialNumber())) {
        return possibleDevice;
      }
    }

    String msg = "Device not found: " + serialId;
    logger.atWarning().log("%s", msg);
    throw new MobileHarnessException(AndroidErrorId.DDM_UTIL_DEVICE_NOT_FOUND, msg);
  }

  /**
   * A wrapper around bridge.getDevices() so that tests can override the behavior, since
   * AndroidDebugBridge is final and can't be mocked.
   */
  @VisibleForTesting
  protected IDevice[] getDevices(AndroidDebugBridge bridge) {
    return bridge.getDevices();
  }

  /**
   * A wrapper around bridge.hasInitialDeviceList() so that tests can override the behavior, since
   * AndroidDebugBridge is final and can't be mocked.
   */
  @VisibleForTesting
  protected boolean hasInitialDeviceList(AndroidDebugBridge bridge) {
    return bridge.hasInitialDeviceList();
  }

  /**
   * Retrieves a client for the specified application name running on the device, or returns null if
   * no running application with this name is found.
   *
   * @param applicationName the application name of the process to connect to
   * @return client for given application, or null if none found
   * @throws MobileHarnessException if a device or emulator cannot be reached
   * @throws InterruptedException if our thread was interrupted while polling
   */
  private synchronized Optional<Client> retrieveClient(String applicationName)
      throws MobileHarnessException, InterruptedException {
    // Initialize the device the first time we need it.
    checkDeviceAvailability(true);

    Client client = allClients.get(applicationName);
    if (client != null) {
      logger.atInfo().log(
          "Device %s: Retrieve Client from Cache for application %s", serialId, applicationName);
      return Optional.of(client);
    }

    logger.atInfo().log(
        "Device %s: Retrieving Client from ddmlib for application %s", serialId, applicationName);
    // A new request, search the device for target client.
    for (int retry = 0;
        (client = device.getClient(applicationName)) == null && retry < ADB_MAX_ATTEMPTS;
        retry++) {
      sleeper.sleep(ADB_RETRY_INTERVAL);
    }

    if (client != null) {
      logger.atInfo().log(
          "Device %s: Retrieved Client from ddmlib for application %s", serialId, applicationName);
      allClients.put(applicationName, client);
    } else {
      logger.atInfo().log(
          "Device %s: Client not found for application %s after retries",
          serialId, applicationName);
      // Adding logs to check if any clients are visible on the device. This will help distinguish
      // between a specific process visibility issue and a total DDM bridge failure.
      Client[] clients = device.getClients();
      if (clients != null) {
        logger.atInfo().log("%d clients found on device", clients.length);
      } else {
        logger.atInfo().log("No clients found on device");
      }
    }

    return Optional.ofNullable(client);
  }

  /** A container to store the collection time and associated list of heaps. */
  @AutoValue
  abstract static class HeapCollectionInfo {
    static HeapCollectionInfo create(Instant collectionTime, List<HeapInfo> heapInfos) {
      return new AutoValue_DdmUtil_HeapCollectionInfo(collectionTime, heapInfos);
    }

    public abstract Instant collectionTime();

    public abstract List<HeapInfo> heapInfos();

    /**
     * Combines all the {@code HeapInfo} objects into a single one.
     *
     * @return the combination of all the heaps in the list
     */
    public HeapInfo getCombinedHeapInfo() {
      HeapInfo heapInfo = new HeapInfo(0, 0, 0, 0, 0, (byte) 0);

      if (heapInfos() == null) {
        return heapInfo;
      }

      for (HeapInfo heap : heapInfos()) {
        heapInfo.maxSizeInBytes += heap.maxSizeInBytes;
        heapInfo.sizeInBytes += heap.sizeInBytes;
        heapInfo.bytesAllocated += heap.bytesAllocated;
        heapInfo.objectsAllocated += heap.objectsAllocated;
        heapInfo.timeStamp = Math.max(heapInfo.timeStamp, heap.timeStamp);
      }

      return heapInfo;
    }
  }
}
