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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.JavaVersion;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class XtsCommandUtilTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private SystemUtil mockSystemUtil;
  @Mock private LocalFileUtil mockLocalFileUtil;

  private static final String CTS_TYPE = "cts";
  private XtsCommandUtil xtsCommandUtil;

  @Before
  public void setUp() throws Exception {
    xtsCommandUtil = new XtsCommandUtil(mockSystemUtil, mockLocalFileUtil);

    when(mockSystemUtil.isOnLinux()).thenReturn(true);
    when(mockSystemUtil.isX8664()).thenReturn(true);
    when(mockLocalFileUtil.isFileExist(any(Path.class))).thenReturn(true);
  }

  @Test
  public void useXtsJavaBinary_xtsJavaVersionHigher_returnsTrue() throws Exception {
    Path xtsRoot = tmpFolder.getRoot().toPath();
    Path xtsJava = XtsDirUtil.getXtsJavaBinary(xtsRoot, CTS_TYPE);
    Path systemJava = Path.of("/system/java");
    when(mockSystemUtil.getJavaBin()).thenReturn(systemJava.toString());
    when(mockSystemUtil.getJavaVersion(xtsJava)).thenReturn(new JavaVersion(11, "11.0.2"));
    when(mockSystemUtil.getJavaVersion(systemJava)).thenReturn(new JavaVersion(8, "1.8.0_1"));

    assertThat(xtsCommandUtil.useXtsJavaBinary(CTS_TYPE, tmpFolder.getRoot().toPath())).isTrue();
    verify(mockSystemUtil).getJavaVersion(xtsJava);
    verify(mockSystemUtil).getJavaVersion(systemJava);
  }

  @Test
  public void useXtsJavaBinary_xtsJavaVersionLower_returnsFalse() throws Exception {
    Path xtsRoot = tmpFolder.getRoot().toPath();
    Path xtsJava = XtsDirUtil.getXtsJavaBinary(xtsRoot, CTS_TYPE);
    Path systemJava = Path.of("/system/java");
    when(mockSystemUtil.getJavaBin()).thenReturn(systemJava.toString());
    when(mockSystemUtil.getJavaVersion(xtsJava)).thenReturn(new JavaVersion(8, "1.8.0_1"));
    when(mockSystemUtil.getJavaVersion(systemJava)).thenReturn(new JavaVersion(11, "11.0.2"));

    assertThat(xtsCommandUtil.useXtsJavaBinary(CTS_TYPE, tmpFolder.getRoot().toPath())).isFalse();
    verify(mockSystemUtil).getJavaVersion(xtsJava);
    verify(mockSystemUtil).getJavaVersion(systemJava);
  }

  @Test
  public void useXtsJavaBinary_versionsEqual_returnsTrue() throws Exception {
    Path xtsRoot = tmpFolder.getRoot().toPath();
    Path xtsJava = XtsDirUtil.getXtsJavaBinary(xtsRoot, CTS_TYPE);
    Path systemJava = Path.of("/system/java");
    when(mockSystemUtil.getJavaBin()).thenReturn(systemJava.toString());
    when(mockSystemUtil.getJavaVersion(xtsJava)).thenReturn(new JavaVersion(11, "11.0.2"));
    when(mockSystemUtil.getJavaVersion(systemJava)).thenReturn(new JavaVersion(11, "11.0.2"));

    assertThat(xtsCommandUtil.useXtsJavaBinary(CTS_TYPE, tmpFolder.getRoot().toPath())).isTrue();
    verify(mockSystemUtil).getJavaVersion(xtsJava);
    verify(mockSystemUtil).getJavaVersion(systemJava);
  }

  @Test
  public void useXtsJavaBinary_versionCheckFails_returnsTrue() throws Exception {
    Path xtsRoot = tmpFolder.getRoot().toPath();
    Path xtsJava = XtsDirUtil.getXtsJavaBinary(xtsRoot, CTS_TYPE);
    Path systemJava = Path.of("/system/java");
    when(mockSystemUtil.getJavaBin()).thenReturn(systemJava.toString());
    when(mockSystemUtil.getJavaVersion(xtsJava))
        .thenThrow(
            new MobileHarnessException(
                BasicErrorId.SYSTEM_GET_JAVA_VERSION_ERROR, "Failed to get java version"));
    when(mockSystemUtil.getJavaVersion(systemJava)).thenReturn(new JavaVersion(11, "11.0.2"));

    assertThat(xtsCommandUtil.useXtsJavaBinary(CTS_TYPE, tmpFolder.getRoot().toPath())).isTrue();
    verify(mockSystemUtil).getJavaVersion(xtsJava);
  }

  @Test
  public void useXtsJavaBinary_xtsJavaFileNotExist_returnsFalse() throws Exception {
    when(mockLocalFileUtil.isFileExist(any(Path.class))).thenReturn(false);
    Path xtsRoot = tmpFolder.getRoot().toPath();

    assertThat(xtsCommandUtil.useXtsJavaBinary(CTS_TYPE, xtsRoot)).isFalse();
    verify(mockLocalFileUtil).isFileExist(XtsDirUtil.getXtsJavaBinary(xtsRoot, CTS_TYPE));
  }
}
