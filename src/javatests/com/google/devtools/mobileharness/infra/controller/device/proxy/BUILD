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
    name = "proxy",
    srcs = glob(["*.java"]),
    runtime_deps = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/proxy:no_op_device_proxy",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:no_op_test",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:module",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:proxy_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:proxy_device_manager_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:proxy_device_requirement",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule:capture_logs",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule:print_test_name",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/model/job:job_info_mocker",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
        "medium",
    ],
    deps = [":proxy"],
)
