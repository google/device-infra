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

package com.google.devtools.mobileharness.api.devicemanager.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static java.util.Comparator.comparingInt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.proto.PersistentDeviceInfoProto.PersistentDeviceInfo;
import com.google.devtools.mobileharness.api.devicemanager.proto.PersistentDeviceInfoProto.PersistentDeviceInfoKey;
import com.google.devtools.mobileharness.api.devicemanager.proto.PersistentDeviceInfoProto.PersistentDeviceInfos;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.concurrent.GuardedBy;

/**
 * Manager for managing persistent device info which is beyond the lifecycle of a device runner,
 * like Android real device chip ID.
 */
public class PersistentDeviceInfoManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final PersistentDeviceInfoManager INSTANCE = new PersistentDeviceInfoManager();

  public static PersistentDeviceInfoManager getInstance() {
    return INSTANCE;
  }

  private static final Comparator<PersistentDeviceInfoKey> KEY_COMPARATOR =
      comparingInt((PersistentDeviceInfoKey key) -> key.getKeyType().getNumber())
          .thenComparing(PersistentDeviceInfoKey::getKey);

  /** TODO: Saves it in a local file. */
  @GuardedBy("itself")
  private final Map<PersistentDeviceInfoKey, PersistentDeviceInfo> infos =
      new TreeMap<>(KEY_COMPARATOR);

  @VisibleForTesting
  PersistentDeviceInfoManager() {}

  public void put(PersistentDeviceInfo info) {
    checkArgument(info.hasKey());

    synchronized (infos) {
      PersistentDeviceInfo oldInfo = infos.put(info.getKey(), info);
      if (oldInfo == null) {
        logger.atInfo().log("Add device info: %s", shortDebugString(info));
      } else {
        logger.atInfo().log(
            "Replace device info, new=[%s], old=[%s]",
            shortDebugString(info), shortDebugString(oldInfo));
      }
    }
  }

  public Optional<PersistentDeviceInfo> get(PersistentDeviceInfoKey key) {
    synchronized (infos) {
      return Optional.ofNullable(infos.get(key));
    }
  }

  @SuppressWarnings("unused")
  @GuardedBy("infos")
  private PersistentDeviceInfos getAll() {
    return PersistentDeviceInfos.newBuilder().addAllInfos(infos.values()).build();
  }
}
