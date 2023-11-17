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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result:__subpackages__",
    ],
)

proto_library(
    name = "result_proto",
    srcs = ["result.proto"],
)

java_proto_library(
    name = "result_java_proto",
    deps = [":result_proto"],
)

proto_library(
    name = "report_proto",
    srcs = ["report.proto"],
)

java_proto_library(
    name = "report_java_proto",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/result:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result:__subpackages__",
    ],
    deps = [":report_proto"],
)
