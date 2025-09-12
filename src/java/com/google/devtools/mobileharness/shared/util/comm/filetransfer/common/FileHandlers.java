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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.common;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.reflect.TypeToken;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/** A group of {@link Handler} for {@link #notify}ing. */
public class FileHandlers {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Map<Class<Message>, Handler<Message>> handlers = new HashMap<>();

  /** Handler of received file. */
  public interface Handler<M extends Message> {
    /**
     * Called while file is received.
     *
     * @param meta Metadata of transferred file
     * @param receivedPath file path in receiver side
     * @param originalPath file path in sender side
     * @param checksum checksum of the file
     */
    void onReceived(M meta, Path receivedPath, Path originalPath, @Nullable String checksum)
        throws MobileHarnessException, InterruptedException;
  }

  /**
   * Adds handler for request with metadata in the type of {@code metadataClass}. There is only one
   * handler allowed for each metadata class, because the handler may move the receive file away.
   */
  @CanIgnoreReturnValue
  public <T extends Message> FileHandlers addHandler(Class<T> metadataClass, Handler<T> handler)
      throws MobileHarnessException {
    MobileHarnessExceptions.check(
        metadataClass == getMetadataClass(handler),
        InfraErrorId.FT_METADATA_CLASS_MISMATCH,
        () -> "Handler doesn't have the same metadata class as " + metadataClass);
    // It's safe to generalize {@code handler} for easier use, because it's type has been checked to
    // be {@code metadataClass}.
    @SuppressWarnings("unchecked")
    Class<Message> rawMetaClass = (Class<Message>) metadataClass;
    @SuppressWarnings("unchecked")
    Handler<Message> rawHandler = (Handler<Message>) handler;
    handlers.put(rawMetaClass, rawHandler);
    return this;
  }

  @VisibleForTesting
  static <M extends Message> Class<M> getMetadataClass(Handler<M> handler)
      throws MobileHarnessException {
    TypeToken<?> token =
        TypeToken.of(handler.getClass()).resolveType(Handler.class.getTypeParameters()[0]);
    Type type = token.getType();
    MobileHarnessExceptions.check(
        type instanceof Class,
        InfraErrorId.FT_METADATA_CLASS_MISMATCH,
        () ->
            "Handler is not initialized properly. Please create the handler "
                + "with a correct subclass, and DO NOT use lambda.");

    @SuppressWarnings("unchecked")
    Class<M> metadataType = (Class<M>) type;
    return metadataType;
  }

  /** Notifies all handlers after file is received. */
  public void notify(Any metadata, Path savedPath, Path originalPath, @Nullable String checksum)
      throws MobileHarnessException, InterruptedException {
    for (Entry<Class<Message>, Handler<Message>> entry : handlers.entrySet()) {
      Class<Message> metaClass = entry.getKey();
      if (!metadata.is(metaClass)) {
        continue;
      }

      Message meta;
      try {
        meta = metadata.unpack(metaClass);
      } catch (InvalidProtocolBufferException e) {
        logger.atWarning().log(
            "Failed to unpack metadata as class [%s], raw data : %s", metaClass, e.getMessage());
        continue;
      }
      entry.getValue().onReceived(meta, savedPath, originalPath, checksum);
      break;
    }
  }
}
