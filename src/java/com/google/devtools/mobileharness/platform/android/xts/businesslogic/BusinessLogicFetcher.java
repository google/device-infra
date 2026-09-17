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

package com.google.devtools.mobileharness.platform.android.xts.businesslogic;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Fetches Business Logic from a remote server. */
public class BusinessLogicFetcher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  BusinessLogicFetcher() {}

  /**
   * Fetches business logic JSON from the given URL.
   *
   * @param urlString the URL to fetch from (already formatted with suite name)
   * @param scope the OAuth2 scope
   * @param apiKey the API key to use if service account is not available
   * @param apiKeyPath the path to the service account API key file
   * @param timeout the connection and read timeout
   * @param params the parameters to append to the URL
   * @return the business logic JSON string
   * @throws MobileHarnessException if fetching fails
   */
  public String fetchBusinessLogic(
      String urlString,
      String scope,
      String apiKey,
      @Nullable String apiKeyPath,
      Duration timeout,
      ImmutableSetMultimap<String, String> params)
      throws MobileHarnessException, InterruptedException {
    try {
      Optional<String> token = getAccessToken(apiKeyPath, scope);
      String finalUrl = buildUrl(urlString, apiKey, token, params);

      HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
      HttpRequest request = buildRequest(finalUrl, timeout, token);
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        return response.body();
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_FETCH_ERROR,
            String.format(
                "Failed to fetch business logic. HTTP Code: %d, Response: %s",
                response.statusCode(), response.body()));
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_FETCH_ERROR,
          "Failed to fetch business logic from " + urlString,
          e);
    }
  }

  /**
   * Returns an access token from the service account key at {@code apiKeyPath}, or {@link
   * Optional#empty()} if no key path is configured or no file exists there.
   *
   * @throws IOException if a key file exists but no token could be obtained from it, for example
   *     because the key is unreadable or malformed, or the token endpoint cannot be reached.
   */
  @VisibleForTesting
  static Optional<String> getAccessToken(@Nullable String apiKeyPath, String scope)
      throws IOException {
    if (isNullOrEmpty(apiKeyPath) || !new File(apiKeyPath).exists()) {
      logger.atInfo().log(
          "No service account key file at '%s'. Proceeding without authentication.", apiKeyPath);
      return Optional.empty();
    }
    try (FileInputStream fis = new FileInputStream(apiKeyPath)) {
      GoogleCredentials credentials = GoogleCredentials.fromStream(fis);
      if (!scope.isEmpty()) {
        credentials = credentials.createScoped(scope);
      }
      credentials.refresh();
      return Optional.of(credentials.getAccessToken().getTokenValue());
    }
  }

  @VisibleForTesting
  static String buildUrl(
      String urlString,
      String apiKey,
      Optional<String> token,
      ImmutableSetMultimap<String, String> params) {
    ImmutableSetMultimap<String, String> finalParams = params;
    if (token.isEmpty() && !apiKey.isEmpty()) {
      finalParams =
          ImmutableSetMultimap.<String, String>builder().putAll(params).put("key", apiKey).build();
    }
    return appendParamsToUrl(urlString, finalParams);
  }

  private static HttpRequest buildRequest(String url, Duration timeout, Optional<String> token) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", "BusinessLogicClient")
            .GET();
    token.ifPresent(t -> builder.header("Authorization", String.format("Bearer %s", t)));
    return builder.build();
  }

  private static String appendParamsToUrl(
      String baseUrl, ImmutableSetMultimap<String, String> params) {
    if (params.isEmpty()) {
      return baseUrl;
    }
    String queryString =
        params.entries().stream()
            .map(
                entry ->
                    URLEncoder.encode(entry.getKey(), UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), UTF_8))
            .collect(joining("&"));

    String separator = baseUrl.contains("?") ? "&" : "?";
    if (baseUrl.endsWith("?") || baseUrl.endsWith("&")) {
      separator = "";
    }
    return baseUrl + separator + queryString;
  }
}
