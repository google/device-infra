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

package com.google.devtools.mobileharness.shared.util.command.backend;

import com.google.auto.value.AutoValue;
import com.google.common.base.VerifyException;
import com.google.common.io.ByteSink;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Supplier of an {@link OutputStream} that buffers lines and feeds them to a {@Consumer}.
 *
 * <p>This class makes an effort to ensure that logically equivalent byte sinks are considered equal
 * by the <code>equals</code method, which increases the likelihood that a native process with
 * equal stdout and stderr sinks uses redirectErrorStream, which improves the user experience.
 * However, given that consumers may frequently be using lambdas for consumers and that lambdas
 * don't have meaningful equality, this is a best effort, not a guarantee.
 */
@AutoValue
abstract class LineConsumingByteSink extends ByteSink {
  /** Returns a byte sink that consumes lines encoded with the specified charset. */
  static ByteSink create(Consumer<String> consumer, Charset charset) {
    return new AutoValue_LineConsumingByteSink(consumer, charset);
  }

  abstract Consumer<String> consumer();

  abstract Charset charset();

  @Override
  public final OutputStream openStream() {
    return new Stream();
  }

  private final class Stream extends OutputStream {
    @Nullable private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean wasReturn;

    @Override
    public void write(int b) throws IOException {
      if (buffer == null) {
        throw new IOException("stream is closed");
      }
      boolean isNewline = b == '\n';
      boolean isReturn = b == '\r';
      if (isNewline && wasReturn) {
        // do nothing on \n following \r
      } else if (isNewline || isReturn) {
        consumeLine();
        buffer.reset();
      } else {
        buffer.write(b);
      }
      wasReturn = isReturn;
    }

    @Override
    public void close() {
      if (buffer != null && buffer.size() > 0) {
        consumeLine();
      }
      buffer = null;
    }

    private void consumeLine() {
      String line;
      try {
        line = buffer.toString(charset().name());
      } catch (UnsupportedEncodingException e) {
        throw new VerifyException(e);
      }
      consumer().accept(line);
    }
  }
}
