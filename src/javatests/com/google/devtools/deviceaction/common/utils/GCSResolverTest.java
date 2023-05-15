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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.storage.model.StorageObject;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceaction.common.utils.GCSUtil.ListResult;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import com.google.devtools.deviceaction.framework.proto.GCSFile;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
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
public final class GCSResolverTest {

  private static final String D2_PATH = "d1/d2/";
  private static final String O1_PATH = "d1/o1";
  private static final String O2_PATH = "d1/d2/o2";
  private static final String O3_PATH = "d1/d2/o3";
  private static final String PROJECT = "gcs project";
  private static final String BUCKET = "bucket";
  private static final String GCS_URI = "gs://bucket/d1/";
  private static final FileSpec GCS_FILE =
      FileSpec.newBuilder()
          .setTag("GCS tag")
          .setGcsFile(GCSFile.newBuilder().setProject(PROJECT).setGsUri(GCS_URI).build())
          .build();
  private static final FileSpec LOCAL_FILE =
      FileSpec.newBuilder().setTag("local tag").setLocalPath("local/path").build();

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock private GCSUtil mockUtil;
  @Mock private ListResult result1;
  @Mock private ListResult result2;

  private final LoadingCache<String, GCSUtil> storageCache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<String, GCSUtil>() {
                @Override
                public GCSUtil load(String project) {
                  return mockUtil;
                }
              });
  private File d1;
  private File o1;
  private File o2;
  private File o3;
  private GCSResolver resolver;

  @Before
  public void setUp() throws Exception {
    File rootDir = tmpFolder.getRoot();
    File serviceAccountKey = tmpFolder.newFile("key.json");
    d1 = tmpFolder.newFolder("d1/");
    tmpFolder.newFolder(D2_PATH);
    o1 = tmpFolder.newFile(O1_PATH);
    StorageObject storageObject1 = setUpPath(O1_PATH);
    o2 = tmpFolder.newFile(O2_PATH);
    StorageObject storageObject2 = setUpPath(O2_PATH);
    o3 = tmpFolder.newFile(O3_PATH);
    StorageObject storageObject3 = setUpPath(O3_PATH);
    when(mockUtil.isDirectory(eq(BUCKET), endsWith("/"))).thenReturn(true);
    when(mockUtil.isDirectory(eq(BUCKET), not(endsWith("/")))).thenReturn(false);
    doNothing().when(mockUtil).copyFileItemToLocal(eq(BUCKET), anyString(), any());
    when(mockUtil.listItemsAndPrefixes(
            eq(BUCKET), or(endsWith("d1/"), endsWith("d1")), eq("/"), anyBoolean(), anyLong()))
        .thenReturn(result1);
    when(mockUtil.listItemsAndPrefixes(
            eq(BUCKET), endsWith("d2/"), eq("/"), anyBoolean(), anyLong()))
        .thenReturn(result2);
    when(result1.listItems()).thenReturn(ImmutableList.of(storageObject1));
    when(result1.listPrefixes()).thenReturn(ImmutableList.of(D2_PATH));
    when(result2.listItems()).thenReturn(ImmutableList.of(storageObject2, storageObject3));
    when(result2.listPrefixes()).thenReturn(ImmutableList.of());
    resolver = new GCSResolver(new LocalFileUtil(), serviceAccountKey, rootDir, storageCache);
  }

  @Test
  public void appliesTo_expectedResult() {
    assertTrue(resolver.appliesTo(GCS_FILE));
    assertFalse(resolver.appliesTo(LOCAL_FILE));
  }

  @Test
  public void resolveFile_getExpectedResults() throws Exception {
    File result = resolver.resolveFile(GCS_FILE);

    assertThat(result).isEqualTo(d1);
    verify(mockUtil).copyFileItemToLocal(BUCKET, O1_PATH, o1.toPath());
    verify(mockUtil).copyFileItemToLocal(BUCKET, O2_PATH, o2.toPath());
    verify(mockUtil).copyFileItemToLocal(BUCKET, O3_PATH, o3.toPath());
  }

  private StorageObject setUpPath(String relativePath) {
    StorageObject storageObject1 = new StorageObject();
    storageObject1.setName(relativePath);
    return storageObject1;
  }
}
