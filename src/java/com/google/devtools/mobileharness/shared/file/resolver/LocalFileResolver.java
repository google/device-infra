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

package com.google.devtools.mobileharness.shared.file.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** The resolver for local files. */
@ThreadSafe
public class LocalFileResolver extends AbstractFileResolver {

  @ParamAnnotation(
      required = false,
      help =
          "Ignore the non-existing local file without throwing an exception. "
              + "By default it is true.")
  public static final String PARAM_IGNORE_NON_EXISTING_LOCAL_FILE =
      "ignore_non_existing_local_file";

  public static final boolean DEFAULT_IGNORE_NON_EXISTING_LOCAL_FILE = true;

  private final LocalFileUtil localFileUtil;

  public LocalFileResolver(
      @Nullable ListeningExecutorService executorService, LocalFileUtil localFileUtil) {
    super(executorService);
    this.localFileUtil = localFileUtil;
  }

  @Override
  protected ResolveResult actuallyResolve(ResolveSource resolveSource)
      throws MobileHarnessException {
    if (!Boolean.parseBoolean(
        resolveSource.parameters().get(PARAM_IGNORE_NON_EXISTING_LOCAL_FILE))) {
      localFileUtil.checkFileOrDir(resolveSource.path());
    }
    return ResolveResult.create(
        ImmutableList.of(resolveSource.path()), ImmutableMap.of(), resolveSource);
  }

  @Override
  protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
    return localFileUtil.isLocalFileOrDir(resolveSource.path());
  }
}
