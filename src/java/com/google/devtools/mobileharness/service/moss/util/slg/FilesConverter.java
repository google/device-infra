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

package com.google.devtools.mobileharness.service.moss.util.slg;

import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.devtools.mobileharness.service.moss.proto.Slg.FileProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.FilesProto;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.JobInInternalFactory;

/** Utility class to help convert a {@link Files} to a {@link FilesProto} in forward or backward. */
final class FilesConverter {

  private FilesConverter() {}

  /** Gets a {@link Files} by the given {@link TouchableTiming} and {@link FilesProto}. */
  static Files fromProto(TouchableTiming timing, FilesProto filesProto) {
    return JobInInternalFactory.createFiles(
        com.google.devtools.mobileharness.api.model.job.in.JobInInternalFactory.createFiles(
            timing, filesProto));
  }

  /** Gets a {@link FilesProto} by the given {@link Files}. */
  static FilesProto toProto(Files files) {
    FilesProto.Builder filesProto = FilesProto.newBuilder();
    files
        .getAll()
        .forEach(
            (tag, location) ->
                filesProto.addFile(FileProto.newBuilder().setTag(tag).setLocation(location)));

    return filesProto.build();
  }
}
