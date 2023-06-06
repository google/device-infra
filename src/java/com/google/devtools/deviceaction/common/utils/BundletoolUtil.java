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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.devtools.deviceaction.common.utils.Constants.APKS_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.ZIP_SUFFIX;
import static java.util.Collections.addAll;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import javax.inject.Inject;

/** A util class to execute bundletool cmds. */
public class BundletoolUtil {

  /** Command {@code get-device-spec}. */
  private static final String GET_DEVICE_SPEC = "get-device-spec";

  /** Flags for get-device-spec. */
  private static final String OUTPUT_FLAG = "--output";

  private static final String OVERWRITE_FLAG = "--overwrite";

  /** Command {@code extract-apks}. */
  private static final String EXTRACT_APKS = "extract-apks";

  /** Flags for extract-apks. */
  private static final String DEVICE_SPEC_FLAG = "--device-spec";

  private static final String OUTPUT_DIR_FLAG = "--output-dir";

  /** Command {@code install-apks}. */
  private static final String INSTALL_APKS = "install-apks";

  /** Command {@code install-multi-apks}. */
  private static final String INSTALL_MULTI_APKS = "install-multi-apks";

  /** Flags for install-apks and install-multi-apks. */
  private static final String AAPT_FLAG = "--aapt2";

  private static final String ADB_FLAG = "--adb";
  private static final String APKS_FLAG = "--apks";
  private static final String APKS_ZIPS_FLAG = "--apks-zip";
  private static final String DEVICE_ID_FLAG = "--device-id";

  /** Command {@code version}. */
  private static final String VERSION = "version";

  private static final String DELIMITER = ",";
  private static final String SPLITS_KEYWORD = "-Splits";

  private final Optional<File> aaptPath;
  private final Optional<File> adbPath;
  private final Path bundletoolJarPath;
  private final Path javaBinPath;
  private final Path genFileDirPath;
  private final CommandExecutor executor;
  private final LocalFileUtil localFileUtil;

  @Inject
  BundletoolUtil(ResourceHelper resourceHelper, LocalFileUtil localFileUtil)
      throws DeviceActionException {
    this(
        resourceHelper.getBundletoolJar(),
        resourceHelper.getAapt().map(aapt -> new File(aapt.getAaptPath())),
        resourceHelper.getAdb().map(adb -> new File(adb.getAdbPath())),
        resourceHelper.getJavaBin(),
        resourceHelper.getGenFileDir(),
        resourceHelper.getCommandExecutor(),
        localFileUtil);
  }

