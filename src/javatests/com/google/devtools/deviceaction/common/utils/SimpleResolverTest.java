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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SimpleResolverTest {
  private static final String TAG_1 = "tag1";
  private static final String TAG_2 = "tag2";

  private static final FileSpec SPEC_1 =
      FileSpec.newBuilder().setTag(TAG_1).setLocalPath("not/used1").build();

  private static final FileSpec SPEC_2 =
      FileSpec.newBuilder().setTag(TAG_2).setLocalPath("not/exist").build();

  private static final FileSpec SPEC_3 =
      FileSpec.newBuilder().setTag(TAG_2).setLocalPath("not/used2").build();

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private File localFile1;

  private final File localFile2 = new File("not/exist");

  private File localFile3;

  private SimpleResolver resolver;

  @Before
  public void setUp() throws Exception {
    localFile1 = tmpFolder.newFile("file1");
    localFile3 = tmpFolder.newFile("file3");
    resolver = spy(SimpleResolver.class);
    when(resolver.resolveFile(SPEC_1)).thenReturn(localFile1);
    when(resolver.resolveFile(SPEC_2)).thenReturn(localFile2);
    when(resolver.resolveFile(SPEC_3)).thenReturn(localFile3);
  }

  @Test
  public void resolve_returnExsitingFiles() throws Exception {
    ImmutableMultimap<String, File> result =
        resolver.resolve(ImmutableList.of(SPEC_1, SPEC_2, SPEC_3));

    assertThat(result.keySet()).containsExactly(TAG_1, TAG_2);
    assertThat(result).valuesForKey(TAG_1).containsExactly(localFile1);
    assertThat(result).valuesForKey(TAG_2).containsExactly(localFile3);
  }
}
