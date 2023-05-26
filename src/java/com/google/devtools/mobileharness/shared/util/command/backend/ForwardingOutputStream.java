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

import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream which forwards all its method calls indiscriminately to another output stream.
 * It does not forward methods of {@link Object}, only methods declared by {@link OutputStream}.
 */
abstract class ForwardingOutputStream extends OutputStream {
  private final OutputStream delegate;

  ForwardingOutputStream(OutputStream delegate) {
    this.delegate = delegate;
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  @Override
  public void write(byte[] b) throws IOException {
    delegate.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    delegate.write(b, off, len);
  }

  @Override
  public void write(int b) throws IOException {
    delegate.write(b);
  }
}
