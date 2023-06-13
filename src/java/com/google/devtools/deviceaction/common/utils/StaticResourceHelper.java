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

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A {@link ResourceHelper} class that can be created by a factory method.
 *
 * <p>This class is suitable when resources are available at different stages. Resources like adb,
 * aapt, java bin can be obtained when we create the factory class. On the other hand, the files can
 * be obtained at a later stage when we call the {@link Factory#create(Path, Path, Path, Path)}
 * method.
 */
public final class StaticResourceHelper implements ResourceHelper {

  private final Path tmpFileDir;

  private final Path genFileDir;

  private final Path javaBin;

  private final Aapt aapt;

  private final Adb adb;

  private final Path bundletoolJar;

  private final Path credFile;

  private final CommandExecutor commandExecutor = new CommandExecutor();

  private StaticResourceHelper(
      Path tmpFileDir,
      Path genFileDir,
      Path javaBin,
      Aapt aapt,
      Adb adb,
      Path bundletoolJar,
      Path credFile) {
    this.tmpFileDir = tmpFileDir;
    this.genFileDir = genFileDir;
    this.javaBin = javaBin;
    this.aapt = aapt;
    this.adb = adb;
    this.bundletoolJar = bundletoolJar;
    this.credFile = credFile;
  }

  @Override
  public Path getTmpFileDir() throws DeviceActionException {
    return getExistingDir(tmpFileDir);
  }

  @Override
  public Path getGenFileDir() throws DeviceActionException {
    return getExistingDir(genFileDir);
  }

  @Override
  public Path getJavaBin() {
    return javaBin;
  }

  @Override
  public Optional<Aapt> getAapt() {
    return Optional.of(aapt);
  }

  @Override
  public Optional<Adb> getAdb() {
    return Optional.of(adb);
  }

  @Override
  public Optional<Path> getBundletoolJar() {
    return filterExistingFile(bundletoolJar);
  }

  @Override
  public Optional<Path> getCredFile() {
    return filterExistingFile(credFile);
  }

  @Override
  public CommandExecutor getCommandExecutor() {
    return commandExecutor;
  }

  /** A factory class for {@link StaticResourceHelper}. */
  public static class Factory {

    private final Adb adb;
    private final Aapt aapt;
    private final Path javaBin;

    public Factory(Adb adb, Aapt aapt, Path javaBin) {
      this.adb = adb;
      this.aapt = aapt;
      this.javaBin = javaBin;
    }

    /** Creates a {@link StaticResourceHelper} with local files. */
    public StaticResourceHelper create(
        Path tmpFileDir, Path genFileDir, Path bundletoolJar, Path credFile)
        throws DeviceActionException {
      return new StaticResourceHelper(
          createSessionDir(tmpFileDir),
          createSessionDir(genFileDir),
          javaBin,
          aapt,
          adb,
          bundletoolJar,
          credFile);
    }
  }
}
