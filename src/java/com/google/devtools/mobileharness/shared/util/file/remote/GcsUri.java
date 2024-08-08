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

package com.google.devtools.mobileharness.shared.util.file.remote;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A wrapper class of GCS URI. */
@AutoValue
public abstract class GcsUri {
  private static final Pattern GCS_FILE_PATTERN =
      Pattern.compile("(?:gs://)?(?<bucket>[^/]+)/(?<path>.*$)");

  abstract String bucketName();

  abstract Path objectPath();

  public static GcsUri create(String bucketName, Path objectPath) {
    return new AutoValue_GcsUri(bucketName, objectPath);
  }

  public static GcsUri parseUri(String gcsUri) throws MobileHarnessException {
    Matcher m = GCS_FILE_PATTERN.matcher(gcsUri);
    if (!m.find()) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_DOWNLOAD_FILE_ERROR, "invalid gcs file pattern: %s" + gcsUri);
    }
    return GcsUri.create(m.group("bucket"), Path.of(m.group("path")));
  }

  @Memoized
  @Override
  public String toString() {
    return String.format("gs://%s/%s", bucketName(), objectPath());
  }

  @Memoized
  @Override
  public abstract int hashCode();
}
