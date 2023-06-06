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

package com.google.devtools.deviceaction.framework.operations;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ModuleCleanerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private AndroidPhone mockDevice;

  private ModuleCleaner moduleCleaner;

  @Before
  public void setUp() throws Exception {
    when(mockDevice.isUserdebug()).thenReturn(true);
    moduleCleaner = new ModuleCleaner(mockDevice);
  }

  @Test
  public void cleanUpSessions_reboots() throws Exception {
    TreeSet<String> files = new TreeSet<>();
    files.add("com.android.art@331413040.apex");
    files.add("com.android.conscrypt@331411000.apex");
    files.add("com.android.extservices@331412000.apex");
    files.add("com.android.ipsec@331310000.apex");
    files.add("com.android.media.swcodec@331511010.apex");
    files.add("com.android.media@331511010.apex");
    files.add("com.android.mediaprovider@331512030.apex");
    files.add("com.android.neuralnetworks@331310000.apex");
    when(mockDevice.listFiles("/data/apex/active/")).thenReturn(files);
    when(mockDevice.listFiles("/data/app-staging/")).thenReturn(new TreeSet<>());
    when(mockDevice.listFiles("/data/apex/sessions/")).thenReturn(new TreeSet<>());

    moduleCleaner.cleanUpSessions();

    verify(mockDevice).removeFiles("/data/apex/active/*");
    verify(mockDevice).reboot();
  }

  @Test
  public void cleanUpSessions_noReboot() throws Exception {
    when(mockDevice.listFiles("/data/apex/active/")).thenReturn(new TreeSet<>());
    when(mockDevice.listFiles("/data/app-staging/")).thenReturn(new TreeSet<>());
    when(mockDevice.listFiles("/data/apex/sessions/")).thenReturn(new TreeSet<>());

    moduleCleaner.cleanUpSessions();

    verify(mockDevice, never()).removeFiles("/data/apex/active/*");
    verify(mockDevice, never()).reboot();
  }
}
