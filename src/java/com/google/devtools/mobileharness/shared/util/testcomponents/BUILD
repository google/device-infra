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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "testcomponents",
    srcs = ["TestComponentsUtil.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness:__subpackages__",
        "//javatests/com/google/devtools/mobileharness:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness:__subpackages__",
    ],
    deps = [
        "//google/devtools/resultstore/v2:test_component_java_proto",
        "//java/com/google/devtools/sponge/shared/parsers:test_component_parser",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/deviceinfra/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
