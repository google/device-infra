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
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "config",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/infra/ats/server/proto/config:common_java_proto",
        "//src/devtools/mobileharness/infra/ats/server/proto/config:file_cleaner_service_java_grpc",
        "//src/devtools/mobileharness/infra/ats/server/proto/config:file_cleaner_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config:ats_server_config_module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config:file_cleaner_service",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config/store:ats_config_store",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config/store:in_memory_ats_config_store",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:annotations",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:database_connections",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags/core:testing",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@grpc-java//core",
        "@grpc-java//core:inprocess",
        "@grpc-java//testing",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":config"],
)
