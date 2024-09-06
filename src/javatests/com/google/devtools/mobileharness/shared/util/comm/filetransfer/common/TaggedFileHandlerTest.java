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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.common;

import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.TaggedFileMetadataProto.TaggedFileMetadata;
import com.google.wireless.qa.mobileharness.shared.comm.filetransfer.FileCallback;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link TaggedFileHandler}. */
@RunWith(JUnit4.class)
public class TaggedFileHandlerTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock public FileCallback callback;

  @Test
  public void onReceiveFile() throws Exception {
    TaggedFileHandler handler = new TaggedFileHandler(callback);

    handler.onReceived(
        TaggedFileMetadata.getDefaultInstance(),
        Path.of("received_path"),
        Path.of("original_path"));
  }
}