  @VisibleForTesting
  BundletoolUtil(
      Optional<Path> bundletoolJar,
      Optional<File> aaptFile,
      Optional<File> adbFile,
      Path javaBinPath,
      Path genFileDirPath,
      CommandExecutor executor,
      LocalFileUtil localFileUtil)
      throws DeviceActionException {
    this.bundletoolJarPath =
        bundletoolJar.orElseThrow(
            () ->
                new DeviceActionException(
                    "MISSING_FILE", ErrorType.CUSTOMER_ISSUE, "Bundletool jar not exist."));
    this.aaptPath = aaptFile;
    this.adbPath = adbFile;
    this.javaBinPath = javaBinPath;
    this.genFileDirPath = genFileDirPath;
    this.executor = executor;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Generates a device spec file for the device.
   *
   * <p>Always overwrites the previously existing file.
   *
   * @param serialId of the connected device.
   * @return the path to the device spec file.
   */
  public Path generateDeviceSpecFile(String serialId)
      throws DeviceActionException, InterruptedException {
    List<String> args = new ArrayList<>();
    args.add(GET_DEVICE_SPEC);
    Path output = Paths.get(genFileDirPath.toString(), serialId, "device-spec.json");
    try {
      localFileUtil.prepareDir(output.getParent());
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to prepare dir %s", output.getParent());
    }
    args.add(createFlag(OUTPUT_FLAG, output.toString()));
    adbPath.ifPresent(file -> args.add(createFlag(ADB_FLAG, file.getAbsolutePath())));
    args.add(createFlag(DEVICE_ID_FLAG, serialId));
    args.add(OVERWRITE_FLAG);
    runCmd(args);
    return output;
  }

  /**
   * Extracts apks and returns the output dir.
   *
   * @param apksFile apks file to extract
   * @param deviceSpecFilePath path to the device spec file.
   * @param options the rest options.
   * @return the path to the directory containing the extracted files.
   */
  public Path extractApks(File apksFile, Path deviceSpecFilePath, String... options)
      throws DeviceActionException, InterruptedException {
    checkArgument(apksFile.getName().endsWith(APKS_SUFFIX));
    List<String> args = new ArrayList<>();
    args.add(EXTRACT_APKS);
    args.add(createFlag(APKS_FLAG, apksFile.getAbsolutePath()));
    args.add(createFlag(DEVICE_SPEC_FLAG, deviceSpecFilePath.toString()));
    // The apks name pattern is "modulename + .apks"
    Path extractOutputDir =
        Paths.get(
            genFileDirPath.toString(),
            localFileUtil.getFileOrDirNameWithoutExtension(apksFile.getName()) + SPLITS_KEYWORD);
    try {
      localFileUtil.prepareDir(extractOutputDir);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to prepare dir %s", extractOutputDir);
    }
    args.add(createFlag(OUTPUT_DIR_FLAG, extractOutputDir.toString()));
    addAll(args, options);
    runCmd(args);
    return extractOutputDir;
  }

  /**
   * Executes the command {@code install-apks} and returns the output.
   *
   * @param serialId of the device.
   * @param apksFile one apks file.
   * @param options the installation options.
   * @return the output of the command.
   */
  public String installApks(String serialId, File apksFile, String... options)
      throws DeviceActionException, InterruptedException {
    checkArgument(apksFile.getName().endsWith(APKS_SUFFIX));
    List<String> args = new ArrayList<>();
    args.add(INSTALL_APKS);
    args.add(createFlag(DEVICE_ID_FLAG, serialId));
    args.add(createFlag(APKS_FLAG, apksFile.getAbsolutePath()));
    adbPath.ifPresent(file -> args.add(createFlag(ADB_FLAG, file.getAbsolutePath())));
    addAll(args, options);
    return runCmd(args);
  }

  /**
   * Executes the command {@code install-multi-apks} to install multiple apks and returns the
   * output.
   *
   * @param serialId of the device.
   * @param apksFiles a list of apks files.
   * @param options the installation options.
   * @return the output of the command.
   */
  public String installMultiApks(String serialId, List<File> apksFiles, String... options)
      throws DeviceActionException, InterruptedException {
    checkArgument(apksFiles.stream().allMatch(f -> f.getName().endsWith(APKS_SUFFIX)));
    checkArgument(apksFiles.size() >= 1);
    return runInstallMultiApks(
        serialId,
        createFlag(APKS_FLAG, apksFiles.stream().map(File::getAbsolutePath).toArray(String[]::new)),
        options);
  }

  /**
   * Executes the command {@code install-multi-apks} to install a zipped train and returns the
   * output.
   *
   * @param serialId of the device.
   * @param zipFile the zip file containing a train.
   * @param options the installation options.
   * @return the output of the command.
   */
  public String installApksZip(String serialId, File zipFile, String... options)
      throws DeviceActionException, InterruptedException {
    checkArgument(zipFile.getName().endsWith(ZIP_SUFFIX));
    return runInstallMultiApks(
        serialId, createFlag(APKS_ZIPS_FLAG, zipFile.getAbsolutePath()), options);
  }

  public String getVersion() throws DeviceActionException, InterruptedException {
    List<String> args = new ArrayList<>();
    args.add(VERSION);
    return runCmd(args).trim();
  }

  private String runInstallMultiApks(String serialId, String fileOption, String... others)
      throws DeviceActionException, InterruptedException {
    List<String> args = new ArrayList<>();
    args.add(INSTALL_MULTI_APKS);
    args.add(createFlag(DEVICE_ID_FLAG, serialId));
    args.add(fileOption);
    aaptPath.ifPresent(file -> args.add(createFlag(AAPT_FLAG, file.getAbsolutePath())));
    adbPath.ifPresent(file -> args.add(createFlag(ADB_FLAG, file.getAbsolutePath())));
    addAll(args, others);
    return runCmd(args);
  }

  @CanIgnoreReturnValue
  private String runCmd(List<String> args) throws DeviceActionException, InterruptedException {
    Path workingDir = bundletoolJarPath.getParent();
    String bundletool = bundletoolJarPath.getFileName().toString();
    List<String> argList = new ArrayList<>();
    argList.add("-jar");
    argList.add(bundletool);
    argList.addAll(args);
    Command cmd = Command.of(javaBinPath.toString(), argList).workDir(workingDir);
    try {
      return executor.run(cmd);
    } catch (CommandException e) {
      throw new DeviceActionException(e, "Failed to execute bundletool cmd %s", cmd);
    }
  }

  private String createFlag(String key, String... args) {
    StringJoiner sj = new StringJoiner(DELIMITER);
    for (String s : args) {
      sj.add(s);
    }
    return String.format("%s=%s", key, sj);
  }
}
