# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "quota",
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    exports = [
        ":quota_manager_interface_only",
        "//src/devtools/mobileharness/shared/util/quota/proto:quota_java_proto",
    ],
    runtime_deps = [
        ":local_quota_manager",
    ],
)

java_library(
    name = "quota_manager_interface_only",
    srcs = ["QuotaManager.java"],
    visibility = [
        # Do not use this target directly. In most cases, you should use "quota" instead.
        # The only exception is that you care about library size extremely and you are very sure
        # that there will be only one process using QuotaManager in one machine.
        # Note that you should also add runtime_deps "local_quota_manager" or "remote_quota_manager"
        # when you use it.
    ],
    deps = [
        "//src/devtools/mobileharness/shared/util/quota/proto:quota_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "local_quota_manager",
    srcs = ["LocalQuotaManager.java"],
    visibility = [
        # Do not use this target directly. In most cases, you should use "quota" instead.
        # See "quota_manager_interface_only".
    ],
    deps = [
        ":quota_manager_interface_only",
        "//src/devtools/mobileharness/shared/util/quota/proto:quota_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
