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

package com.google.devtools.mobileharness.platform.android.app;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ActivityManagerTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  private ActivityManager am;
  private static final String ADB_SHELL_GET_CONFIG = "am get-config";
  private static final String SERIAL = "FA77N0302705";

  @Before
  public void setUp() {
    am = new ActivityManager(adb);
  }

  @Test
  public void parsingLocale_ok() throws MobileHarnessException {
    String text1 =
        "config:"
            + " en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-420dpi-finger-keysexposed-nokeys-navhidden-nonav-1794x1080-v28";
    assertThat(ActivityManager.parseLocale(text1).locale()).isEqualTo("en-US");

    String text2 =
        "config:"
            + " mcc321-en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-420dpi-finger-keysexposed-nokeys-navhidden-nonav-1794x1080-v28";
    assertThat(ActivityManager.parseLocale(text2).locale()).isEqualTo("en-US");

    String text3 =
        "config:"
            + " mcc321-mnc333-en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-420dpi-finger-keysexposed-nokeys-navhidden-nonav-1794x1080-v28";
    assertThat(ActivityManager.parseLocale(text3).locale()).isEqualTo("en-US");
  }

  @Test
  public void parsingLocale_exception() throws MobileHarnessException {
    String emptyText = "";
    Exception thrown0 =
        assertThrows(MobileHarnessException.class, () -> ActivityManager.parseLocale(emptyText));
    assertThat(thrown0)
        .hasCauseThat()
        .hasMessageThat()
        .contains("contains no valid config information");

    String illegalText1 = "config: no-";
    Exception thrown1 =
        assertThrows(MobileHarnessException.class, () -> ActivityManager.parseLocale(illegalText1));
    assertThat(thrown1)
        .hasCauseThat()
        .hasMessageThat()
        .contains("contains no valid config information");

    String illegalText2 = "This is not legal";
    Exception thrown2 =
        assertThrows(MobileHarnessException.class, () -> ActivityManager.parseLocale(illegalText2));
    assertThat(thrown2)
        .hasCauseThat()
        .hasMessageThat()
        .contains("contains no valid config information");
  }

  @Test
  public void getLocale_ok()
      throws MobileHarnessException,
          InterruptedException,
          com.google.wireless.qa.mobileharness.shared.MobileHarnessException {
    String expectOutput =
        "config:"
            + " en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-420dpi-finger-keysexposed-nokeys-navhidden-nonav-1794x1080-v28";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(expectOutput);
    assertThat(am.getLocale(SERIAL).locale()).isEqualTo("en-US");
  }

  @Test
  public void getLocale_emptyException()
      throws MobileHarnessException,
          InterruptedException,
          com.google.wireless.qa.mobileharness.shared.MobileHarnessException {
    String emptyOutput = "";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(emptyOutput);
    Exception thrown = assertThrows(MobileHarnessException.class, () -> am.getLocale(SERIAL));
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void getLocale_missingException()
      throws MobileHarnessException,
          InterruptedException,
          com.google.wireless.qa.mobileharness.shared.MobileHarnessException {
    String missingOutput = "config: ";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(missingOutput);
    Exception thrown = assertThrows(MobileHarnessException.class, () -> am.getLocale(SERIAL));
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void getLocale_invalidException()
      throws MobileHarnessException,
          InterruptedException,
          com.google.wireless.qa.mobileharness.shared.MobileHarnessException {
    String invalidOutput = "config: ss";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(invalidOutput);
    Exception thrown = assertThrows(MobileHarnessException.class, () -> am.getLocale(SERIAL));
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void getLocale_notFoundException()
      throws MobileHarnessException,
          InterruptedException,
          com.google.wireless.qa.mobileharness.shared.MobileHarnessException {
    String notFoundOutput = "not found";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(notFoundOutput);
    Exception thrown = assertThrows(MobileHarnessException.class, () -> am.getLocale(SERIAL));
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }
}
