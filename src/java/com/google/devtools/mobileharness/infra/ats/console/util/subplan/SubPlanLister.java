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

package com.google.devtools.mobileharness.infra.ats.console.util.subplan;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import javax.inject.Inject;

/** Lister for listing subplans. */
public class SubPlanLister {

  private final LocalFileUtil localFileUtil;

  @Inject
  SubPlanLister(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /** Lists file names of subplans. */
  public ImmutableList<String> listSubPlans(String xtsRootDir, String xtsType)
      throws MobileHarnessException {
    Path subPlansDir = XtsDirUtil.getXtsSubPlansDir(Path.of(xtsRootDir), xtsType);
    if (!subPlansDir.toFile().exists()) {
      throw new IllegalStateException(
          String.format("Subplans directory %s does not exist.", subPlansDir.toAbsolutePath()));
    }
    return localFileUtil
        .listFilePaths(
            subPlansDir,
            /* recursively= */ false,
            path -> path.getFileName().toString().endsWith(".xml"))
        .stream()
        .map(path -> Files.getNameWithoutExtension(path.getFileName().toString()))
        .sorted()
        .collect(toImmutableList());
  }
}
