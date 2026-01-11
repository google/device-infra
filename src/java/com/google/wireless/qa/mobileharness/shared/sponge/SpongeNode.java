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

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;

/** A base class to record testsuite/testcase in sponge. */
public class SpongeNode {
  /** A {{@code key}, {@code value}} pair of testcase/testsuite showing in sponge. */
  @AutoValue
  public abstract static class Property {
    public static Property create(String key, String value) {
      return new AutoValue_SpongeNode_Property(key, value);
    }

    public abstract String key();

    public abstract String value();
  }

  /** The name of the testcase/testsuite. */
  private volatile String name;

  /**
   * The parent testsuite of the testcase/testsuite. If it's null, means the node is a root node. Or
   * means the node is a leaf node.
   */
  protected volatile SpongeTestSuite parent;

  /** The run time of the testcase/testsuite. By default, it is 0. */
  private volatile Duration runTime = Duration.ZERO;

  /** The time stamp of the testcase/testsuite. */
  private volatile Optional<Instant> startTimeStamp = Optional.empty();

  /** The generated files directory of the testcase/testsuite. */
  private volatile String genFilesDir;

  /** The remote generated files directory of the testcase/testsuite. */
  private volatile Optional<String> remoteGenFilesDir = Optional.empty();

  /** List of the attached file relative paths under {@link #genFilesDir}. */
  private volatile Optional<ImmutableList<String>> attachedFileRelativePaths = Optional.empty();

  /** The properties of the testcase/testsuite. */
  private final ConcurrentLinkedQueue<Property> properties = new ConcurrentLinkedQueue<>();

  protected SpongeNode(String name) {
    this.name = name;
  }

  /** Returns the parent testsuite of the testcase/testsuite. */
  @Nullable
  public SpongeTestSuite getParent() {
    return parent;
  }

  /** Returns the name of the testcase/testsuite. */
  public String getName() {
    return name;
  }

  /** Returns the run time of the testcase/testsuite. */
  public Duration getRunTime() {
    return runTime;
  }

  /**
   * Sets the run time of the testcase/testsuite.
   *
   * @param runTime the run time of the testcase/testsuite
   */
  @CanIgnoreReturnValue
  public SpongeNode setRunTime(Duration runTime) {
    this.runTime = runTime;
    return this;
  }

  /** Returns the run time of the testcase/testsuite. */
  public Optional<Instant> getStartTimeStamp() {
    return startTimeStamp;
  }

  /** Sets the run time of the testcase/testsuite. */
  @CanIgnoreReturnValue
  public SpongeNode setStartTimeStamp(Instant startTimeStamp) {
    this.startTimeStamp = Optional.of(startTimeStamp);
    return this;
  }

  /** Returns the generated files directory of the testcase/testsuite. */
  @Nullable
  public String getGenFilesDir() {
    return genFilesDir;
  }

  /** Sets the generated files directory of the testcase/testsuite. */
  @CanIgnoreReturnValue
  public SpongeNode setGenFilesDir(String genFilesDir) {
    this.genFilesDir = genFilesDir;
    return this;
  }

  /** Returns the remote generated files directory of the testcase/testsuite. */
  public Optional<String> getRemoteGenFilesDir() {
    return remoteGenFilesDir;
  }

  /** Sets the remote generated files directory of the testcase/testsuite. */
  @CanIgnoreReturnValue
  public SpongeNode setRemoteGenFilesDir(Optional<String> remoteGenFilesDir) {
    this.remoteGenFilesDir = remoteGenFilesDir;
    return this;
  }

  /** Returns the properties list of the testcase/testsuite. */
  public ImmutableList<Property> getProperties() {
    return ImmutableList.copyOf(properties);
  }

  /** Adds the property to the testcase/testsuite. */
  @CanIgnoreReturnValue
  public SpongeNode addProperty(Property property) {
    properties.add(property);
    return this;
  }

  /** Removes the property to the testcase/testsuite. */
  @CanIgnoreReturnValue
  public SpongeNode removeProperty(Property property) {
    properties.remove(property);
    return this;
  }

  /**
   * Sets the specific attached file relative paths under {@link #genFilesDir}, if the specific
   * files are not set, the entire files under {@link #genFilesDir} will be attached.
   */
  @CanIgnoreReturnValue
  public SpongeNode setAttachedFileRelativePaths(List<String> attachedFileRelativePaths) {
    this.attachedFileRelativePaths = Optional.of(ImmutableList.copyOf(attachedFileRelativePaths));
    return this;
  }

  /** Gets the specific attached file relative paths under {@link #genFilesDir}. */
  public Optional<ImmutableList<String>> getAttachedFileRelativePaths() {
    return attachedFileRelativePaths;
  }

  /** Gets the root node genfiles dir recursively. */
  @Nullable
  public String getRootGenFilesDir() {
    if (parent != null) {
      String rootGenFilesDir = parent.getRootGenFilesDir();
      if (Strings.isNullOrEmpty(rootGenFilesDir)) {
        return genFilesDir;
      } else {
        return rootGenFilesDir;
      }
    } else {
      return genFilesDir;
    }
  }

  @CanIgnoreReturnValue
  SpongeNode addAllProperties(List<Property> properties) {
    this.properties.addAll(properties);
    return this;
  }
}
