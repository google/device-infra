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
#   Output data models for Mobile Harness labs and devices.
package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/api/model:__subpackages__",
    ],
)

java_library(
    name = "out",
    exports = [
        ":property",
        ":status",
    ],
)

java_library(
    name = "status",
    srcs = ["Status.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "property",
    srcs = ["Properties.java"],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "property_factory",
    srcs = [
        "PropertiesFactory.java",
    ],
    visibility = [
        # Do no add visibility here. Please use local_property_factory instead.
        "//src/java/com/google/devtools/mobileharness/api/model/lab:__pkg__",
    ],
    deps = [
        ":local_property",
        ":property",
    ],
)

java_library(
    name = "local_property",
    srcs = ["LocalProperties.java"],
    visibility = [
        # Do no add visibility here. Please use local_property_factory instead.
    ],
    deps = [
        ":property",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_property_factory",
    srcs = [
        "LocalPropertiesFactory.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/api/model/lab:__pkg__",
    ],
    deps = [
        ":local_property",
        ":property",
    ],
)
