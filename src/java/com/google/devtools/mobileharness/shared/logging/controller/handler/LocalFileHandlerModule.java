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

package com.google.devtools.mobileharness.shared.logging.controller.handler;

import com.google.common.base.Strings;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.logging.controller.handler.Annotations.LoggerHandler;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogFormatter;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.throwingproviders.CheckedProvider;
import com.google.inject.throwingproviders.CheckedProvides;
import com.google.inject.throwingproviders.ThrowingProviderBinder;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import javax.annotation.Nullable;

/** Module for providing local {@link FileHandler}. */
public final class LocalFileHandlerModule extends AbstractModule {
  /** The provider for providing {@link FileHandler}s. */
  public interface LocalFileHandlerProvider extends CheckedProvider<Optional<FileHandler>> {

    @Override
    Optional<FileHandler> get() throws MobileHarnessException;
  }

  /** Maximum number of bytes to write to any one file. */
  private static final int LOG_FILE_SIZE_LIMIT = 10 * 1024 * 1024;

  @Nullable private final String logFileDir;

  LocalFileHandlerModule(@Nullable String logFileDir) {
    this.logFileDir = logFileDir;
  }

  @Override
  protected void configure() {
    install(ThrowingProviderBinder.forModule(this));

    if (logFileDir != null) {
      // Only add the multibinding if logFileDir is non-null, since provideFileHandler provides a
      // FileHandler only when logFileDir is present.
      Multibinder.newSetBinder(binder(), Handler.class, LoggerHandler.class)
          .addBinding()
          .to(FileHandler.class);
    }
  }

  @CanIgnoreReturnValue
  @CheckedProvides(LocalFileHandlerProvider.class)
  @Singleton
  Optional<FileHandler> provideFileHandler(LocalFileUtil localFileUtil)
      throws MobileHarnessException {
    if (logFileDir == null) {
      return Optional.empty();
    }
    if (Strings.isNullOrEmpty(logFileDir)) {
      throw new MobileHarnessException(
          InfraErrorId.LOGGER_CREATE_FILE_HANDLER_ERROR,
          "Failed to create file handler, the file name is empty.");
    }

    String logFilePattern = PathUtil.join(logFileDir, DirCommon.DEFAULT_LOG_FILE_NAME);
    try {
      localFileUtil.prepareDir(logFileDir);
      localFileUtil.grantFileOrDirFullAccess(logFileDir);
      FileHandler fileHandler =
          new FileHandler(
              logFilePattern, LOG_FILE_SIZE_LIMIT, Flags.instance().logFileNumber.get());
      fileHandler.setFormatter(MobileHarnessLogFormatter.getDefaultFormatter());
      fileHandler.setLevel(Level.INFO);
      return Optional.of(fileHandler);
    } catch (MobileHarnessException | IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.LOGGER_CREATE_FILE_HANDLER_ERROR, "Failed to create file handler", e);
    }
  }
}
