# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Util to convert RpcError to an ExceptionDetail proto."""
import typing
from typing import Optional, Union
import zlib

import grpc

from com_google_deviceinfra.src.devtools.common.metrics.stability.model.proto import exception_pb2
from com_google_deviceinfra.src.devtools.common.metrics.stability.rpc.proto import rpc_error_payload_pb2

_EXCEPTION_KEY = '__crpc_mse_300713958-bin'


def to_exception_detail(
    e: Union[grpc.RpcError, grpc.Call]
) -> Optional[exception_pb2.ExceptionDetail]:
  """Convert RpcError to an ExceptionDetail proto."""
  error_info = None
  if isinstance(e, grpc.Call):
    typing.cast(grpc.Call, e)
    for metadata in e.trailing_metadata():
      if metadata.key == _EXCEPTION_KEY:
        error_info = rpc_error_payload_pb2.RpcErrorPayload.FromString(
            metadata.value
        )
    if error_info:
      exception_detail = exception_pb2.ExceptionDetail.FromString(
          zlib.decompress(
              error_info.rpc_error.compressed_exception_detail.compressed_data
          )
      )
      return exception_detail
  return None
