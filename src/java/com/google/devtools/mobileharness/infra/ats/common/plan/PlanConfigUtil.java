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

package com.google.devtools.mobileharness.infra.ats.common.plan;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.plan.JarFileUtil.EntryFilter;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.FileNameUtil;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/** Utility to get the plan configuration info. */
public class PlanConfigUtil {

  /** Information for the plan configuration. */
  @AutoValue
  public abstract static class PlanConfigInfo {

    /** Creates a {@link PlanConfigInfo}. */
    public static PlanConfigInfo of(String configName, Path source, String description) {
      return new AutoValue_PlanConfigUtil_PlanConfigInfo(configName, source, description);
    }

    /** The name for the plan config. */
    public abstract String configName();

    /** The path of source file from which the plan config is, that could be a JAR file. */
    public abstract Path source();

    /** The description for the plan config. */
    public abstract String description();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Currently supported extensions for TF configurations */
  private static final ImmutableSet<String> SUPPORTED_EXTENSIONS = ImmutableSet.of("xml", "config");

  private static final String CONFIG_PREFIX = "config/";

  private final JarFileUtil jarFileUtil;
  private final LocalFileUtil localFileUtil;
  private final FileNameUtil fileNameUtil;

  /** A {@link EntryFilter} for plan configuration XML files. */
  private class ConfigInJarFilter implements EntryFilter {

    private String prefix;

    public ConfigInJarFilter(@Nullable String prefixSubPath) {
      prefix = getConfigPrefix();

      if (prefixSubPath != null) {
        prefix += prefixSubPath;
      }
    }

    @Override
    public boolean accept(String pathName) {
      String extension = fileNameUtil.getExtension(pathName);
      return pathName.startsWith(prefix) && SUPPORTED_EXTENSIONS.contains(extension);
    }

    @Override
    public String transform(String pathName) {
      // Strip off CONFIG_PREFIX and config extension
      int pathStartIndex = getConfigPrefix().length();
      String extension = fileNameUtil.getExtension(pathName);
      int pathEndIndex = pathName.length() - (extension.length() + 1);
      return pathName.substring(pathStartIndex, pathEndIndex);
    }
  }

  @Inject
  PlanConfigUtil(JarFileUtil jarFileUtil, LocalFileUtil localFileUtil, FileNameUtil fileNameUtil) {
    this.jarFileUtil = jarFileUtil;
    this.localFileUtil = localFileUtil;
    this.fileNameUtil = fileNameUtil;
  }

  /**
   * Loads info of all plan configs from the JAR files in {@code dir}.
   *
   * @return all plan configs or empty if some error occurs
   */
  public ImmutableMap<String, PlanConfigInfo> loadAllConfigsInfo(Path dir) {
    ImmutableMap.Builder<String, PlanConfigInfo> configNameToPlanConfigInfo =
        ImmutableMap.builder();
    try {
      List<Path> jars =
          localFileUtil.listFilePaths(
              dir, /* recursively= */ false, p -> p.getFileName().toString().endsWith(".jar"));

      ImmutableMap<String, Path> configNameToJar = getConfigsFromJars(jars, /* subPath= */ null);
      for (Map.Entry<String, Path> entry : configNameToJar.entrySet()) {
        String configName = entry.getKey();
        Path jarPath = entry.getValue();
        Optional<Document> document = loadConfig(configName, jarPath);
        if (document.isEmpty()) {
          continue;
        }
        configNameToPlanConfigInfo.put(
            configName, loadPlanConfigInfo(configName, jarPath, document.get()));
      }
    } catch (MobileHarnessException e) {
      logger
          .atWarning()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Failed to load plan configs from JAR files in dir %s: %s",
              dir, MoreThrowables.shortDebugString(e));
    }
    return configNameToPlanConfigInfo.buildOrThrow();
  }

  public Optional<Document> loadConfig(String configName, Path jar) {
    Optional<InputStream> configStream = getBundledConfigStream(jar, configName);
    if (configStream.isEmpty()) {
      return Optional.empty();
    }

    Document document;
    try (BufferedInputStream bufferedInputStream = new BufferedInputStream(configStream.get())) {
      try {
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        document = documentBuilder.parse(bufferedInputStream);
      } catch (ParserConfigurationException | SAXException | IOException e) {
        logger
            .atWarning()
            .with(IMPORTANCE, IMPORTANT)
            .log(
                "Failed to load the config [%s] from %s: %s",
                configName, jar, MoreThrowables.shortDebugString(e));
        return Optional.empty();
      }
    } catch (IOException e) {
      logger
          .atWarning()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Failed to load the config [%s] from %s: %s",
              configName, jar, MoreThrowables.shortDebugString(e));
      return Optional.empty();
    }
    return Optional.of(document);
  }

  public Optional<InputStream> getBundledConfigStream(Path jar, String name) {
    String ext = fileNameUtil.getExtension(name);
    if (Strings.isNullOrEmpty(ext)) {
      // If the default name doesn't have an extension, search all possible extensions.
      for (String supportedExt : SUPPORTED_EXTENSIONS) {
        Optional<InputStream> inputStream =
            jarFileUtil.getZipEntryInputStream(
                jar, String.format("%s%s.%s", getConfigPrefix(), name, supportedExt));
        if (inputStream.isPresent()) {
          return inputStream;
        }
      }
      return Optional.empty();
    }

    return jarFileUtil.getZipEntryInputStream(jar, String.format("%s%s", getConfigPrefix(), name));
  }

  private PlanConfigInfo loadPlanConfigInfo(String configName, Path jar, Document document) {
    return PlanConfigInfo.of(
        configName, jar, document.getDocumentElement().getAttribute("description"));
  }

  @SuppressWarnings("SameParameterValue")
  private ImmutableMap<String, Path> getConfigsFromJars(
      List<Path> jarFiles, @Nullable String subPath) {
    return jarFileUtil.getEntriesFromJars(jarFiles, new ConfigInJarFilter(subPath));
  }

  /**
   * Gets the path prefix of plan config xml files in the JAR.
   *
   * @return {@link String} path with trailing /
   */
  private String getConfigPrefix() {
    return CONFIG_PREFIX;
  }
}
