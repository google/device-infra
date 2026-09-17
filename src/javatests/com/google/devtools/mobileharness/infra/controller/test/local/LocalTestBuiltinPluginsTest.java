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

package com.google.devtools.mobileharness.infra.controller.test.local;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LocalTestBuiltinPluginsTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private JobInfo jobInfo;
  @Mock private Properties properties;

  @Before
  public void setUp() {
    when(jobInfo.properties()).thenReturn(properties);
  }

  @Test
  public void shouldAddMctsDynamicDownloadPlugin_dynamicDownloadDisabled_returnsFalse() {
    when(properties.getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED))
        .thenReturn(Optional.of(false));

    assertThat(LocalTestBuiltinPlugins.shouldAddMctsDynamicDownloadPlugin(jobInfo)).isFalse();
  }

  @Test
  public void shouldAddMctsDynamicDownloadPlugin_setupJob_returnsTrue() {
    when(properties.getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED))
        .thenReturn(Optional.of(true));
    when(properties.get(XtsConstants.XTS_JOB_NAME)).thenReturn(XtsConstants.SETUP_JOB_NAME);

    assertThat(LocalTestBuiltinPlugins.shouldAddMctsDynamicDownloadPlugin(jobInfo)).isTrue();
  }

  @Test
  public void shouldAddMctsDynamicDownloadPlugin_teardownJob_returnsTrue() {
    when(properties.getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED))
        .thenReturn(Optional.of(true));
    when(properties.get(XtsConstants.XTS_JOB_NAME)).thenReturn(XtsConstants.TEARDOWN_JOB_NAME);

    assertThat(LocalTestBuiltinPlugins.shouldAddMctsDynamicDownloadPlugin(jobInfo)).isTrue();
  }

  @Test
  public void shouldAddMctsDynamicDownloadPlugin_dynamicMctsJob_returnsTrue() {
    when(properties.getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED))
        .thenReturn(Optional.of(true));
    when(properties.get(XtsConstants.XTS_JOB_NAME)).thenReturn(XtsConstants.DYNAMIC_MCTS_JOB_NAME);

    assertThat(LocalTestBuiltinPlugins.shouldAddMctsDynamicDownloadPlugin(jobInfo)).isTrue();
  }

  @Test
  public void shouldAddMctsDynamicDownloadPlugin_staticXtsJob_returnsFalse() {
    when(properties.getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED))
        .thenReturn(Optional.of(true));
    when(properties.get(XtsConstants.XTS_JOB_NAME)).thenReturn(XtsConstants.STATIC_XTS_JOB_NAME);

    assertThat(LocalTestBuiltinPlugins.shouldAddMctsDynamicDownloadPlugin(jobInfo)).isFalse();
  }
}
