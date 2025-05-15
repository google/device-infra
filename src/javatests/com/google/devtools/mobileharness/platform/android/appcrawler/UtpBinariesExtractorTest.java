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

package com.google.devtools.mobileharness.platform.android.appcrawler;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class UtpBinariesExtractorTest {
  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock ResUtil resourcesUtil;

  @Test
  public void setupUtpBinaries_extractsFromResources() throws Exception {
    when(resourcesUtil.getResourceFile(any(), any())).thenReturn("resource_file");

    var unused = new UtpBinariesExtractor(resourcesUtil).setUpUtpBinaries();

    verify(resourcesUtil, times(6)).getResourceFile(any(), any());
  }

  @Test
  public void setupUtpBinaries_extractsFromExternalResources() throws Exception {
    when(resourcesUtil.getResourceFile(any(), any())).thenReturn("");
    when(resourcesUtil.getExternalResourceFile(any())).thenReturn(Optional.of("resource_file"));

    var unused = new UtpBinariesExtractor(resourcesUtil).setUpUtpBinaries();

    verify(resourcesUtil, times(6)).getExternalResourceFile(any());
  }

  @Test
  public void setupUtpBinaries_throwsExceptionWhenResourcesAbsent() throws Exception {
    when(resourcesUtil.getResourceFile(any(), any())).thenReturn("");
    when(resourcesUtil.getExternalResourceFile(any())).thenReturn(Optional.empty());

    var exception =
        assertThrows(
            MobileHarnessException.class,
            () -> new UtpBinariesExtractor(resourcesUtil).setUpUtpBinaries());

    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_ROBO_TEST_MH_ROBO_DEPS_EXTRACTION_ERROR);
  }
}
