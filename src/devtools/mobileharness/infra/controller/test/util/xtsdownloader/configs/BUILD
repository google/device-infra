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
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util/xtsdownloader:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
    ],
)

filegroup(
    name = "config_proto_files",
    srcs = ["module_info_map.textpb"],
)

# This is for Tradefed CTS modules only.
filegroup(
    name = "cts_list",
    srcs = ["cts_list.txt"],
)

filegroup(
    name = "mcts_list",
    srcs = ["mcts_list.txt"],
)
