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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.devtools.mobileharness.shared.constant.closeable.MobileHarnessAutoCloseable;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.FileInfoProto.FileInfo;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher.FileTransferEvent.ExecutionType;
import java.time.Instant;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests of {@link WatchableFileTransferClient}. */
@RunWith(JUnit4.class)
public class WatchableFileTransferClientTest {

  private final WatchableFileTransferClient client =
      new WatchableFileTransferClient() {
        @Override
        public void sendFile(String fileId, String tag, String path, @Nullable String checksum) {}

        @Override
        public long downloadFile(String remote, String local) {
          return 0;
        }

        @Override
        public List<FileInfo> listFiles(String dir) {
          return null;
        }

        @Override
        public void shutdown() {}
      };

  @Test
  public void watch() {
    FileTransferWatcher watcher1 = mock(FileTransferWatcher.class);
    FileTransferWatcher watcher2 = mock(FileTransferWatcher.class);

    try (MobileHarnessAutoCloseable ignore1 = client.addWatcher(watcher1)) {
      try (MobileHarnessAutoCloseable ignore2 = client.addWatcher(watcher2)) {
        for (int i = 0; i < 5; i++) {
          FileTransferEvent e = createEvent(i);
          client.publishEvent(e);
          verify(watcher1).addEvent(e);
          verify(watcher2).addEvent(e);
        }
      }
      for (int i = 5; i < 10; i++) {
        FileTransferEvent e = createEvent(i);
        client.publishEvent(e);
        verify(watcher1).addEvent(e);
        verify(watcher2, never()).addEvent(e);
      }
    }
    for (int i = 10; i < 15; i++) {
      FileTransferEvent e = createEvent(i);
      client.publishEvent(e);
      verify(watcher1, never()).addEvent(e);
      verify(watcher2, never()).addEvent(e);
    }
  }

  private FileTransferEvent createEvent(int index) {
    return FileTransferEvent.builder()
        .setStart(Instant.now())
        .setEnd(Instant.now())
        .setFileSize(index)
        .setIsCached((index % 2) == 0)
        .setType((index % 2) == 0 ? ExecutionType.GET : ExecutionType.SEND)
        .build();
  }
}
