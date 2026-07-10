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

package com.google.wireless.qa.mobileharness.shared.android;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.AdbCommandRejectedException;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.AndroidDebugBridge;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.Client;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.ClientData;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.ClientData.HeapInfo;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.IDevice;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.RawImage;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.TimeoutException;
import com.google.devtools.mobileharness.shared.android.shaded.ddmlib.internal.ClientImpl;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link DdmUtil}. */
@RunWith(JUnit4.class)
public class DdmUtilTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String SERIAL = "363005dc750400ec";
  private static final String APPLICATION_NAME = "com.google.android.apps.translate.test";

  @Mock private IDevice device;
  @Mock private ClientImpl client;
  @Mock private ClientData clientData;
  private DdmUtil ddmUtil;

  /**
   * This class overrides wrapper functions in DdmUtil. We can't mock AndroidDebugBridge because
   * it's final, so overriding wrapper functions is our next best solution.
   */
  private class DdmUtilUnderTest extends DdmUtil {
    private final IDevice[] devices;

    DdmUtilUnderTest(IDevice[] devices) {
      super(SERIAL, device, Sleeper.noOpSleeper());
      this.devices = devices;
    }

    @Override
    protected IDevice[] getDevices(AndroidDebugBridge bridge) {
      return devices;
    }

    @Override
    protected boolean hasInitialDeviceList(AndroidDebugBridge bridge) {
      return true;
    }
  }

  @Before
  public void setUp() throws Exception {
    ddmUtil = new DdmUtilUnderTest(new IDevice[] {device});

    doReturn(SERIAL).when(device).getSerialNumber();
    doReturn(client).when(device).getClient(eq(APPLICATION_NAME));
    doReturn(clientData).when(client).getClientData();
    doReturn(APPLICATION_NAME).when(clientData).getClientDescription();
    doReturn(ImmutableList.of(Integer.valueOf(1), Integer.valueOf(2)).iterator())
        .when(clientData)
        .getVmHeapIds();
    doReturn(new HeapInfo(1, 2, 3, 4, 5, (byte) 0)).when(clientData).getVmHeapInfo(eq(1));
    doReturn(new HeapInfo(6, 7, 8, 9, 10, (byte) 0)).when(clientData).getVmHeapInfo(eq(2));

    RawImage sRawImage = new RawImage();
    sRawImage.alpha_length = 8;
    sRawImage.alpha_offset = 24;
    sRawImage.blue_length = 8;
    sRawImage.blue_offset = 16;
    sRawImage.bpp = 32;
    sRawImage.green_length = 8;
    sRawImage.green_offset = 8;
    sRawImage.height = 4;
    sRawImage.red_length = 8;
    sRawImage.red_offset = 0;
    sRawImage.size = 64;
    sRawImage.version = 1;
    sRawImage.width = 4;
    sRawImage.data =
        new byte[] {
          0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
          0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
          0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28,
          0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38,
          0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
          0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
          0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
          0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
        };
    doReturn(sRawImage).when(device).getScreenshot();
  }

  @Test
  public void retrieveDevice_findsDevice_returnsDevice()
      throws MobileHarnessException, InterruptedException {
    assertThat(ddmUtil.retrieveDevice(null, SERIAL)).isEqualTo(device);
  }

  @Test
  public void retrieveDevice_noMatchingDevices_throws()
      throws MobileHarnessException, InterruptedException {
    doReturn("blah").when(device).getSerialNumber();
    // Expected, pass.
    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> ddmUtil.retrieveDevice(null, SERIAL));
    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.DDM_UTIL_DEVICE_NOT_FOUND);
  }

  @Test
  public void retrieveDevice_noDevices_throws()
      throws MobileHarnessException, InterruptedException {
    // Create a new ddmUtil which returns no devices.
    DdmUtil ddmUtilUnderTest = new DdmUtilUnderTest(new IDevice[] {});
    // Expected, pass.
    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class, () -> ddmUtilUnderTest.retrieveDevice(null, SERIAL));
    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.DDM_UTIL_DEVICE_NOT_FOUND);
  }

  @Test
  public void garbageCollect_findsApplication_collectsGarbage()
      throws MobileHarnessException, InterruptedException {
    assertThat(ddmUtil.issueGarbageCollection(APPLICATION_NAME)).isTrue();
    verify(client).executeGarbageCollector();
  }

  @Test
  public void garbageCollect_missingApplication_doesNotThrow()
      throws MobileHarnessException, InterruptedException {
    assertThat(ddmUtil.issueGarbageCollection("foo")).isFalse();
    verify(client, never()).executeGarbageCollector();
  }

  @Test
  public void kill_findsApplication_issuesKill()
      throws MobileHarnessException, InterruptedException {
    ddmUtil.kill(APPLICATION_NAME);
    verify(client).kill();
  }

  @Test
  public void kill_missingApplication_doesNothing()
      throws MobileHarnessException, InterruptedException {
    ddmUtil.kill("foo");
    verify(client, never()).kill();
  }

  @Test
  public void getLastCollectionTimestamp_doesNotFindProcess_returnsZero() {
    assertThat(ddmUtil.getLastCollectionTimestamp("foo")).isNull();
  }

  @Test
  public void getLastCollectionTimestamp_findsProcess_returnsTimeStamp()
      throws MobileHarnessException, InterruptedException {
    Instant startTime = Instant.now();

    // Enable the collection and register Client into cache first.
    ddmUtil.enableHeapCollection(APPLICATION_NAME);
    // Cause a "callback" and get the results.
    ddmUtil.clientChanged(client, Client.CHANGE_HEAP_DATA);
    Instant callbackTime = ddmUtil.getLastCollectionTimestamp(APPLICATION_NAME);

    assertThat(callbackTime).isNotNull();
    assertThat(callbackTime).isAtLeast(startTime);
  }

  @Test
  public void getCombinedHeapInfo_doesNotFindProcess_returnsNull() {
    assertThat(ddmUtil.getCombinedHeapInfo("foo")).isNull();
  }

  @Test
  public void getCombinedHeapInfo_findsProcess_returnsCombinedHeapInfo()
      throws MobileHarnessException, InterruptedException {
    // Enable the collection and register Client into cache first.
    ddmUtil.enableHeapCollection(APPLICATION_NAME);
    // Cause a "callback".
    ddmUtil.clientChanged(client, Client.CHANGE_HEAP_DATA);
    HeapInfo finalHeapInfo = ddmUtil.getCombinedHeapInfo(APPLICATION_NAME);

    // All the properties, except timeStamp, are summed. The timeStamp property is the max.
    assertThat(finalHeapInfo.maxSizeInBytes).isEqualTo(7);
    assertThat(finalHeapInfo.sizeInBytes).isEqualTo(9);
    assertThat(finalHeapInfo.bytesAllocated).isEqualTo(11);
    assertThat(finalHeapInfo.objectsAllocated).isEqualTo(13);
    assertThat(finalHeapInfo.timeStamp).isEqualTo(10);
  }

  @Test
  public void getScreenshot_adbException_throw()
      throws MobileHarnessException,
          InterruptedException,
          TimeoutException,
          AdbCommandRejectedException,
          IOException {

    doThrow(new TimeoutException()).when(device).getScreenshot();

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> ddmUtil.getScreenshot(false));

    assertThat(e).hasMessageThat().contains("Failed to capture screen");
    return;
  }

  @Test
  public void getScreenshot_success_returnImage()
      throws MobileHarnessException, InterruptedException {

    BufferedImage image = ddmUtil.getScreenshot(false);

    assertThat(image.getWidth()).isEqualTo(4);
    return;
  }

  @Test
  public void enableHeapCollection_doesNotFindProcess_returnsFalse()
      throws MobileHarnessException, InterruptedException {
    assertThat(ddmUtil.enableHeapCollection("foo")).isFalse();
  }

  @Test
  public void enableHeapCollection_findsProcess_returnsTrue()
      throws MobileHarnessException, InterruptedException {
    assertThat(ddmUtil.enableHeapCollection(APPLICATION_NAME)).isTrue();
    verify(client).setHeapUpdateEnabled(true);
  }

  @Test
  public void enableHeapCollection_alreadyEnabled_doesNothingReturnsTrue()
      throws MobileHarnessException, InterruptedException {
    doReturn(true).when(client).isHeapUpdateEnabled();
    assertThat(ddmUtil.enableHeapCollection(APPLICATION_NAME)).isTrue();
    verify(client, never()).setHeapUpdateEnabled(true);
  }

  @Test
  public void clientChanged_missingChangeHeapDataMask_doesNothing() {
    // Pass in all bits other than CHANGE_HEAP_DATA.
    ddmUtil.clientChanged(client, ~Client.CHANGE_HEAP_DATA);
    verify(client, never()).getClientData();
  }
}
