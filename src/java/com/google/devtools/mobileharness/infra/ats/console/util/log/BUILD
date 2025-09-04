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
    name = "log_dumper",
    srcs = ["LogDumper.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "log_record_printer",
    srcs = ["LogRecordPrinter.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_text_style",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:javax_inject_jsr330_api",
    ],
)
