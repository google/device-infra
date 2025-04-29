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

package com.google.wireless.qa.mobileharness.shared.sponge;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** The object to record test suite in sponge. */
public class SpongeTestSuite extends SpongeNode {
  /** The child test suite/test case list. */
  private final ConcurrentLinkedQueue<SpongeNode> childNodes = new ConcurrentLinkedQueue<>();

  private volatile boolean isLock = false;

  public SpongeTestSuite(String name) {
    super(name);
  }

  /** Adds a new child test suite/test case. */
  public boolean addChildNode(SpongeNode childNode) {
    childNodes.add(childNode);
    childNode.parent = this;
    return true;
  }

  /**
   * Returns the child {@link SpongeNode} of the test suites. They can be {@link SpongeTestCase} or
   * {@link SpongeTestSuite}.
   */
  public List<SpongeNode> getChildNodes() {
    return ImmutableList.copyOf(childNodes);
  }

  /** Clears all child nodes in the queue of the test suite node. */
  public void clearChildNodes() {
    childNodes.clear();
  }

  /** Returns true if the test suite can remove the child sponge node. */
  boolean removeChildNode(SpongeNode childNode) {
    return childNodes.remove(childNode);
  }

  /** Returns true if the test suite is empty. */
  public boolean isEmpty() {
    return childNodes.isEmpty()
        && getProperties().isEmpty()
        && Strings.isNullOrEmpty(getGenFilesDir());
  }

  /**
   * Marks the test suite is locked. It is just a signal and does not force the test suite can not
   * be changed.
   */
  public synchronized void lock() {
    isLock = true;
  }

  /** Returns true if the test suite is locked. */
  public synchronized boolean isLocked() {
    return isLock;
  }
}
