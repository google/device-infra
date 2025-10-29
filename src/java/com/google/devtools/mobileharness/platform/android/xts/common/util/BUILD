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
    name = "abi_formatter",
    srcs = ["AbiFormatter.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
    ],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "abi_util",
    srcs = ["AbiUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "xts_constants",
    srcs = ["XtsConstants.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util/xtsdownloader:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/test/util/xtsdownloader:__subpackages__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
)

java_library(
    name = "xts_dir_util",
    srcs = ["XtsDirUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/integration/concord:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
    ],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "xts_command_util",
    srcs = ["XtsCommandUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
    ],
    deps = [
        ":xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "mobly_test_loader",
    srcs = ["MoblyTestLoader.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/common/util:__subpackages__",
    ],
    deps = [
        ":xts_constants",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
