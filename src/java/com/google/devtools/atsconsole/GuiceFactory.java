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

package com.google.devtools.atsconsole;

import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/** Instantiates commands with the given injector. */
public final class GuiceFactory implements IFactory {

  private final Injector injector;

  public GuiceFactory(Injector injector) {
    this.injector = injector;
  }

  @Override
  public <K> K create(Class<K> cls) throws Exception {
    try {
      return injector.getInstance(cls);
    } catch (ConfigurationException ex) { // no implementation found in Guice configuration
      return CommandLine.defaultFactory().create(cls); // fallback if missing
    }
  }
}
