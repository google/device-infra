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

# Description:
#   Basic input data models of Mobile Harness jobs and tests.

java_library(
    name = "in",
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in",
        "avoid_dep",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
        "//java/com/google/devtools/mobileharness/api:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra:__subpackages__",
        "//java/com/google/devtools/mobileharness/service/moss/util/slg:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/shared/model:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/api:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/platform/fuchsia:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/service/moss/util/slg:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/client/api:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/model:__subpackages__",
    ],
    exports = [
        ":decorators",
        ":device_specs",
        ":dimensions",
        ":dirs",
        ":files",
        ":params",
        ":timeout",
    ],
)

java_library(
    name = "decorators",
    srcs = ["Decorators.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in:decorators",
        "avoid_dep",
    ],
    visibility = [
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
        "//java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:__pkg__",
    ],
    runtime_deps = [
        # Otherwise wireless/android/comms/tools/mobileharness/plugins/testtracker/client/sponge:testtracker_sponge_uploader_client_test will break.
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//java/com/google/wireless/qa/mobileharness/shared/util:local_file",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:touchable_timing",
        "//src/java/com/google/devtools/mobileharness/api/model/job/util:add_file_handler",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//third_party/java/gson",
        "//third_party/java/joda_time",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_specs",
    srcs = [
        "DeviceSpec.java",
        "DeviceSpecs.java",
    ],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in:device_specs",
        "avoid_dep",
    ],
    visibility = [
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
    ],
    deps = [
        ":decorators",
        ":dimensions",
        "//java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:device_requirements",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//third_party/java/gson",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "dimensions",
    srcs = ["Dimensions.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in:dimensions",
        "avoid_dep",
    ],
    visibility = [
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
        "//java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:__pkg__",
        "//java/com/google/devtools/mobileharness/shared/util/sharedpool:__pkg__",
        "//javatests/com/google/devtools/mobileharness/shared/util/sharedpool:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:__pkg__",
    ],
    deps = [
        "//devtools/mobileharness/service/moss/proto:slg_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "dirs",
    srcs = ["Dirs.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in:dirs",
        "avoid_dep",
    ],
    visibility = [
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
        "//java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "files",
    srcs = ["Files.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in:files",
        "avoid_dep",
    ],
    visibility = [
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:touchable_timing",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "params",
    srcs = ["Params.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in:params",
        "avoid_dep",
    ],
    visibility = [
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
    ],
    deps = [
        "//devtools/mobileharness/service/moss/proto:slg_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:touchable_timing",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "timeout",
    srcs = ["Timeout.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in:timeout",
        "avoid_dep",
    ],
    visibility = [
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
        "//java/com/google/devtools/mobileharness/service/gateway/controller:__pkg__",
        "//java/com/google/devtools/mobileharness/shared/util/time:__pkg__",
        "//java/com/google/wireless/qa/mobileharness/client/api/mode/remote:__pkg__",
        "//javatests/com/google/devtools/mobileharness/shared/util/time:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "in_factory",
    srcs = ["JobInInternalFactory.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/in:in_factory",
        "avoid_dep",
    ],
    visibility = [
        # Note: this tool is only for for MH infra, please don't add more visibilities.
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
        "//java/com/google/devtools/mobileharness/service/moss/util/slg:__pkg__",
        "//javatests/com/google/devtools/mobileharness/api/model/job:__subpackages__",
    ],
    deps = [
        ":dimensions",
        ":params",
        "//devtools/mobileharness/service/moss/proto:slg_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:touchable_timing",
    ],
)
