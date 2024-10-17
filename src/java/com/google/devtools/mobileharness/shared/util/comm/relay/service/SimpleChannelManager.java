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

package com.google.devtools.mobileharness.shared.util.comm.relay.service;

import io.grpc.Channel;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.concurrent.ThreadSafe;

/** Manages channels with a concurrent map. */
@SuppressWarnings("GoogleInternalAnnotationsChecker")
@ThreadSafe
public final class SimpleChannelManager implements ChannelManager {

  private final ConcurrentMap<String, Channel> map = new ConcurrentHashMap<>();

  @Override
  public Optional<Channel> lookupChannel(String channelName) {
    return Optional.ofNullable(map.get(channelName));
  }

  @Override
  public void registerChannel(String channelName, Channel channel) {
    map.put(channelName, channel);
  }

  @Override
  public void registerChannels(Map<String, Channel> channels) {
    map.putAll(channels);
  }
}
