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

import com.google.common.collect.Sets;
import com.google.devtools.mobileharness.shared.constant.closeable.MobileHarnessAutoCloseable;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.comm.filetransfer.FileTransferClient;
import java.util.Set;

/** A {@link FileTransferClient} which is watchable. */
public abstract class WatchableFileTransferClient implements FileTransferClient {

  /** List of added watchers. */
  private final Set<FileTransferWatcher> watchers = Sets.newConcurrentHashSet();

  /** Remover of a watcher. It removes the wrapped {@link #watcher} when it is closed. */
  private class WatcherRemover extends MobileHarnessAutoCloseable {

    /** Wrapped watcher. */
    private final FileTransferWatcher watcher;

    private WatcherRemover(FileTransferWatcher watcher) {
      this.watcher = watcher;
    }

    @Override
    public void close() {
      watchers.remove(watcher);
    }
  }

  /** {@inheritDoc} */
  @Override
  public MobileHarnessAutoCloseable addWatcher(FileTransferWatcher watcher) {
    watchers.add(watcher);
    return new WatcherRemover(watcher);
  }

  @Override
  public void sendFile(String fileId, String tag, String path)
      throws MobileHarnessException, InterruptedException {
    sendFile(fileId, tag, path, null);
  }

  /** Notifies all observers with {@code event}. */
  protected void publishEvent(FileTransferEvent event) {
    for (FileTransferWatcher watcher : watchers) {
      watcher.addEvent(event);
    }
  }
}
