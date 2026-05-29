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

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.inject.Inject;

/** Fetches Business Logic from a remote server. */
public class BusinessLogicFetcher {

  @Inject
  BusinessLogicFetcher() {}

  /**
   * Fetches business logic JSON from the given URL.
   *
   * @param urlString the URL to fetch from
   * @param timeout the connection and read timeout
   * @return the business logic JSON string
   * @throws MobileHarnessException if fetching fails
   */
  public String fetchBusinessLogic(String urlString, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create(urlString)).timeout(timeout).GET().build();
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
}
