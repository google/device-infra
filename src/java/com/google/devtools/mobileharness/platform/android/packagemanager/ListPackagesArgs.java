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

package com.google.devtools.mobileharness.platform.android.packagemanager;

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Wrapper class for the arguments of {@link AndroidPackageManagerUtil#listPackages}. */
@AutoValue
public abstract class ListPackagesArgs {

  private static final String ADB_SHELL_LIST_PACKAGES = "pm list packages";
  private static final String ARG_SHOW_VERSION_CODE = "--show-versioncode";
  private static final String ARG_SHOW_UID = "-U";
  private static final String ARG_USER = "--user";

  /** Filter to only list the packages of the given type. */
  public abstract Optional<PackageType> packageType();

  /** Filter to only list the packages of the given status. */
  public abstract Optional<StatusFilter> statusFilter();

  /** Whether to show the version code. */
  public abstract boolean showVersionCode();

  /** Whether to show the UID. */
  public abstract boolean showUid();

  /** Filter to only those whose name contains this string. */
  public abstract Optional<String> nameFilter();

  @Memoized
  String suffix() {
    return nameFilter().map(it -> " " + it).orElse("");
  }

  @Memoized
  ImmutableList<String> argList() {
    List<String> argList = new ArrayList<>();
    argList.add(ADB_SHELL_LIST_PACKAGES);
    packageType().ifPresent(type -> argList.add(type.getOption()));
    statusFilter().ifPresent(filter -> argList.add(filter.getOption()));
    if (showVersionCode()) {
      argList.add(ARG_SHOW_VERSION_CODE);
    }
    if (showUid()) {
      argList.add(ARG_SHOW_UID);
    }
    return ImmutableList.copyOf(argList);
  }

  @Memoized
  @Override
  public abstract int hashCode();

  @Memoized
  @Override
  public abstract String toString();

  /** Returns the pm list packages command. */
  public String getCmd(UtilArgs utilArgs) {
    List<String> args = new ArrayList<>(argList());
    if (utilArgs.userId().isPresent()) {
      args.add(ARG_USER);
      args.add(utilArgs.userId().get());
    }
    return args.stream().collect(joining(" ", "", suffix()));
  }

  /** Gets default ListPackagesArgs builder instance. */
  public static Builder builder() {
    return new AutoValue_ListPackagesArgs.Builder().setShowVersionCode(false).setShowUid(false);
  }

  /** Auto value builder for {@link ListPackagesArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPackageType(PackageType packageType);

    public abstract Builder setStatusFilter(StatusFilter statusFilter);

    public abstract Builder setShowVersionCode(boolean showVersionCode);

    public abstract Builder setShowUid(boolean showUid);

    public abstract Builder setNameFilter(String nameFilter);

    public abstract ListPackagesArgs build();
  }
}
