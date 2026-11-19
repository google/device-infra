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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.gson.JsonParseException;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Extracts dynamic config files from the suite tradefed JAR and fetches dynamic config entries from
 * the Android partner server.
 */
public class DynamicConfigResolver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DEFAULT_CONFIG_URL =
      AndroidAtsDynamicConfigPusherDecoratorSpec.getDefaultInstance().getConfigUrl();
  private static final String DEFAULT_API_KEY =
      AndroidAtsDynamicConfigPusherDecoratorSpec.getDefaultInstance().getApiKey();
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration MAX_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

  /** Static because a new resolver is created for every decorator instance. */
  private static final Cache<String, ImmutableMap<String, ImmutableList<String>>>
      serverConfigCache =
          CacheBuilder.newBuilder().maximumSize(100).expireAfterAccess(Duration.ofHours(1)).build();

  private final String configUrl;

  @Inject
  DynamicConfigResolver() {
    this(DEFAULT_CONFIG_URL);
  }

  @VisibleForTesting
  DynamicConfigResolver(String configUrl) {
    this.configUrl = configUrl;
  }

  /**
   * Extracts {@code <lookupName>.dynamic} from the suite tradefed JAR to a temp file, or returns
   * null if it is not found.
   */
  @Nullable
  public File extractFromTradefedJar(String suiteName, String xtsTestDir, String lookupName) {
    Path parentDir = Path.of(xtsTestDir).getParent();
    Path xtsRootDir = parentDir == null ? null : parentDir.getParent();
    if (xtsRootDir == null) {
      return null;
    }
    File toolsDir = XtsDirUtil.getXtsToolsDir(xtsRootDir, suiteName).toFile();
    File tradefedJar = new File(toolsDir, Ascii.toLowerCase(suiteName) + "-tradefed.jar");
    if (!tradefedJar.exists()) {
      return null;
    }
    try (ZipFile zipFile = new ZipFile(tradefedJar)) {
      ZipEntry entry = zipFile.getEntry(lookupName + ".dynamic");
      if (entry == null) {
        return null;
      }
      Path fileName = Path.of(lookupName).getFileName();
      String tempPrefix =
          fileName == null || fileName.toString().equals(".") || fileName.toString().equals("..")
              ? "dynamic-config-"
              : fileName.toString();
      File localConfigFile = Files.createTempFile(tempPrefix, ".dynamic").toFile();
      try (InputStream inputStream = zipFile.getInputStream(entry)) {
        Files.copy(inputStream, localConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return localConfigFile;
      } catch (IOException e) {
        if (!localConfigFile.delete()) {
          logger.atWarning().log("Failed to delete temp dynamic config file %s", localConfigFile);
        }
        throw e;
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to read from %s", tradefedJar.getAbsolutePath());
      return null;
    }
  }

  /**
   * Returns the suite-level dynamic config entries served by the Android partner server using the
   * default server URL and API key.
   *
   * <p>The parsed config is cached per session (until unused for 1 hour) when {@code sessionId} is
   * non-empty. Failures and malformed JSON are not cached.
   */
  public ImmutableMap<String, ImmutableList<String>> fetchServerConfig(
      String suiteName, String version, @Nullable String sessionId, @Nullable Duration timeout)
      throws MobileHarnessException {
    return fetchServerConfig(
        configUrl,
        DEFAULT_API_KEY,
        suiteName,
        Ascii.toLowerCase(suiteName),
        version,
        sessionId,
        timeout);
  }

  /**
   * Returns the dynamic config entries served by the Android partner server.
   *
   * <p>The parsed config is cached per session (until unused for 1 hour) when {@code sessionId} is
   * non-empty. Failures and malformed JSON are not cached. If {@code timeout} is {@code null}, a
   * 60-second default timeout is used.
   */
  public ImmutableMap<String, ImmutableList<String>> fetchServerConfig(
      String configUrl,
      String apiKey,
      String suiteName,
      String configName,
      String version,
      @Nullable String sessionId,
      @Nullable Duration timeout)
      throws MobileHarnessException {
    String requestUrl = getRequestUrl(configUrl, apiKey, suiteName, configName, version);
    if (Strings.isNullOrEmpty(sessionId)) {
      return fetchAndParseServerConfig(requestUrl, timeout);
    }
    try {
      return serverConfigCache.get(
          sessionId + " " + requestUrl, () -> fetchAndParseServerConfig(requestUrl, timeout));
    } catch (ExecutionException e) {
      Throwables.throwIfInstanceOf(e.getCause(), MobileHarnessException.class);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_PARTNER_SERVER_ERROR,
          "Failed to get dynamic config from android partner remote server.",
          e);
    }
  }

  private static ImmutableMap<String, ImmutableList<String>> fetchAndParseServerConfig(
      String requestUrl, @Nullable Duration timeout) throws MobileHarnessException {
    String json = readUrl(requestUrl, timeout);
    try {
      return DynamicConfigHandler.parseJsonToConfigMap(json).entrySet().stream()
          .collect(toImmutableMap(Map.Entry::getKey, e -> ImmutableList.copyOf(e.getValue())));
    } catch (JsonParseException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_PARTNER_SERVER_ERROR,
          "Invalid dynamic config from android partner remote server.",
          e);
    }
  }

  private static String readUrl(String requestUrl, @Nullable Duration timeout)
      throws MobileHarnessException {
    Duration effectiveTimeout = timeout == null ? DEFAULT_REQUEST_TIMEOUT : timeout;
    int timeoutMs =
        effectiveTimeout.compareTo(MAX_TIMEOUT) >= 0
            ? Integer.MAX_VALUE
            : Math.max(1, (int) effectiveTimeout.toMillis());
    try {
      URLConnection connection = new URI(requestUrl).toURL().openConnection();
      connection.setConnectTimeout(timeoutMs);
      connection.setReadTimeout(timeoutMs);
      try (InputStream inputStream = connection.getInputStream()) {
        return new String(inputStream.readAllBytes(), UTF_8);
      }
    } catch (IOException | URISyntaxException | IllegalArgumentException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_PARTNER_SERVER_ERROR,
          "Failed to access android partner remote server over internet.",
          e);
    }
  }

  private static String getRequestUrl(
      String configUrl, String apiKey, String suiteName, String configName, String version) {
    // TODO: Support to read URL from UrlReplacement.xml. More context in
    // com.android.compatibility.common.util.UrlReplacement.java
    return configUrl
        .replace("{suite-name}", suiteName.toUpperCase(Locale.ROOT))
        .replace("{module}", configName)
        .replace("{version}", version)
        .replace("{api-key}", apiKey);
  }
}
