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

package com.google.devtools.mobileharness.shared.util.system;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemInfoPrinter.Mode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class SystemInfoPrinterTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private NetworkUtil networkUtil;
  @Spy private SystemUtil systemUtil = new SystemUtil();

  private SystemInfoPrinter systemInfoPrinter;

  @Before
  public void setUp() throws Exception {
    when(networkUtil.getLocalHostName()).thenReturn("fake-host");
    systemInfoPrinter =
        new SystemInfoPrinter(
            networkUtil,
            systemUtil,
            ImmutableList.of("--public_dir=/tmp/public", "--fake_token=token_value"),
            ImmutableMap.of(
                "PATH", "/usr/bin:/bin",
                "HTTPS_PROXY", "http://user:pass@proxy:3128",
                "MY_SECRET_ENV", "secret_value"),
            ImmutableMap.of(
                "os.name", "Linux",
                "os.version", "6.1",
                "os.arch", "amd64",
                "java.class.path", "/fake/class/path"));
  }

  @Test
  public void getSystemInfo_full() {
    String systemInfo = systemInfoPrinter.getSystemInfo(DEBUG, Mode.FULL);

    assertThat(systemInfo).startsWith("System info (mode=FULL):");
    assertThat(systemInfo).contains("\n  Hostname: fake-host\n");
    assertThat(systemInfo).contains("\n  PID: " + ProcessHandle.current().pid() + "\n");
    assertThat(systemInfo).contains("\n  Process start time: ");
    assertThat(systemInfo).contains("\n  Process uptime: ");
    assertThat(systemInfo).contains("\n  OmniLab version: lab-");
    assertThat(systemInfo).contains("\n  Client version: ");
    assertThat(systemInfo)
        .contains("\n  Main args: [--public_dir=/tmp/public, --fake_token=token_value]\n");
    assertThat(systemInfo).contains("\n  Memory: ");
    assertThat(systemInfo).contains("\n    HTTPS_PROXY=http://user:pass@proxy:3128\n");
    assertThat(systemInfo).contains("\n    MY_SECRET_ENV=secret_value\n");
    assertThat(systemInfo).contains("\n    PATH=/usr/bin:/bin\n");
    assertThat(systemInfo).contains("\n    java.class.path=/fake/class/path\n");
    assertThat(systemInfo).contains("\n    os.name=Linux\n");
    // Java and OS information is only printed in system properties.
    assertThat(systemInfo).doesNotContain("\n  Java: ");
    assertThat(systemInfo).doesNotContain("\n  OS: ");
  }

  @Test
  public void getSystemInfo_redacted() {
    String systemInfo = systemInfoPrinter.getSystemInfo(DEBUG, Mode.REDACTED);

    assertThat(systemInfo).startsWith("System info (mode=REDACTED):");
    assertThat(systemInfo).contains("\n  Hostname: fake-host\n");
    assertThat(systemInfo)
        .contains("\n  Main args: [--public_dir=/tmp/public, --fake_token=<redacted>]\n");
    assertThat(systemInfo).contains("\n    HTTPS_PROXY=http://<redacted>@proxy:3128\n");
    assertThat(systemInfo).contains("\n    MY_SECRET_ENV=<redacted>\n");
    assertThat(systemInfo).contains("\n    PATH=/usr/bin:/bin\n");
    assertThat(systemInfo).contains("\n    java.class.path=<redacted>\n");
    assertThat(systemInfo).contains("\n    os.name=Linux");
    assertThat(systemInfo).doesNotContain("pass@");
    assertThat(systemInfo).doesNotContain("secret_value");
    assertThat(systemInfo).doesNotContain("token_value");
    assertThat(systemInfo).doesNotContain("/fake/class/path");
  }

  @Test
  public void getSystemInfo_fullAndRedactedHaveSameLines() {
    List<String> fullLines =
        Splitter.on('\n').splitToList(systemInfoPrinter.getSystemInfo(DEBUG, Mode.FULL));
    List<String> redactedLines =
        Splitter.on('\n').splitToList(systemInfoPrinter.getSystemInfo(DEBUG, Mode.REDACTED));

    // Skips the first line which contains the mode.
    assertThat(getLineKeys(redactedLines))
        .containsExactlyElementsIn(getLineKeys(fullLines))
        .inOrder();
  }

  @Test
  public void getSystemInfo_processStartTimeAndUptime() {
    // 2026-11-05 is in PST (UTC-8), the time zone of log line timestamps.
    doReturn(Optional.of(Instant.parse("2026-11-05T08:00:00.123Z")))
        .when(systemUtil)
        .getProcessStartTime();
    doReturn(Optional.of(Duration.ofHours(74).plusMinutes(5).plusSeconds(12).plusMillis(300)))
        .when(systemUtil)
        .getProcessUptime();

    for (Mode mode : Mode.values()) {
      String systemInfo = systemInfoPrinter.getSystemInfo(DEBUG, mode);

      assertThat(systemInfo).contains("\n  Process start time: 2026-11-05 00:00:00:123 PST\n");
      assertThat(systemInfo).contains("\n  Process uptime: 74h 5m 12s\n");
    }
  }

  @Test
  public void getSystemInfo_processStartTimeAndUptimeUnavailable() {
    doReturn(Optional.empty()).when(systemUtil).getProcessStartTime();
    doReturn(Optional.empty()).when(systemUtil).getProcessUptime();

    String systemInfo = systemInfoPrinter.getSystemInfo(DEBUG, Mode.REDACTED);

    assertThat(systemInfo).contains("\n  Process start time: n/a\n");
    assertThat(systemInfo).contains("\n  Process uptime: n/a\n");
  }

  @Test
  public void getSystemInfo_failedToGetHostname() throws Exception {
    when(networkUtil.getLocalHostName())
        .thenThrow(
            new MobileHarnessException(
                BasicErrorId.LOCAL_NETWORK_HOSTNAME_DETECTION_ERROR, "Fake error"));

    assertThat(systemInfoPrinter.getSystemInfo(DEBUG, Mode.REDACTED))
        .contains("\n  Hostname: n/a\n");
  }

  @Test
  public void getSystemInfo_resolvesHostnameOnlyOnce() throws Exception {
    for (int i = 0; i < 3; i++) {
      assertThat(systemInfoPrinter.getSystemInfo(DEBUG, Mode.FULL))
          .contains("\n  Hostname: fake-host\n");
      assertThat(systemInfoPrinter.getSystemInfo(DEBUG, Mode.REDACTED))
          .contains("\n  Hostname: fake-host\n");
    }

    verify(networkUtil, times(1)).getLocalHostName();
  }

  @Test
  public void getSystemInfo_failedToGetHostname_resolvesHostnameOnlyOnce() throws Exception {
    when(networkUtil.getLocalHostName())
        .thenThrow(
            new MobileHarnessException(
                BasicErrorId.LOCAL_NETWORK_HOSTNAME_DETECTION_ERROR, "Fake error"));

    for (int i = 0; i < 3; i++) {
      assertThat(systemInfoPrinter.getSystemInfo(DEBUG, Mode.REDACTED))
          .contains("\n  Hostname: n/a\n");
    }

    verify(networkUtil, times(1)).getLocalHostName();
  }

  @Test
  public void getSystemInfo_concurrentCalls_resolvesHostnameOnlyOnce() throws Exception {
    int threadNum = 8;
    ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
    try {
      CountDownLatch startLatch = new CountDownLatch(1);
      List<Future<String>> futures = new ArrayList<>();
      for (int i = 0; i < threadNum; i++) {
        futures.add(
            executorService.submit(
                () -> {
                  startLatch.await();
                  return systemInfoPrinter.getSystemInfo(DEBUG, Mode.REDACTED);
                }));
      }
      startLatch.countDown();

      for (Future<String> future : futures) {
        assertThat(future.get(1L, MINUTES)).contains("\n  Hostname: fake-host\n");
      }
    } finally {
      executorService.shutdownNow();
    }

    verify(networkUtil, times(1)).getLocalHostName();
  }

  @Test
  public void redactArgs() {
    assertThat(
            SystemInfoPrinter.redactArgs(
                ImmutableList.of(
                    "--public_dir=/tmp/public",
                    "--api_key=key_value",
                    "--DB_PASSWORD",
                    "password_value",
                    "--next_flag",
                    "--wifi_psk=psk_value",
                    "--client_secret_file=/path/to/secret.json",
                    "--fake_cred",
                    "--other_flag=value",
                    "--proxy=http://user:pass@proxy:3128/path",
                    "positional_arg")))
        .containsExactly(
            "--public_dir=/tmp/public",
            "--api_key=<redacted>",
            "--DB_PASSWORD",
            "<redacted>",
            "--next_flag",
            "--wifi_psk=<redacted>",
            "--client_secret_file=<redacted>",
            "--fake_cred",
            "--other_flag=value",
            "--proxy=http://<redacted>@proxy:3128/path",
            "positional_arg")
        .inOrder();
  }

  @Test
  public void redactEnvironmentVariable() {
    assertThat(SystemInfoPrinter.redactEnvironmentVariable("PATH", "/usr/bin"))
        .isEqualTo("/usr/bin");
    assertThat(SystemInfoPrinter.redactEnvironmentVariable("http_proxy", "https://u:p@proxy:1"))
        .isEqualTo("https://<redacted>@proxy:1");
    assertThat(SystemInfoPrinter.redactEnvironmentVariable("GCP_SA_KEY", "key_value"))
        .isEqualTo("<redacted>");
    assertThat(SystemInfoPrinter.redactEnvironmentVariable("path", "/usr/bin"))
        .isEqualTo("<redacted>");
  }

  @Test
  public void redactSystemProperty() {
    assertThat(SystemInfoPrinter.redactSystemProperty("os.name", "Linux")).isEqualTo("Linux");
    assertThat(SystemInfoPrinter.redactSystemProperty("java.class.path", "/a.jar"))
        .isEqualTo("<redacted>");
  }

  @Test
  public void stripUrlUserInfo() {
    assertThat(SystemInfoPrinter.stripUrlUserInfo("no url")).isEqualTo("no url");
    assertThat(SystemInfoPrinter.stripUrlUserInfo("http://host:80/path"))
        .isEqualTo("http://host:80/path");
    assertThat(SystemInfoPrinter.stripUrlUserInfo("https://host/path@foo"))
        .isEqualTo("https://host/path@foo");
    assertThat(SystemInfoPrinter.stripUrlUserInfo("a=http://u@h,b=socks5://u:p@h:1"))
        .isEqualTo("a=http://<redacted>@h,b=socks5://<redacted>@h:1");
  }

  /** Returns the field name / key part of each line, e.g., " Hostname" or " PATH". */
  private static ImmutableList<String> getLineKeys(List<String> lines) {
    return lines.stream()
        .skip(1)
        .map(line -> Splitter.onPattern("[:=]").limit(2).splitToList(line).get(0))
        .collect(toImmutableList());
  }
}
