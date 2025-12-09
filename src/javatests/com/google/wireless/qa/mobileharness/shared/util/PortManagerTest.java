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

package com.google.wireless.qa.mobileharness.shared.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.testing.serverutil.PortPicker;
import com.google.testing.serverutil.PortPicker.UnableToPickPortException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link PortManager}. */
@RunWith(JUnit4.class)
public class PortManagerTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private PortPicker portPicker;
  @Mock private SystemUtil systemUtil;

  private PortManager portManager;

  @Before
  public void setUp() throws Exception {
    portManager = new PortManager(portPicker, systemUtil);
    when(systemUtil.getProcessesByPort(anyString())).thenReturn(ImmutableSet.of());
  }

  @Test
  public void pickContiguousUnusedPortsInRange_success() throws Exception {
    when(portPicker.pickUnusedPort(10000, 3)).thenReturn(10000);

    List<Integer> ports = portManager.pickContiguousUnusedPortsInRange(3, 10000, 5);
    assertThat(ports).containsExactly(10000, 10001, 10002).inOrder();
  }

  @Test
  public void pickContiguousUnusedPortsInRange_retrySuccessWhenFirstPortBusy() throws Exception {
    when(portPicker.pickUnusedPort(10000, 3)).thenReturn(10000).thenReturn(10001);
    when(systemUtil.getProcessesByPort("10000,10001,10002")).thenReturn(ImmutableSet.of(123));

    List<Integer> ports = portManager.pickContiguousUnusedPortsInRange(3, 10000, 5);

    assertThat(ports).containsExactly(10001, 10002, 10003).inOrder();
  }

  @Test
  public void pickContiguousUnusedPortsInRange_portBusy_throwsException() throws Exception {
    when(portPicker.pickUnusedPort(10000, 3)).thenReturn(10000);
    when(systemUtil.getProcessesByPort("10000,10001,10002")).thenReturn(ImmutableSet.of(123));

    assertThrows(
        UnableToPickPortException.class,
        () -> portManager.pickContiguousUnusedPortsInRange(3, 10000, 5));
  }

  @Test
  public void pickContiguousUnusedPortsInRange_nPortsTooLarge_throwsException() throws Exception {
    assertThrows(
        UnableToPickPortException.class,
        () -> portManager.pickContiguousUnusedPortsInRange(6, 10000, 5));
  }

  @Test
  public void pickContiguousUnusedPorts_success() throws Exception {
    when(portPicker.pickUnusedPort(anyInt(), anyInt())).thenReturn(20000);

    List<Integer> ports = portManager.pickContiguousUnusedPorts(2);
    assertThat(ports).containsExactly(20000, 20001).inOrder();
  }
}
