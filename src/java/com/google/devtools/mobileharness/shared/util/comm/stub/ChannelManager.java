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
import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.deviceinfra.shared.util.concurrent.ThreadFactoryUtil;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.annotation.Nullable;

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
   * Creates a gRPC stub. If necessary, creates a {@link ManagedChannel} and caches it in the
   * manager before creating the stub.
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
    AtomicReference<T> stubReceiver = new AtomicReference<>();
    AtomicReference<Throwable> stubCreationFailureReceiver = new AtomicReference<>();

    // Creates or gets a channel, and then creates a stub using the channel.
    channels.compute(
        channelKey,
        (key, existingChannel) -> {
          CountingChannel channel =
              existingChannel == null
                  ? new CountingChannel(channelKey, channelCreator.get())
                  : existingChannel;
          channel.createStub(stubCreator, stubReceiver, stubCreationFailureReceiver);
          return channel;
        });
    rethrowUncheckedIfAny(stubCreationFailureReceiver.get());

    // Records a weak reference to the new stub.
    stubs.put(new WeakReference<>(stubReceiver.get(), collectedStubs), channelKey);
    return stubReceiver.get();
  }

  /**
   * A {@link ManagedChannel} wrapper that tracks the number of stubs associated with the channel
   * and the time when the stub count reaches zero.
   *
   * <p>Note that this class is not thread-safe. All access to this class must be guarded by atomic
   * computing operations (e.g., {@link Map#compute}) with the same key in a {@link
   * ConcurrentHashMap}.
   */
  private class CountingChannel {

    private final String channelKey;
    private final ManagedChannel channel;

    /** The number of stubs associated with the channel. */
    private int stubCount;

    /** The time when {@link #stubCount} reached zero, or {@code null} if the count is non-zero. */
    @Nullable private Instant becomeUnusedTime;

    private CountingChannel(String channelKey, ManagedChannel channel) {
      this.channelKey = channelKey;
      this.channel = channel;
      this.becomeUnusedTime = Instant.now();
      logger.atInfo().log(
          "Channel [%s] cached, channel_id=%s", channelKey, System.identityHashCode(channel));
    }

    /**
     * Creates a stub using the channel. If successful, updates the {@link #stubCount} and {@link
     * #becomeUnusedTime}. If failed, saves the exception in {@code stubCreationFailureReceiver}.
     */
    private <T> void createStub(
        Function<Channel, T> stubCreator,
        AtomicReference<T> stubReceiver,
        AtomicReference<Throwable> stubCreationFailureReceiver) {
      try {
        stubReceiver.set(stubCreator.apply(channel));
        stubCount++;
        becomeUnusedTime = null;
        logger.atInfo().log(
            "Channel [%s] stub count: %s -> %s", channelKey, stubCount - 1, stubCount);
      } catch (RuntimeException | Error e) {
        // Catches the stub creation failure to ensure that the new channel will be cached.
        stubCreationFailureReceiver.set(e);
      }
    }

    private CountingChannel decrementStubCount() {
      stubCount--;
      if (stubCount == 0) {
        becomeUnusedTime = Instant.now();
      }
      logger.atInfo().log(
          "Channel [%s] stub count: %s -> %s", channelKey, stubCount + 1, stubCount);
      return this;
    }

    /**
     * Tries to shut down the channel.
     *
     * @return {@code null} if the channel can be shut down and has been shut down by this method,
     *     {@code this} if the channel cannot be shut down
     */
    @Nullable
    private CountingChannel tryShutdown() {
      if (canShutdown()) {
        shutdown();
        logger.atInfo().log(
            "Channel [%s] is shut down and removed from cache manager, channel_id=%s",
            channelKey, System.identityHashCode(channel));
        return null;
      } else {
        return this;
      }
    }

    /**
     * Returns whether the channel can be shut down.
     *
     * <p>A channel can be shut down if no stubs have been associated with the channel for {@link
     * #channelExpirationTime}.
     */
    private boolean canShutdown() {
      return becomeUnusedTime != null
          && Instant.now().isAfter(becomeUnusedTime.plus(channelExpirationTime));
    }

    private void shutdown() {
      channel.shutdown();
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
        channels.computeIfPresent(
            channelKey, (key, existingChannel) -> existingChannel.decrementStubCount());
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
      channels
          .keySet()
          .forEach(
              channelKey ->
                  channels.computeIfPresent(
                      channelKey, (key, existingChannel) -> existingChannel.tryShutdown()));
    }
  }

  /**
   * Throws {@code error} if it is an unchecked exception ({@link Runtime} or {@link Error}). Does
   * nothing if {@code error} is {@code null}. Throws {@link AssertionError} if {@code error} is a
   * checked exception (assuming it will not happen).
   */
  private static void rethrowUncheckedIfAny(@Nullable Throwable error) {
    if (error != null) {
      Throwables.throwIfUnchecked(error);
      throw new AssertionError(error);
    }
  }
}
