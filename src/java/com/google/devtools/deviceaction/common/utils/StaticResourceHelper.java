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

import com.google.auto.value.AutoValue;
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
@AutoValue
public abstract class StaticResourceHelper implements ResourceHelper {
  @Override
  public abstract Path getTmpFileDir();

  @Override
  public abstract Path getGenFileDir();

  @Override
  public abstract Path getJavaBin();

  @Override
  public abstract Optional<Aapt> getAapt();

  @Override
  public abstract Optional<Adb> getAdb();

  @Override
  public abstract Optional<Path> getBundletoolJar();

  @Override
  public abstract Optional<Path> getCredFile();

  @Override
  public abstract CommandExecutor getCommandExecutor();

  private static StaticResourceHelper create(
      Path tmpFileDir,
      Path genFileDir,
      Path javaBin,
      Optional<Aapt> aapt,
      Optional<Adb> adb,
      Optional<Path> bundletoolJar,
      Optional<Path> credFile) {
    return new AutoValue_StaticResourceHelper(
        tmpFileDir, genFileDir, javaBin, aapt, adb, bundletoolJar, credFile, new CommandExecutor());
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
        Path tmpFileDir, Path genFileDir, Path bundleToolJar, Path credFile)
        throws DeviceActionException {
      return StaticResourceHelper.create(
          getExistingDir(createSessionDir(tmpFileDir)),
          getExistingDir(createSessionDir(genFileDir)),
          javaBin,
          Optional.of(aapt),
          Optional.of(adb),
          filterExistingFile(bundleToolJar),
          filterExistingFile(credFile));
    }
  }
}
