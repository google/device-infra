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
    name = "api",
    srcs = ["ClientApi.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__",
    ],
    deps = [
        ":annotations",
        "//java/com/google/devtools/mobileharness/infra/client/api/util/uploader:result_uploader",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode:util",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/plugin:job_reporter",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/lister",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:messaging_manager_holder",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/network",
        "//src/java/com/google/devtools/mobileharness/shared/util/system:shutdown_hook_manager",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_manager",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "module",
    srcs = ["ClientApiModule.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__",
    ],
    deps = [
        ":annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/plugin:gen_file_handler",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context_executors",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_factory_util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__",
    ],
    deps = [
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "client_api_helper",
    srcs = ["ClientApiHelper.java"],
    # Do NOT add visibility here.
    visibility = [
        "//java/com/google/wireless/qa/mobileharness/client/api:__pkg__",
    ],
    deps = [
        ":api",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job",
    ],
)
