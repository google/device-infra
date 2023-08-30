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

package com.google.devtools.mobileharness.shared.util.comm.stub;

import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.deviceinfra.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.constant.closeable.CountingCloseable;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;

/** Manager for managing {@link ManagedChannel}s. */
public class ChannelManager {

  /**
   * After all stubs of a channel have been closed, the channel will be shut down and removed from
   * cache after this expiration time multiplied by 1.0 ~ 2.0.
   */
  private static final Duration CHANNEL_EXPIRATION_TIME = Duration.ofHours(6L);

  private static final ChannelManager INSTANCE = new ChannelManager();

  public static ChannelManager getInstance() {
    return INSTANCE;
  }

  private final Duration channelExpirationTime;

  @GuardedBy("itself")
  private final Map<String, CountingManagedChannel> channels = new HashMap<>();

  private ChannelManager() {
    this(
        CHANNEL_EXPIRATION_TIME,
        MoreExecutors.listeningDecorator(
            Executors.newScheduledThreadPool(
                /* corePoolSize= */ 1,
                ThreadFactoryUtil.createThreadFactory("grpc-channel-cleaner"))));
  }

  @VisibleForTesting
  ChannelManager(Duration channelExpirationTime, ListeningScheduledExecutorService threadPool) {
    this.channelExpirationTime = channelExpirationTime;
    logFailure(
        threadPool.scheduleWithFixedDelay(
            new ChannelCleaner(), channelExpirationTime, channelExpirationTime),
        Level.SEVERE,
        "Fatal error in gRPC channel cleaner");
  }

  /**
   * Creates a gRPC stub, creates and caches a {@link ManagedChannel} before if necessary.
   *
   * <p>The stub must be {@linkplain AutoCloseable#close() closed} after used. If not, the
   * corresponding {@link ManagedChannel} will never be shut down.
   *
   * <p>If a cached channel has no open (unclosed) stubs for a while (in detail, {@link
   * #CHANNEL_EXPIRATION_TIME} multiplied by 1.0 ~ 2.0), the channel will be shut down and removed
   * from cache.
   *
   * @param <T> type of the gRPC stub
   */
  public <T extends CountingCloseable> T createStub(
      String key,
      Supplier<? extends ManagedChannel> channelCreator,
      Function<Channel, T> stubCreator) {
    synchronized (channels) {
      CountingManagedChannel channel =
          channels.computeIfAbsent(
              key, k -> new CountingManagedChannel(channelCreator.get(), channelExpirationTime));
      return channel.createStub(stubCreator);
    }
  }

  private class ChannelCleaner implements Runnable {

    @Override
    public void run() {
      synchronized (channels) {
        // Shuts down all channels which have no open stubs for a while and removes them.
        channels.values().removeIf(CountingManagedChannel::tryShutdown);
      }
    }
  }
}
