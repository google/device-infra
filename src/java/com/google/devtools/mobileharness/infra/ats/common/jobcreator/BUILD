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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__"],
)

java_library(
    name = "xts_job_creator",
    srcs = ["XtsJobCreator.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
    ],
    deps = [
        ":module_sharding_args_generator",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_handler_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_handler_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:xts_property_name",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/plan:test_plan_parser",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_common",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_args",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_generator",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "console_job_creator",
    srcs = ["ConsoleJobCreator.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common/jobcreator:__pkg__",
    ],
    deps = [
        ":module_sharding_args_generator",
        ":xts_job_creator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_handler_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:xts_property_name",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/plan:test_plan_parser",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_common",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:previous_result_loader",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_args",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_generator",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_type",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "server_job_creator",
    srcs = ["ServerJobCreator.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common/jobcreator:__pkg__",
    ],
    deps = [
        ":module_sharding_args_generator",
        ":xts_job_creator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_handler_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:xts_property_name",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/plan:test_plan_parser",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:previous_result_loader",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_args",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_generator",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_type",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "module_sharding_args_generator",
    srcs = ["ModuleShardingArgsGenerator.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:shard_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "@maven//:com_google_guava_guava",
    ],
)
