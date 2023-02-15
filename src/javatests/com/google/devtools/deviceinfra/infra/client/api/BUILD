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

load("//src/javatests/com/google/devtools/deviceinfra/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "api",
    srcs = glob(["*.java"]),
    runtime_deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:no_op_test",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/deviceinfra/infra/client/api",
        "//src/java/com/google/devtools/deviceinfra/infra/client/api:module",
        "//src/java/com/google/devtools/deviceinfra/infra/client/api/mode/local",
        "//src/java/com/google/devtools/deviceinfra/shared/util/flags",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":api"],
)
