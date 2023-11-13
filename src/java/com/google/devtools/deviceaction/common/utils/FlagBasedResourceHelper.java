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

import static com.google.devtools.deviceaction.common.utils.ResourceUtil.createSessionDir;
import static com.google.devtools.deviceaction.common.utils.ResourceUtil.filterExistingFile;
import static com.google.devtools.deviceaction.common.utils.ResourceUtil.getExistingDir;

import com.google.common.base.Suppliers;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/** A {@link ResourceHelper} class based on command line flags. */
public final class FlagBasedResourceHelper implements ResourceHelper {

  private static final FlagBasedResourceHelper INSTANCE = new FlagBasedResourceHelper();

  private static final Supplier<Adb> ADB_SUPPLIER = Suppliers.memoize(Adb::new);

  private static final Supplier<Aapt> AAPT_SUPPLIER = Suppliers.memoize(Aapt::new);

  private static final Path JAVA_BIN =
      Path.of(Flags.instance().javaCommandPath.getNonNull()).normalize();

  private static final Path TMP_FILE_ROOT =
      Path.of(Flags.instance().tmpDirRoot.getNonNull()).normalize();

  private static final Supplier<Path> TMP_FILE_DIR_SUPPLIER =
      Suppliers.memoize(() -> createSessionDir(TMP_FILE_ROOT));

  private static final Path GEN_FILE_ROOT =
      Path.of(Flags.instance().daGenFileDir.getNonNull()).normalize();

  private static final Supplier<Path> GEN_FILE_DIR_SUPPLIER =
      Suppliers.memoize(() -> createSessionDir(GEN_FILE_ROOT));

  private static final Path BUNDLETOOL_JAR =
      Path.of(Flags.instance().daBundletool.getNonNull()).normalize();

  private static final Path CRED_FILE =
      Path.of(Flags.instance().daCredFile.getNonNull()).normalize();

  private static final CommandExecutor executor = new CommandExecutor();

  public static FlagBasedResourceHelper getInstance() {
    return INSTANCE;
  }

  @Override
  public Path getTmpFileDir() throws DeviceActionException {
    return getExistingDir(TMP_FILE_DIR_SUPPLIER.get());
  }

  @Override
  public Path getGenFileDir() throws DeviceActionException {
    return getExistingDir(GEN_FILE_DIR_SUPPLIER.get());
  }

  @Override
  public Path getJavaBin() {
    return JAVA_BIN;
  }

  @Override
  public Optional<Aapt> getAapt() {
    return Optional.of(AAPT_SUPPLIER.get());
  }

  @Override
  public Optional<Adb> getAdb() {
    return Optional.of(ADB_SUPPLIER.get());
  }

  @Override
  public Optional<Path> getBundletoolJar() {
    return filterExistingFile(BUNDLETOOL_JAR);
  }

  @Override
  public Optional<Path> getCredFile() {
    return filterExistingFile(CRED_FILE);
  }

  @Override
  public CommandExecutor getCommandExecutor() {
    return executor;
  }

  private FlagBasedResourceHelper() {}
}
