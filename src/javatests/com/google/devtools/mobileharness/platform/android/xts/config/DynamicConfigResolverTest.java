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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DynamicConfigResolverTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String DEFAULT_API_KEY =
      AndroidAtsDynamicConfigPusherDecoratorSpec.getDefaultInstance().getApiKey();

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private DynamicConfigResolver resolver;
  private String configUrlTemplate;
  private File serverConfigFile;
  private String xtsTestDir;

  @Before
  public void setUp() throws Exception {
    configUrlTemplate =
        tmpFolder.getRoot().toURI() + "{suite-name}-{module}-{version}-{api-key}.json";
    serverConfigFile =
        new File(tmpFolder.getRoot(), String.format("GTS-gts-1.0-%s.json", DEFAULT_API_KEY));
    resolver = new DynamicConfigResolver(configUrlTemplate);
    xtsTestDir = tmpFolder.getRoot().toPath().resolve("xts/android-gts/testcases").toString();
  }

  @Test
  public void extractFromTradefedJar_extractsEntryOrReturnsNull() throws Exception {
    assertThat(resolver.extractFromTradefedJar("GTS", "", "gts-tradefed")).isNull();
    assertThat(resolver.extractFromTradefedJar("GTS", xtsTestDir, "gts-tradefed")).isNull();

    File toolsDir = tmpFolder.newFolder("xts", "android-gts", "tools");
    try (ZipOutputStream jar =
        new ZipOutputStream(new FileOutputStream(new File(toolsDir, "gts-tradefed.jar")))) {
      jar.putNextEntry(new ZipEntry("gts-tradefed.dynamic"));
      jar.write("<dynamicConfig/>".getBytes(UTF_8));
      jar.closeEntry();
      jar.putNextEntry(new ZipEntry("sub/gts-tradefed.dynamic"));
      jar.write("<dynamicConfig><entry key=\"k\"/></dynamicConfig>".getBytes(UTF_8));
      jar.closeEntry();
    }

    File extracted = resolver.extractFromTradefedJar("GTS", xtsTestDir, "gts-tradefed");
    assertThat(extracted).isNotNull();
    try {
      assertThat(Files.readString(extracted.toPath(), UTF_8)).isEqualTo("<dynamicConfig/>");
    } finally {
      extracted.delete();
    }

    File subExtracted = resolver.extractFromTradefedJar("GTS", xtsTestDir, "sub/gts-tradefed");
    assertThat(subExtracted).isNotNull();
    try {
      assertThat(Files.readString(subExtracted.toPath(), UTF_8))
          .isEqualTo("<dynamicConfig><entry key=\"k\"/></dynamicConfig>");
    } finally {
      subExtracted.delete();
    }
    assertThat(resolver.extractFromTradefedJar("GTS", xtsTestDir, "missing")).isNull();
  }

  @Test
  public void fetchServerConfig_cachedPerSessionAndSharedAcrossOverloads() throws Exception {
    Files.writeString(serverConfigFile.toPath(), configJson("ro.soc.model"), UTF_8);
    assertThat(resolver.fetchServerConfig("GTS", "1.0", "session_1", TIMEOUT))
        .isEqualTo(expectedConfig("ro.soc.model"));

    Files.writeString(serverConfigFile.toPath(), configJson("ro.product.device"), UTF_8);
    assertThat(resolver.fetchServerConfig("GTS", "1.0", "session_1", TIMEOUT))
        .isEqualTo(expectedConfig("ro.soc.model"));
    assertThat(
            resolver.fetchServerConfig(
                configUrlTemplate,
                DEFAULT_API_KEY,
                "GTS",
                "gts",
                "1.0",
                "session_1",
                /* timeout= */ null))
        .isEqualTo(expectedConfig("ro.soc.model"));

    assertThat(resolver.fetchServerConfig("GTS", "1.0", "session_2", TIMEOUT))
        .isEqualTo(expectedConfig("ro.product.device"));
    assertThat(resolver.fetchServerConfig("GTS", "1.0", "", TIMEOUT))
        .isEqualTo(expectedConfig("ro.product.device"));

    Files.writeString(serverConfigFile.toPath(), configJson("ro.build.fingerprint"), UTF_8);
    assertThat(resolver.fetchServerConfig("GTS", "1.0", "", TIMEOUT))
        .isEqualTo(expectedConfig("ro.build.fingerprint"));
  }

  @Test
  public void fetchServerConfig_failuresAndMalformedJsonNotCached() throws Exception {
    MobileHarnessException networkError =
        assertThrows(
            MobileHarnessException.class,
            () -> resolver.fetchServerConfig("GTS", "1.0", "session_retry", TIMEOUT));
    assertThat(networkError.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_PARTNER_SERVER_ERROR);
    assertThat(networkError).hasCauseThat().isInstanceOf(IOException.class);

    Files.writeString(serverConfigFile.toPath(), "not json", UTF_8);
    MobileHarnessException parseError =
        assertThrows(
            MobileHarnessException.class,
            () -> resolver.fetchServerConfig("GTS", "1.0", "session_retry", TIMEOUT));
    assertThat(parseError.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_PARTNER_SERVER_ERROR);

    Files.writeString(serverConfigFile.toPath(), configJson("ro.soc.model"), UTF_8);
    assertThat(resolver.fetchServerConfig("GTS", "1.0", "session_retry", TIMEOUT))
        .isEqualTo(expectedConfig("ro.soc.model"));
  }

  private static ImmutableMap<String, ImmutableList<String>> expectedConfig(String property) {
    return ImmutableMap.of("business_logic_device_properties", ImmutableList.of(property));
  }

  private static String configJson(String property) {
    return String.format(
        "{\"dynamicConfigEntries\": {\"business_logic_device_properties\": {\"configValues\":"
            + " [\"%s\"]}}}",
        property);
  }
}
