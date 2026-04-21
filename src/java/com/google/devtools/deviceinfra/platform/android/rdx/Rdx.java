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

package com.google.devtools.deviceinfra.platform.android.rdx;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** A wrapper for the RDX command-line tool. */
public class Rdx {

  private final CommandExecutor cmdExecutor;
  private final String rdxPath;
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration LONG_TIMEOUT =
      Duration.ofMinutes(5); // For operations like download

  public Rdx(String rdxPath) {
    this.cmdExecutor = new CommandExecutor();
    this.rdxPath = rdxPath;
  }

  public Rdx(String rdxPath, CommandExecutor executor) {
    this.cmdExecutor = executor;
    this.rdxPath = rdxPath;
  }

  @CanIgnoreReturnValue
  private String runCommand(ImmutableList<String> args, Duration timeout)
      throws MobileHarnessException {
    Command command = Command.of(rdxPath).args(args).timeout(timeout);
    try {
      CommandResult result = cmdExecutor.exec(command);
      if (result.exitCode() != 0) {
        throw new MobileHarnessException(
            BasicErrorId.COMMAND_EXEC_FAIL,
            String.format(
                "RDX command failed with exit code %d: %s\nSTDOUT: %s\nSTDERR: %s",
                result.exitCode(), command.getCommand(), result.stdout(), result.stderr()));
      }
      return result.stdout();
    } catch (MobileHarnessException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          BasicErrorId.COMMAND_EXEC_FAIL, "RDX command interrupted: " + command.getCommand(), e);
    }
  }

  /** Gets the version of the RDX tool. Corresponds to: rdx version */
  public String getVersion() throws MobileHarnessException, InterruptedException {
    return runCommand(ImmutableList.of("version"), DEFAULT_TIMEOUT);
  }

  /** Lists available device paths. Corresponds to: rdx devices */
  public List<String> getDevicePaths() throws MobileHarnessException, InterruptedException {
    String output = runCommand(ImmutableList.of("devices"), DEFAULT_TIMEOUT);
    return Splitter.on('\n').trimResults().omitEmptyStrings().splitToList(output);
  }

  /** Lists partition info for a device. Corresponds to: rdx list [<device_path>] */
  public String listPartitions(Optional<String> devicePath)
      throws MobileHarnessException, InterruptedException {
    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add("list");
    devicePath.ifPresent(args::add);
    return runCommand(args.build(), DEFAULT_TIMEOUT);
  }

  /** Downloads all dump files. Corresponds to: rdx download-all <target_dir> [<device_path>] */
  public void downloadAll(String targetDir, Optional<String> devicePath)
      throws MobileHarnessException, InterruptedException {
    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add("download-all").add(targetDir);
    devicePath.ifPresent(args::add);
    runCommand(args.build(), LONG_TIMEOUT);
  }

  /**
   * Downloads a specific partition. Corresponds to: rdx download <partition_num> <target_dir>
   * [<device_path>]
   */
  public void downloadPartition(int partitionNum, String targetDir, Optional<String> devicePath)
      throws MobileHarnessException, InterruptedException {
    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add("download").add(String.valueOf(partitionNum)).add(targetDir);
    devicePath.ifPresent(args::add);
    runCommand(args.build(), LONG_TIMEOUT);
  }

  /** Reboots the device. Corresponds to: rdx reboot [<device_path>] */
  public void reboot(Optional<String> devicePath)
      throws MobileHarnessException, InterruptedException {
    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add("reboot");
    devicePath.ifPresent(args::add);
    runCommand(args.build(), DEFAULT_TIMEOUT);
  }

  /** Changes to Odin mode. Corresponds to: rdx odin [<device_path>] */
  public void odinMode(Optional<String> devicePath)
      throws MobileHarnessException, InterruptedException {
    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add("odin");
    devicePath.ifPresent(args::add);
    runCommand(args.build(), DEFAULT_TIMEOUT);
  }
}
