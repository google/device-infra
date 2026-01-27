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

load("@rules_java//java:defs.bzl", "java_library")

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "error",
    srcs = glob(
        ["*.java"],
        exclude = [
            "MobileHarnessExceptionFactory.java",
        ],
    ),
    visibility = [
        "//:deviceinfra_api_users",
        "//:omnilab_fusion",
        "//java/com/google/fitbit/platform/testing/mobileharness/plugins/sherlog:__subpackages__",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/model:error_id",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_formatter",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

# For internal use only. Do NOT add external visibility to it.
java_library(
    name = "exception_factory",
    srcs = ["MobileHarnessExceptionFactory.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/jobcreator:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/plan:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/config:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [
        ":error",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
