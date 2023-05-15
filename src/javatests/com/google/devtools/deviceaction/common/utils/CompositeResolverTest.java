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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import com.google.devtools.deviceaction.framework.proto.GCSFile;
import java.io.File;
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
public final class CompositeResolverTest {
  private static final String PROJECT = "project";
  private static final String TAG_1 = "gcs";
  private static final String TAG_2 = "gcs2";
  private static final String TAG_3 = "local";
  private static final FileSpec GCS_1 =
      FileSpec.newBuilder()
          .setTag(TAG_1)
          .setGcsFile(GCSFile.newBuilder().setProject(PROJECT).setGsUri("gs://bucket/o1").build())
          .build();
  private static final FileSpec GCS_2 =
      FileSpec.newBuilder()
          .setTag(TAG_2)
          .setGcsFile(GCSFile.newBuilder().setProject(PROJECT).setGsUri("gs://bucket/o2").build())
          .build();
  private static final File GCS_FILE_1 = new File("root/o1");
  private static final File GCS_FILE_2 = new File("root/o2");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private final LocalFileResolver localFileResolver = LocalFileResolver.getInstance();
  @Mock private GCSResolver gCSResolver;

  private File localFile1;
  private File localFile2;
  private ImmutableList<FileSpec> fileSpecs;

  @Before
  public void setUp() throws Exception {
    localFile1 = tmpFolder.newFile("path1");
    localFile2 = tmpFolder.newFile("path2");
    FileSpec local1 =
        FileSpec.newBuilder().setTag(TAG_3).setLocalPath(localFile1.getAbsolutePath()).build();
    FileSpec local2 =
        FileSpec.newBuilder().setTag(TAG_3).setLocalPath(localFile2.getAbsolutePath()).build();
    fileSpecs = ImmutableList.of(GCS_1, GCS_2, local1, local2);
    when(gCSResolver.resolve(anyList()))
        .thenReturn(ImmutableMultimap.of(TAG_1, GCS_FILE_1, TAG_2, GCS_FILE_2));
    when(gCSResolver.appliesTo(GCS_1)).thenReturn(true);
    when(gCSResolver.appliesTo(GCS_2)).thenReturn(true);
    when(gCSResolver.appliesTo(local1)).thenReturn(false);
    when(gCSResolver.appliesTo(local2)).thenReturn(false);
  }

  @Test
  public void resolve_bothResolvers_resolvesAll() throws Exception {
    CompositeResolver resolver =
        CompositeResolver.toBuilder()
            .addResolver(gCSResolver)
            .addResolver(localFileResolver)
            .build();

    ImmutableMultimap<String, File> result = resolver.resolve(fileSpecs);

    assertThat(result).hasSize(4);
    assertThat(result).valuesForKey(TAG_1).containsExactly(GCS_FILE_1);
    assertThat(result).valuesForKey(TAG_2).containsExactly(GCS_FILE_2);
    assertThat(result).valuesForKey(TAG_3).containsExactly(localFile1, localFile2);
  }

  @Test
  public void resolve_oneLocalResolver_resolvesOnlyLocal() throws Exception {
    CompositeResolver resolver =
        CompositeResolver.toBuilder().addResolver(localFileResolver).build();

    ImmutableMultimap<String, File> result = resolver.resolve(fileSpecs);

    assertThat(result).hasSize(2);
    assertThat(result).valuesForKey(TAG_3).containsExactly(localFile1, localFile2);
  }

  @Test
  public void build_noResolver_throwException() {
    assertThrows(DeviceActionException.class, () -> CompositeResolver.toBuilder().build());
  }
}
