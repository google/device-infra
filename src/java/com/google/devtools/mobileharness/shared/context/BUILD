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
    name = "invocation_context",
    srcs = ["InvocationContext.java"],
    visibility = [
        "//javatests/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/shared/context:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "invocation_context_executors",
    srcs = ["InvocationContextExecutors.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:__pkg__",
    ],
    deps = [
        ":invocation_context",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)
