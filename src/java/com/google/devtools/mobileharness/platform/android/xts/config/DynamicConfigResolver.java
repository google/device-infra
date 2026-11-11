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

package com.google.devtools.mobileharness.platform.android.xts.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.JsonSyntaxException;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Resolves a dynamic config file by merging the local dynamic config of an xTS suite or module with
 * the config served by the Android partner server.
 */
public class DynamicConfigResolver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;

  @Inject
  @VisibleForTesting
  public DynamicConfigResolver(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Returns a temporary file with the merged dynamic config described by {@code spec}.
   *
   * @param spec the dynamic config spec, whose {@code config_filename} and {@code version} must be
   *     set
   * @param suiteName the xTS suite name (e.g. "GTS"), used to locate the suite tradefed JAR and to
   *     build the partner server URL
   */
  public File resolveFile(
      AndroidAtsDynamicConfigPusherDecoratorSpec spec, @Nullable String suiteName)
      throws MobileHarnessException, InterruptedException {
    File localConfigFile = getLocalConfigFile(spec, suiteName);
    String apfeConfig = fetchApfeConfig(spec, suiteName);
    return mergeConfigFiles(spec, localConfigFile, apfeConfig);
  }

  /**
   * Gets the local config file.
   *
   * <p>If {@code extractFromResource} is true, it attempts to extract the dynamic config file from
   * the suite's tradefed JAR.
   *
   * <p>If {@code extractFromResource} is false, it searches for the dynamic config file directly in
   * the XTS test directory.
   */
  private File getLocalConfigFile(
      AndroidAtsDynamicConfigPusherDecoratorSpec spec, @Nullable String suiteName)
      throws MobileHarnessException {
    String lookupName =
        spec.getExtractFromResource()
            ? (spec.getDynamicResourceName().isEmpty()
                ? spec.getConfigFilename()
                : spec.getDynamicResourceName())
            : (spec.getDynamicConfigName().isEmpty()
                ? spec.getConfigFilename()
                : spec.getDynamicConfigName());
    String fileName = String.format("%s.dynamic", lookupName);

    if (spec.getExtractFromResource()) {
      if (suiteName != null && !spec.getXtsTestDir().isEmpty()) {
        Path xtsRootDir = Path.of(spec.getXtsTestDir()).getParent().getParent();
        File toolsDir = XtsDirUtil.getXtsToolsDir(xtsRootDir, suiteName).toFile();
        File tradefedJar = new File(toolsDir, Ascii.toLowerCase(suiteName) + "-tradefed.jar");
        if (tradefedJar.exists()) {
          try (ZipFile zipFile = new ZipFile(tradefedJar)) {
            ZipEntry entry = zipFile.getEntry(fileName);
            if (entry != null) {
              try (InputStream inputStream = zipFile.getInputStream(entry)) {
                File localConfigFile = File.createTempFile(lookupName, ".dynamic");
                Files.copy(
                    inputStream, localConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return localConfigFile;
              }
            }
          } catch (IOException e) {
            logger.atWarning().withCause(e).log(
                "Failed to read from %s", tradefedJar.getAbsolutePath());
          }
        }
      }

      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_LOCAL_CONFIG_NOT_FOUND,
          String.format("Fail to find '%s' in tradefed jar", fileName));
    }

    List<File> files = localFileUtil.listFiles(spec.getXtsTestDir(), /* recursively= */ true);
    return files.stream()
        .filter(file -> file.getName().equals(fileName))
        .findFirst()
        .orElseThrow(() ->
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_LOCAL_CONFIG_NOT_FOUND,
                String.format("Config file %s not found.", fileName)));
  }

  @Nullable
  private String fetchApfeConfig(
      AndroidAtsDynamicConfigPusherDecoratorSpec spec, @Nullable String suiteName)
      throws MobileHarnessException {
    if (!spec.getHasServerSideConfig()) {
      return null;
    }
    if (suiteName == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_INVALID_PARAMETER,
          "suite_name is missing from xtsSuiteInfo");
    }

    try {
      // TODO: Support to read URL from UrlReplacement.xml. More context in
      // com.android.compatibility.common.util.UrlReplacement.java
      String requestUrl =
          spec.getConfigUrl()
              .replace("{suite-name}", suiteName.toUpperCase(Locale.ROOT))
              .replace("{module}", spec.getConfigFilename())
              .replace("{version}", spec.getVersion())
              .replace("{api-key}", spec.getApiKey());
      URL request = new URI(requestUrl).toURL();
      return new String(request.openStream().readAllBytes(), UTF_8);
    } catch (IOException | URISyntaxException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_PARTNER_SERVER_ERROR,
          "Failed to access android partner remote server over internet.",
          e);
    }
  }

  private File mergeConfigFiles(
      AndroidAtsDynamicConfigPusherDecoratorSpec spec,
      File localConfigFile,
      String apfeConfigInJson)
      throws MobileHarnessException, InterruptedException {
    File hostFile = null;
    try {
      hostFile =
          DynamicConfigHandler.getMergedDynamicConfigFile(
              localConfigFile, apfeConfigInJson, spec.getConfigFilename(), new HashMap<>());
      return hostFile;
    } catch (IOException | XmlPullParserException | JsonSyntaxException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_MERGE_DYNAMIC_CONFIG_ERROR,
          "Failed to merged dynamic config file.",
          e);
    } finally {
      if (spec.getExtractFromResource()) {
        try {
          localFileUtil.removeFileOrDir(localConfigFile.toPath());
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed to remove extracted config file %s", localConfigFile.getName());
        }
      }
    }
  }
}
