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
    name = "process_builder_listener",
    srcs = ["ProcessBuilderListener.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/shared/commandhistory/controller:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/shared/subprocess/agent:__subpackages__",
    ],
    deps = [
        # Do NOT add dependencies here because this library needs to be loaded
        # by Java bootstrap class loader to instrument classes in the java.lang
        # package, and many classes like protobuf-generated classes can not be
        # loaded by Java bootstrap class loader.
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
