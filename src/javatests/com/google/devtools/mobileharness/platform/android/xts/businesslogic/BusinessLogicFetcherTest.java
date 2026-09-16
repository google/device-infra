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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSetMultimap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BusinessLogicFetcherTest {

  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void buildUrl_noTokenWithApiKey_appendsKey() {
    String url =
        BusinessLogicFetcher.buildUrl(
            "http://fake-url/logic", "fake_api_key", Optional.empty(), ImmutableSetMultimap.of());
    assertThat(url).isEqualTo("http://fake-url/logic?key=fake_api_key");
  }

  @Test
  public void buildUrl_withTokenAndApiKey_doesNotAppendKey() {
    String url =
        BusinessLogicFetcher.buildUrl(
            "http://fake-url/logic",
            "fake_api_key",
            Optional.of("fake_token"),
            ImmutableSetMultimap.of());
    assertThat(url).isEqualTo("http://fake-url/logic");
  }

  @Test
  public void buildUrl_withParams_appendsParams() {
    String url =
        BusinessLogicFetcher.buildUrl(
            "http://fake-url/logic",
            "",
            Optional.empty(),
            ImmutableSetMultimap.of("param1", "value1", "param2", "value2"));
    assertThat(url).isEqualTo("http://fake-url/logic?param1=value1&param2=value2");
  }

  @Test
  public void buildUrl_withParamsAndApiKey_appendsBoth() {
    String url =
        BusinessLogicFetcher.buildUrl(
            "http://fake-url/logic",
            "fake_api_key",
            Optional.empty(),
            ImmutableSetMultimap.of("param1", "value1"));
    assertThat(url).isEqualTo("http://fake-url/logic?param1=value1&key=fake_api_key");
  }

  @Test
  public void getAccessToken_noKeyPath_returnsEmpty() throws Exception {
    assertThat(BusinessLogicFetcher.getAccessToken(/* apiKeyPath= */ null, SCOPE)).isEmpty();
  }

  /** An unset {@code APE_API_KEY} may arrive as "", which resolves to the working directory. */
  @Test
  public void getAccessToken_emptyKeyPath_returnsEmpty() throws Exception {
    assertThat(BusinessLogicFetcher.getAccessToken("", SCOPE)).isEmpty();
  }

  @Test
  public void getAccessToken_missingKeyFile_returnsEmpty() throws Exception {
    String missingPath = new File(temporaryFolder.getRoot(), "absent.json").getAbsolutePath();

    assertThat(BusinessLogicFetcher.getAccessToken(missingPath, SCOPE)).isEmpty();
  }

  @Test
  public void getAccessToken_malformedKeyFile_throws() throws Exception {
    File keyFile = temporaryFolder.newFile("malformed.json");
    Files.writeString(keyFile.toPath(), "not valid json");

    assertThrows(
        IOException.class,
        () -> BusinessLogicFetcher.getAccessToken(keyFile.getAbsolutePath(), SCOPE));
  }
}
