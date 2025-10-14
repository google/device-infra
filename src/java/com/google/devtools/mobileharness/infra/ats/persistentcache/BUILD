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

load("@rules_java//java:defs.bzl", "java_binary")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/javatests/com/google/devtools/mobileharness/shared/size:__subpackages__",
        "//third_party/py/multitest_transport:__subpackages__",
    ],
)

java_binary(
    name = "cache_manager_server",
    main_class = "com.google.devtools.mobileharness.shared.util.cache.persistent.manager.CacheManagerServer",
    runtime_deps = ["//src/java/com/google/devtools/mobileharness/shared/util/cache/persistent/manager:cache_manager_server_lib"],
)
