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

"""
Macros for generating OLC server java binaries.
"""

def olc_server_binary(name, extra_runtime_deps, visibility = None):
    """Macro for generating OLC server java binaries.

    Args:
      name: java_binary.name
      extra_runtime_deps: extra runtime deps including builtin session plugins
        and supported OmniLab drivers/decorators/devices
      visibility: java_binary.visibility
    """
    native.java_binary(
        name = name,
        main_class = "com.google.devtools.mobileharness.infra.client.longrunningservice.OlcServer",
        visibility = visibility,
        runtime_deps = [
            "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:olc_server",
        ] + extra_runtime_deps,
    )
