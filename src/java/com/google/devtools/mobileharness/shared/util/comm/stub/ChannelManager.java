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
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Channel manager for managing gRPC {@link ManagedChannel}s. */
public class ChannelManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * After all stubs associated with a channel become unreferenced and garbage collected, the
   * channel will be shut down and removed from the manager after this expiration time multiplied by
   * 1.0 ~ 2.0.
   */
  private static final Duration CHANNEL_EXPIRATION_TIME = Duration.ofHours(6L);

  private static final ChannelManager INSTANCE = new ChannelManager();

  public static ChannelManager getInstance() {
    return INSTANCE;
  }

  private final Duration channelExpirationTime;

  /** Channels whose key is the channel key provided by the caller. */
  private final Map<String, CountingChannel> channels = new ConcurrentHashMap<>();

  /** Weak references to stubs, whose value is the key of the channel associated with the stub. */
  private final Map<Reference<?>, String> stubs = new ConcurrentHashMap<>();

  /** A reference queue for garbage collected stubs. */
  private final ReferenceQueue<Object> collectedStubs = new ReferenceQueue<>();

  private ChannelManager() {
    this(
        CHANNEL_EXPIRATION_TIME,
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor(
                ThreadFactoryUtil.createThreadFactory("grpc-stub-cleaner", /* daemon= */ true))),
        MoreExecutors.listeningDecorator(
            Executors.newScheduledThreadPool(
                /* corePoolSize= */ 1,
                ThreadFactoryUtil.createThreadFactory(
                    "grpc-channel-cleaner", /* daemon= */ true))));
  }

  @VisibleForTesting
  ChannelManager(
      Duration channelExpirationTime,
      ListeningExecutorService threadPool,
      ListeningScheduledExecutorService scheduledThreadPool) {
    this.channelExpirationTime = channelExpirationTime;

    // Starts the stub cleaner and the channel cleaner threads.
    logFailure(
        threadPool.submit(new StubCleaner()), Level.SEVERE, "Fatal error in gRPC stub cleaner");
    logFailure(
        scheduledThreadPool.scheduleWithFixedDelay(
            new ChannelCleaner(), channelExpirationTime, channelExpirationTime),
        Level.SEVERE,
        "Fatal error in gRPC channel cleaner");
  }

  /**
   * Creates a gRPC stub. If needed, creates a {@link ManagedChannel} and caches it in the manager
   * before creating the stub.
   *
   * <p>After all stubs associated with a channel become unreferenced and garbage collected, the
   * channel will be shut down and removed from the manager after a while (in detail, {@link
   * #CHANNEL_EXPIRATION_TIME} multiplied by 1.0 ~ 2.0).
   *
   * <p>In one invocation of this method, if the {@code channelCreator} creates a channel and then
   * the {@code stubCreator} throws an exception, the new channel will not be shut down and will
   * still be cached.
   *
   * @param channelKey a key unique for a gRPC server, e.g., "localhost:9994"
   * @param channelCreator a function that creates a {@link ManagedChannel} connecting to the gRPC
   *     server specified by the {@code channelKey}. It is only invoked if there is no cached
   *     channel with the same key in the manager.
   * @param stubCreator a function that creates a gRPC stub / an object wrapping the gRPC stub,
   *     using the given {@link Channel} specified by the {@code channelKey}. The return value of
   *     this function will be {@linkplain WeakReference weakly referenced} by the manager to track
   *     if the channel can be shut down. The stub creator should be lightweight, because stub
   *     creators on the same {@code channelKey} in other threads will be blocked while this creator
   *     is in progress.
   * @param <T> type of the gRPC stub
   */
  public <T> T createStub(
      String channelKey,
      Supplier<? extends ManagedChannel> channelCreator,
      Function<Channel, T> stubCreator) {
    T stub;
    while (true) {
      // Gets or creates a channel wrapper.
      CountingChannel channel = channels.computeIfAbsent(channelKey, CountingChannel::new);
      try {
        // Creates a channel if needed, and then create a stub using the channel.
        stub = channel.createChannelAndStub(channelCreator, stubCreator);
        break;
      } catch (ChannelClosedException e) {
        // If the channel cleaner is cleaning up the same key, and the channel shutdown happened
        // before channel.createChannelAndStub(), but the channel wrapper removal happened after
        // channels.computeIfAbsent(), it needs to remove the channel wrapper here and create a new
        // channel wrapper. The case may happen at most once per createStub() call.
        channels.remove(channelKey, channel);
      }
    }

    // Records a weak reference to the new stub.
    stubs.put(new WeakReference<>(stub, collectedStubs), channelKey);
    return stub;
  }

  /**
   * A {@link ManagedChannel} wrapper that tracks the number of stubs associated with the channel
   * and the time when the stub count reaches zero.
   */
  private class CountingChannel {

    private final String channelKey;

    private final Object lock = new Object();

    /** Whether {@link #shutdown()} of this channel has been invoked. */
    @GuardedBy("lock")
    private boolean closed;

    @GuardedBy("lock")
    @Nullable
    private ManagedChannel channel;

    /** The number of stubs associated with the channel. */
    @GuardedBy("lock")
    private int stubCount;

    /** The time when {@link #stubCount} reached zero, or {@code null} if the count is non-zero. */
    @GuardedBy("lock")
    @Nullable
    private Instant becomeUnusedTime;

    private CountingChannel(String channelKey) {
      this.channelKey = channelKey;
      this.becomeUnusedTime = Instant.now();
    }

    /**
     * Creates a channel if needed, and then creates a stub using the channel. If successful,
     * updates the {@link #stubCount} and {@link #becomeUnusedTime}.
     *
     * @throws ChannelClosedException if {@link #shutdown()} of this channel has been invoked
     */
    private <T> T createChannelAndStub(
        Supplier<? extends ManagedChannel> channelCreator, Function<Channel, T> stubCreator)
        throws ChannelClosedException {
      synchronized (lock) {
        if (closed) {
          throw new ChannelClosedException();
        }

        // Creates a channel if needed.
        if (channel == null) {
          channel = channelCreator.get();
          logger.atInfo().log(
              "Channel [%s] cached, channel_id=%s", channelKey, System.identityHashCode(channel));
        }

        // Creates a stub using the channel.
        T stub = stubCreator.apply(channel);

        stubCount++;
        becomeUnusedTime = null;
        logger.atInfo().log(
            "Channel [%s] stub count: %s -> %s", channelKey, stubCount - 1, stubCount);
        return stub;
      }
    }

    private void decrementStubCount() {
      synchronized (lock) {
        stubCount--;
        if (stubCount == 0) {
          becomeUnusedTime = Instant.now();
        }
        logger.atInfo().log(
            "Channel [%s] stub count: %s -> %s", channelKey, stubCount + 1, stubCount);
      }
    }

    /**
     * Attempts to shut down the channel. If the channel can be shut down, shuts down it and removes
     * it from the manager.
     */
    private void tryShutdownAndRemove() {
      boolean canShutdown;
      synchronized (lock) {
        canShutdown = canShutdown();
        if (canShutdown) {
          shutdown();
        }
      }
      if (canShutdown) {
        channels.remove(channelKey, this);
      }
    }

    /**
     * Returns whether the channel can be shut down.
     *
     * <p>A channel can be shut down if no stubs have been associated with the channel for {@link
     * #channelExpirationTime}.
     */
    @GuardedBy("lock")
    private boolean canShutdown() {
      return becomeUnusedTime != null
          && Instant.now().isAfter(becomeUnusedTime.plus(channelExpirationTime));
    }

    @GuardedBy("lock")
    private void shutdown() {
      if (channel != null) {
        channel.shutdown();
        logger.atInfo().log(
            "Channel [%s] is shut down and removed from cache manager, channel_id=%s",
            channelKey, System.identityHashCode(channel));
      }
      closed = true;
    }
  }

  /** Stub cleaner that tracks the garbage collection of stubs associated with cached channels. */
  private class StubCleaner implements Callable<Void> {

    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public Void call() throws InterruptedException {
      while (true) {
        // Blocks until a stub has been garbage collected and obtains its reference.
        Reference<?> stub = collectedStubs.remove();

        // Updates the stub count of the channel associated with the stub.
        String channelKey = stubs.remove(stub);
        CountingChannel channel = channels.get(channelKey);
        if (channel != null) {
          channel.decrementStubCount();
        }
      }
    }
  }

  /**
   * Channel cleaner that is periodically invoked to shut down and remove all channels that
   * {@linkplain CountingChannel#canShutdown() can be} shut down.
   */
  private class ChannelCleaner implements Runnable {

    @Override
    public void run() {
      channels.values().forEach(CountingChannel::tryShutdownAndRemove);
    }
  }

  /**
   * Signals that {@link CountingChannel#shutdown()} has been invoked before {@link
   * CountingChannel#createChannelAndStub(Supplier, Function)} on the same channel.
   */
  private static class ChannelClosedException extends Exception {}
}
