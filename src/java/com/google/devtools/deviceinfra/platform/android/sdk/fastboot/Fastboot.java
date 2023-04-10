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

package com.google.devtools.deviceinfra.platform.android.sdk.fastboot;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootDeviceState;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootProperty;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.Partition;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.Slot;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.initializer.FastbootInitializer;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.initializer.FastbootParam;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.constant.DeviceConstants;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.command.LineCallback.Response;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager.Lease;
import com.google.devtools.mobileharness.shared.util.quota.proto.Quota.QuotaKey;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;

/** Executor for invoking fastboot command line tools from Android. */
public class Fastboot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Command args for listing the devices. */
  @VisibleForTesting static final String[] ARGS_LIST_DEVICES = new String[] {"devices"};

  /** Command args for listing the devices. */
  @VisibleForTesting
  static final String[] ARGS_LIST_DEVICES_WITH_PATHS = new String[] {"devices", "-l"};

  /** Default max attempt times if requiring retry. */
  @VisibleForTesting static final int DEFAULT_ATTEMPTS = 2;

  /** Duration used for short-running commands like 'fastboot devices' */
  @VisibleForTesting static final Duration SHORT_COMMAND_DURATION = Duration.ofSeconds(30);

  /** Duration used for flashing. */
  private static final Duration FLASH_COMMAND_DURATION = Duration.ofMinutes(20);

  /** Duration used for fastboot update command. */
  private static final Duration UPDATE_COMMAND_DURATION = Duration.ofMinutes(30);

  /** Duration used for wiping userdata. */
  private static final Duration WIPE_COMMAND_DURATION = Duration.ofMinutes(2);

  private static final Pattern FASTBOOT_SUCCESS_PATTERN =
      Pattern.compile("(okay|finished\\.)", Pattern.CASE_INSENSITIVE);

  private static final String FASTBOOT_DEVICE_INTERFACE_ITERATOR_ERROR_ON_MAC =
      "ERROR: Couldn't create a device interface iterator";

  private final Supplier<FastbootParam> fastbootParamSupplier;

  private final QuotaManager quotaManager;

  /** Command executor for executing fastboot commands. */
  private final CommandExecutor cmdExecutor;

  private final SystemUtil systemUtil;

  /** Command output callback for capturing fastboot command output. */
  private LineCallback outputCallback;

  public Fastboot() {
    this(
        Suppliers.memoize(() -> new FastbootInitializer().initializeFastbootEnvironment()),
        QuotaManager.getInstance(),
        new CommandExecutor(),
        new SystemUtil());
  }

  @VisibleForTesting
  Fastboot(
      Supplier<FastbootParam> fastbootParamSupplier,
      QuotaManager quotaManager,
      CommandExecutor cmdExecutor,
      SystemUtil systemUtil) {
    this.fastbootParamSupplier = fastbootParamSupplier;
    this.quotaManager = quotaManager;
    this.cmdExecutor = cmdExecutor;
    this.systemUtil = systemUtil;
  }

  /** Interface for specifying logic to run on retry. */
  @FunctionalInterface
  public interface RetryTask {
    void onRetry(int retryCount) throws Exception;
  }

  /**
   * Enable fastboot command output to be logged to the class logger.
   *
   * <p>WARNING: This will log ALL command output for this instance of Fastboot. Take caution to
   * make sure this won't unintentionally spam your log.
   */
  public void enableCommandOutputLogging() {
    outputCallback = getLineCallback();
  }

  private LineCallback getLineCallback() {
    return line -> {
      Fastboot.logger.atInfo().log("%s", line);
      return Response.empty();
    };
  }

  /**
   * Boots the fastboot device with specific image.
   *
   * @param serial device serial number
   * @param imagePath the file path of the image
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void boot(String serial, String imagePath)
      throws MobileHarnessException, InterruptedException {
    checkFastboot();
    runWithRetry(
        serial,
        new String[] {"boot", imagePath},
        SHORT_COMMAND_DURATION,
        /* flashSemaphore= */ false);
  }

  /**
   * Flashes a partition on the fastboot device with the given image.
   *
   * @param serial device serial number
   * @param partition image partition
   * @param imagePath the file path of the image
   * @param retryTask a task to run on retry
   * @param extraArgs extra args appended to the command
   * @return the command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String flash(
      String serial,
      Partition partition,
      String imagePath,
      @Nullable RetryTask retryTask,
      String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    checkFastboot();

    String partitionName;
    if (partition == Partition.SYSTEM_OTHER) {
      partitionName = getPartitionNameForSystemOther(serial);
    } else {
      partitionName = Ascii.toLowerCase(partition.name());
    }

    logger.atInfo().log(
        "Flashing device %s partition %s with image file %s", serial, partitionName, imagePath);
    String[] baseCommand = new String[] {"flash", partitionName, imagePath};
    ImmutableList<String> fullCommand =
        ImmutableList.<String>builder()
            .addAll(Arrays.asList(baseCommand))
            .addAll(Arrays.asList(extraArgs))
            .build();

    String output = "";
    try {
      output =
          runWithRetry(
              serial,
              fullCommand.toArray(new String[0]),
              FLASH_COMMAND_DURATION,
              /* flashSemaphore= */ true,
              retryTask);
    } catch (MobileHarnessException e) {
      // Bootloader downgrade will not return OKAY message.
      if (Partition.BOOTLOADER == partition
          && e.getErrorId().equals(AndroidErrorId.ANDROID_FASTBOOT_COMMAND_EXEC_ERROR)) {
        return output;
      } else {
        throw e;
      }
    }
    if (!FASTBOOT_SUCCESS_PATTERN.matcher(output).find()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FASTBOOT_FLASH_PARTITION_ERROR,
          "Failed to flash " + partition + " partition to device " + serial + "\n:" + output);
    }
    return output;
  }

  /**
   * Flashes the fastboot device with image zip file.
   *
   * @param serial device serial number
   * @param imageZipFilePath the file path of the image zip file
   * @param wipeUserdata if wipe the user data along with the flash
   * @param retryTask a task to run on retry
   * @param extraArgs extra args appended to the command
   * @return {@code true} if the flash succeeds
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public boolean update(
      String serial,
      String imageZipFilePath,
      boolean wipeUserdata,
      @Nullable RetryTask retryTask,
      String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    checkFastboot();

    logger.atInfo().log("Flashing device %s with image zip file %s", serial, imageZipFilePath);
    String[] baseCommand = new String[] {"update", imageZipFilePath};
    ImmutableList.Builder<String> fullCommandBuilder =
        ImmutableList.<String>builder()
            .addAll(Arrays.asList(baseCommand))
            .addAll(Arrays.asList(extraArgs));

    if (wipeUserdata) {
      fullCommandBuilder.add("-w");
    }

    String output =
        runWithRetry(
            serial,
            fullCommandBuilder.build().toArray(new String[0]),
            UPDATE_COMMAND_DURATION,
            /* flashSemaphore= */ true,
            retryTask);
    if (!FASTBOOT_SUCCESS_PATTERN.matcher(output).find()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FASTBOOT_UPDATE_COMMAND_EXEC_ERROR,
          "Failed to update " + imageZipFilePath + " to device " + serial + "\n:" + output);
    }
    return true;
  }

  /**
   * Gets serial numbers of the devices in bootloader or fastbootd mode.
   *
   * @return a map of Android device serial to {@link FastbootDeviceState}, or an empty map if no
   *     devices detected
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public Map<String, FastbootDeviceState> getDeviceSerialsAndDetail()
      throws MobileHarnessException, InterruptedException {
    checkFastboot();
    Map<String, FastbootDeviceState> serials = new HashMap<>();
    String output =
        runWithRetry(ARGS_LIST_DEVICES, SHORT_COMMAND_DURATION, /* flashSemaphore= */ false);
    // It is known issue of fastboot on macOS >= 10.13.4 (latest tested 10.14.4). b/64292422
    if (systemUtil.isOnMac() && output.contains(FASTBOOT_DEVICE_INTERFACE_ITERATOR_ERROR_ON_MAC)) {
      return serials;
    }

    // Example output:
    // 014994B00D014014 fastbootd
    // 363005DC750400EC fastboot
    Iterable<String> lines = Splitters.LINE_SPLITTER.split(output);
    for (String line : lines) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }

      List<String> words = Splitter.onPattern("\\s+").splitToList(line);
      try {
        String serial = words.get(0);
        FastbootDeviceState fastbootDeviceState =
            FastbootDeviceState.valueOf(Ascii.toUpperCase(words.get(1)));
        if (DeviceConstants.OUTPUT_DEVICE_DEFAULT_SERIALS.contains(serial)) {
          serials.put("usb:" + getUsbLocation(serial), fastbootDeviceState);
        } else {
          serials.put(serial, fastbootDeviceState);
        }
      } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
        // For some nexus6 devices, when they're in fastboot mode, they may be detected as
        // `ZX1G22MVTL  Motorola Fastboot Interface`
        // then consider them as detected as fastboot device (b/184131823)
        logger.atWarning().withCause(e).log(
            "Unknown device type: %s, consider it as fastboot mode device", words);
        serials.put(words.get(0), FastbootDeviceState.FASTBOOT);
      }
    }
    return serials;
  }

  /**
   * Gets serial numbers of the devices in bootloader or fastbootd mode.
   *
   * @return a set of serial number for devices in bootloader or fastbootd mode
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public Set<String> getDeviceSerials() throws MobileHarnessException, InterruptedException {
    return getDeviceSerialsAndDetail().keySet();
  }

  /** Gets the USB location of a fastboot device, {@code ""} if fails to get the usb location. */
  public String getUsbLocation(String serial) throws InterruptedException {
    try {
      String output =
          runWithRetry(
              ARGS_LIST_DEVICES_WITH_PATHS, SHORT_COMMAND_DURATION, /* flashSemaphore= */ false);
      // Example output:
      // 0256eeebde9d4e25       fastboot
      //  usb:1-9
      // 0A241JECB02904         fastboot
      //  usb:3-1
      //
      // Since fastboot version 33.0.3, the usb locations are merged into the same line:
      // 29271FDH300LFX         fastboot usb:1-14.4.4.2
      List<String> words =
          Splitters.LINE_OR_WHITESPACE_SPLITTER
              .trimResults()
              .omitEmptyStrings()
              .splitToList(output);
      if (words.size() % 3 != 0) {
        logger.atSevere().log("Invalid fastboot -l format %s", output);
        return "";
      }
      for (int i = 0; i < words.size() - 2; i += 3) {
        if (words.get(i).contentEquals(serial)) {
          String usb = words.get(i + 2).trim();
          if (usb.startsWith("usb:")) {
            return usb.substring("usb:".length());
          }
          return "";
        }
      }
    } catch (IndexOutOfBoundsException | MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get usb location of fastboot device %s", serial);
    }
    return "";
  }

  /**
   * Gets the property value of a device. If the device has no value for any keys of the property,
   * will return empty.
   *
   * @return the value of the property, or empty if the system property is not found; will never
   *     return null
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String getVar(String serial, FastbootProperty key)
      throws MobileHarnessException, InterruptedException {
    checkFastboot();
    // Handle vars like "current-slot", which will be represented in enum as "current_slot".
    String keyName = Ascii.toLowerCase(key.name()).replace('_', '-');
    String[] command = new String[] {"getvar", keyName};
    String output =
        runWithRetry(serial, command, Duration.ofSeconds(10L), /* flashSemaphore= */ false);
    Matcher matcher = Pattern.compile(keyName + ": (.+)").matcher(output);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  /**
   * Erases a partition on the fastboot device.
   *
   * @param serial device serial number
   * @param partition image partition being erased
   * @param retryTask a task to run on retry
   * @return the command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String erase(String serial, Partition partition, @Nullable RetryTask retryTask)
      throws MobileHarnessException, InterruptedException {
    checkFastboot();

    String partitionName;
    if (partition == Partition.SYSTEM_OTHER) {
      partitionName = getPartitionNameForSystemOther(serial);
    } else {
      partitionName = Ascii.toLowerCase(partition.name());
    }

    logger.atInfo().log("Erase device %s partition %s", serial, partitionName);
    return runWithRetry(
        serial,
        new String[] {"erase", partitionName},
        WIPE_COMMAND_DURATION,
        /* flashSemaphore= */ false,
        retryTask);
  }

  /**
   * Erases userdata and cache, and formats if supported by partition type.
   *
   * @param serial device serial number
   * @param retryTask a task to run on retry
   * @return the command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String wipe(String serial, @Nullable RetryTask retryTask)
      throws MobileHarnessException, InterruptedException {
    checkFastboot();
    try {
      return runWithRetry(
          serial,
          new String[] {"-w"},
          WIPE_COMMAND_DURATION,
          /* flashSemaphore= */ false,
          retryTask);
    } catch (MobileHarnessException e) {
      // When device is using f2fs for userdata just like volantis, fasboot -w will be failed.
      // This isn't a new failure, just not silently as before in the newest fastboot tool.
      // Fastboot -w means erase userdata and cache(and format if supported by partition type), to
      // avoid the failure, we split the commands and make the result match the older version
      // fastboot, wipe volantis by erasing the userdata partition as opposed to formatting it.
      return String.format(
          "%s\n%s",
          runWithRetry(
              serial,
              new String[] {"erase", "userdata"},
              WIPE_COMMAND_DURATION,
              /* flashSemaphore= */ false,
              retryTask),
          runWithRetry(
              serial,
              new String[] {"format", "cache"},
              WIPE_COMMAND_DURATION,
              /* flashSemaphore= */ false,
              retryTask));
    }
  }

  /** Reboots the device. */
  public void reboot(String serial) throws MobileHarnessException, InterruptedException {
    checkFastboot();
    runWithRetry(
        serial, new String[] {"reboot"}, SHORT_COMMAND_DURATION, /* flashSemaphore= */ false);
  }

  /**
   * Reboots the device to bootloader.
   *
   * @param serial device serial number
   * @return the command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String rebootBootloader(String serial)
      throws MobileHarnessException, InterruptedException {
    checkFastboot();
    String output =
        runWithRetry(
            serial,
            new String[] {"reboot-bootloader"},
            SHORT_COMMAND_DURATION,
            /* flashSemaphore= */ false);

    // 'fastboot reboot-bootloader' can return immediately, before the device has rebooted.
    // If another flash command is issued before the device reboots, the flash will fail.
    //
    // Testing showed a 1-3 second delay before reboot. Wait 5.
    Thread.sleep(5000);

    return output;
  }

  /**
   * Unlocks the bootloader.
   *
   * @param serial device serial number
   * @return the command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String unlock(String serial) throws MobileHarnessException, InterruptedException {
    checkFastboot();
    try {
      return runWithRetry(
          serial,
          new String[] {"oem", "unlock"},
          SHORT_COMMAND_DURATION,
          /* flashSemaphore= */ false);
    } catch (MobileHarnessException e) {
      if (!Ascii.toLowerCase(e.getMessage()).contains("already unlocked")) {
        throw e;
      } else {
        return "Already unlocked";
      }
    }
  }

  /** Gets local path of fastboot. */
  public String getFastbootPath() {
    return fastbootParamSupplier.get().fastbootPath().orElse("");
  }

  /**
   * Runs fastboot command with retry.
   *
   * @param serial device serial number
   * @param args fastboot command line arguments
   * @param timeout amount of time to wait for the command to complete
   * @param flashSemaphore if true, acquires a ticket from config flash semaphore before starting
   *     the command. Time spent waiting for the semaphore does not count against the command
   *     timeout
   * @return the command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String runWithRetry(String serial, String[] args, Duration timeout, boolean flashSemaphore)
      throws MobileHarnessException, InterruptedException {
    return runWithRetry(serial, args, timeout, flashSemaphore, /* retryTask= */ null);
  }

  /**
   * Runs fastboot command with retry.
   *
   * @param serial device serial number
   * @param args fastboot command line arguments
   * @param timeout amount of time to wait for the command to complete
   * @param flashSemaphore if true, acquires a ticket from config flash semaphore before starting
   *     the command. Time spent waiting for the semaphore does not count against the command
   *     timeout
   * @param retryTask a task to run on retry
   * @return the command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String runWithRetry(
      String serial,
      String[] args,
      Duration timeout,
      boolean flashSemaphore,
      @Nullable RetryTask retryTask)
      throws MobileHarnessException, InterruptedException {
    return runWithRetry(
        ArrayUtils.addAll(new String[] {"-s", serial}, args), timeout, flashSemaphore, retryTask);
  }

  /**
   * Runs fastboot command with retry.
   *
   * @param args fastboot command line arguments
   * @param timeout amount of time to wait for the command to complete
   * @param flashSemaphore if true, acquires a ticket from config flash semaphore before starting
   *     the command. Time spent waiting for the semaphore does not count against the command
   *     timeout
   * @return the command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String runWithRetry(String[] args, Duration timeout, boolean flashSemaphore)
      throws MobileHarnessException, InterruptedException {
    return runWithRetry(args, timeout, flashSemaphore, /* retryTask= */ null);
  }

  /**
   * Runs fastboot command with retry.
   *
   * @param args fastboot command line arguments
   * @param timeout amount of time to wait for the command to complete
   * @param flashSemaphore if true, acquires a ticket from config flash semaphore before starting
   *     the command. Time spent waiting for the semaphore does not count against the command
   *     timeout
   * @param retryTask a task to run on retry.
   * @return the command output
   * @throws MobileHarnessException if the command fails or times out
   * @throws InterruptedException if interrupted while waiting for the semaphore or during running
   *     of the command.
   */
  public String runWithRetry(
      String[] args, Duration timeout, boolean flashSemaphore, @Nullable RetryTask retryTask)
      throws MobileHarnessException, InterruptedException {
    Lease lease = null;
    if (flashSemaphore) {
      lease = quotaManager.acquire(QuotaKey.ADB_PUSH_LARGE_FILE, 1);
    }
    try {
      CommandException error = null;
      for (int i = 0; i < DEFAULT_ATTEMPTS; i++) {
        try {
          Command cmd =
              Command.of(
                      ArrayUtils.addAll(
                          new String[] {
                            fastbootParamSupplier
                                .get()
                                .fastbootPath()
                                .orElseThrow(this::getInitializationException)
                          },
                          args))
                  .timeout(timeout);
          if (outputCallback != null) {
            cmd = cmd.onStdout(outputCallback);
          }
          String output = cmdExecutor.exec(cmd).stdoutWithoutTrailingLineTerminator();
          if (error != null) {
            logger.atWarning().log(
                "%s",
                String.format(
                    "fastboot command succeed after retry %s times, last error:%n%s",
                    i, error.getMessage()));
          }
          return output;
        } catch (CommandException e) {
          error = e;
          if (retryTask != null) {
            try {
              retryTask.onRetry(i);
            } catch (Exception retryError) {
              logger.atWarning().log(
                  "%s", String.format("Retry task failed: %s", retryError.getMessage()));
            }
          }
        }
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FASTBOOT_COMMAND_EXEC_ERROR,
          String.format(
              "Abort fastboot command after attempting %d times:%n%s",
              DEFAULT_ATTEMPTS, error.getMessage()),
          error);
    } finally {
      if (flashSemaphore) {
        lease.release();
      }
    }
  }

  /**
   * Checks whether the fastboot has been initialized normally.
   *
   * @return fastboot path if it has been initialized successfully
   * @throws MobileHarnessException if fails to initialize the fastboot
   */
  public String checkFastboot() throws MobileHarnessException {
    Optional<String> fastbootPath = fastbootParamSupplier.get().fastbootPath();
    if (fastbootPath.orElse("").isEmpty()) {
      throw getInitializationException();
    }
    return fastbootPath.get();
  }

  private MobileHarnessException getInitializationException() {
    return new MobileHarnessException(
        AndroidErrorId.ANDROID_FASTBOOT_MISSING_FASTBOOT_BINARY_ERROR,
        fastbootParamSupplier.get().initializationError().orElse(""));
  }

  /** Returns partition for flashing system_other.img ("system", but on a non-default slot). */
  private String getPartitionNameForSystemOther(String serial)
      throws MobileHarnessException, InterruptedException {
    Slot currentSlot;
    String currentSlotName = getVar(serial, FastbootProperty.CURRENT_SLOT);
    try {
      currentSlot = Slot.valueOf(Ascii.toUpperCase(currentSlotName));
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FASTBOOT_UNKNOWN_SLOT, "Unknown slot: " + currentSlotName, e);
    }
    Slot slotForSystemOther = currentSlot == Slot.A ? Slot.B : Slot.A;

    return Ascii.toLowerCase(Partition.SYSTEM.name())
        + "_"
        + Ascii.toLowerCase(slotForSystemOther.name());
  }
}
