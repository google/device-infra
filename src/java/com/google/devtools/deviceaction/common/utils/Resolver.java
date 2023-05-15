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

package com.google.devtools.deviceaction.common.utils;

import com.google.common.collect.ImmutableMultimap;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import java.io.File;
import java.util.List;

/** An interface for file resolvers. */
public interface Resolver {

  /**
   * Resolves a list of file specs to tagged local files.
   *
   * @param fileSpecs a list of file specs.
   * @return multimap from tags to the local files.
   */
  ImmutableMultimap<String, File> resolve(List<FileSpec> fileSpecs)
      throws DeviceActionException, InterruptedException;
}
