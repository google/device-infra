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

package com.google.devtools.mobileharness.infra.controller.device;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Filter to manage device runner creation and execution based on allowlist and blocklist.
 *
 * <p>A device is allowed only if it is allowed by the allowlist AND NOT blocked by the blocklist.
 */
public class DeviceManagementFilter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Supplier<DeviceManagementFilter> INSTANCE =
      Suppliers.memoize(DeviceManagementFilter::new);

  public static DeviceManagementFilter getInstance() {
    return INSTANCE.get();
  }

  private final Object lock = new Object();

  /**
   * The allowlist of device control IDs.
   *
   * <ul>
   *   <li>{@code null} represents the universal set (contains all devices / allow all).
   *   <li>A non-null {@link Set} represents the matching set of allowed devices (an empty set
   *       matches no devices / allow none).
   * </ul>
   */
  @GuardedBy("lock")
  @Nullable
  private ImmutableSet<String> allowlist;

  /**
   * The blocklist of device control IDs.
   *
   * <ul>
   *   <li>{@code null} represents the universal set (contains all devices / block all).
   *   <li>A non-null {@link Set} represents the matching set of blocked devices (an empty set
   *       matches no devices / block none).
   * </ul>
   */
  @GuardedBy("lock")
  @Nullable
  private ImmutableSet<String> blocklist = ImmutableSet.of();

  @VisibleForTesting
  DeviceManagementFilter() {}

  /**
   * Sets the device control IDs in the allowlist.
   *
   * @param allowlist the allowlist of device control IDs, or {@code null} for the universal set
   *     (allow all)
   */
  public void setAllowlist(@Nullable Set<String> allowlist) {
    synchronized (lock) {
      this.allowlist = allowlist == null ? null : ImmutableSet.copyOf(allowlist);
      logState();
    }
  }

  /**
   * Sets the device control IDs in the blocklist.
   *
   * @param blocklist the blocklist of device control IDs, or {@code null} for the universal set
   *     (block all)
   */
  public void setBlocklist(@Nullable Set<String> blocklist) {
    synchronized (lock) {
      this.blocklist = blocklist == null ? null : ImmutableSet.copyOf(blocklist);
      logState();
    }
  }

  /**
   * Returns true if the device is allowed to have a device runner created or executed.
   *
   * @param controlId the control ID of the device
   */
  public boolean isAllowed(String controlId) {
    synchronized (lock) {
      return matchesList(allowlist, controlId) && !matchesList(blocklist, controlId);
    }
  }

  @GuardedBy("lock")
  private void logState() {
    logger.atInfo().log(
        "DeviceManagementFilter updated. Allowlist: %s, Blocklist: %s",
        allowlist == null ? "null (allow all)" : allowlist,
        blocklist == null ? "null (block all)" : blocklist);
  }

  private boolean matchesList(@Nullable Set<String> list, String controlId) {
    if (list == null) {
      return true;
    }
    return list.contains(controlId);
  }
}
