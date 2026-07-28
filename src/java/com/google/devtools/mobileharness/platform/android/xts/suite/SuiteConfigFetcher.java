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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.common.plan.JarFileUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationXmlParser;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Option;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Utility class for fetching suite-level configuration options from tradefed JAR. */
public class SuiteConfigFetcher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String OPTION_ENABLE_PARAMETERIZED_MODULES = "enable-parameterized-modules";
  private static final String OPTION_ENABLE_OPTIONAL_PARAMETERIZATION =
      "enable-optional-parameterization";
  private static final String OPTION_PRIMARY_ABI_ONLY = "primary-abi-only";

  private final JarFileUtil jarFileUtil;

  public SuiteConfigFetcher() {
    this(new JarFileUtil());
  }

  @VisibleForTesting
  public SuiteConfigFetcher(JarFileUtil jarFileUtil) {
    this.jarFileUtil = jarFileUtil;
  }

  /** Suite configuration options loaded from suite XML. */
  @AutoValue
  public abstract static class SuiteConfig {
    public abstract Optional<Boolean> allowParameterizedModules();

    public abstract Optional<Boolean> allowOptionalParameterizedModules();

    public abstract Optional<Boolean> primaryAbiOnly();

    public static Builder builder() {
      return new AutoValue_SuiteConfigFetcher_SuiteConfig.Builder();
    }

    /** Builder for {@link SuiteConfig}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setAllowParameterizedModules(Boolean allowParameterizedModules);

      public abstract Builder setAllowOptionalParameterizedModules(
          Boolean allowOptionalParameterizedModules);

      public abstract Builder setPrimaryAbiOnly(Boolean primaryAbiOnly);

      public abstract SuiteConfig build();
    }
  }

  /** Fetches {@link SuiteConfig} for the given {@code xtsRootDir} and {@code xtsType}. */
  public SuiteConfig fetchConfig(String xtsRootDir, String xtsType) {
    Path toolsDir = XtsDirUtil.getXtsToolsDir(Path.of(xtsRootDir), xtsType);
    Path xtsTfJar = toolsDir.resolve(String.format("%s-tradefed.jar", xtsType));
    if (!Files.exists(xtsTfJar)) {
      return SuiteConfig.builder().build();
    }

    Optional<InputStream> inputStream =
        jarFileUtil.getZipEntryInputStream(xtsTfJar, String.format("config/%s.xml", xtsType));

    if (inputStream.isEmpty()) {
      logger.atInfo().log(
          "No suite configuration XML found for xtsType '%s' in JAR %s", xtsType, xtsTfJar);
      return SuiteConfig.builder().build();
    }

    try (InputStream stream = inputStream.get()) {
      Configuration config = ConfigurationXmlParser.parse(stream, String.format("%s.xml", xtsType));
      SuiteConfig.Builder builder = SuiteConfig.builder();
      for (Option option : config.getOptionsList()) {
        String name = option.getName();
        String canonicalName =
            name.contains(":") ? name.substring(name.lastIndexOf(':') + 1) : name;
        String value = option.getValue();
        if (canonicalName.equals(OPTION_ENABLE_PARAMETERIZED_MODULES)) {
          builder.setAllowParameterizedModules(Boolean.parseBoolean(value));
        } else if (canonicalName.equals(OPTION_ENABLE_OPTIONAL_PARAMETERIZATION)) {
          builder.setAllowOptionalParameterizedModules(Boolean.parseBoolean(value));
        } else if (canonicalName.equals(OPTION_PRIMARY_ABI_ONLY)) {
          builder.setPrimaryAbiOnly(Boolean.parseBoolean(value));
        }
      }
      return builder.build();
    } catch (Exception e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse suite configuration for xtsType '%s' from JAR %s", xtsType, xtsTfJar);
      return SuiteConfig.builder().build();
    }
  }
}
