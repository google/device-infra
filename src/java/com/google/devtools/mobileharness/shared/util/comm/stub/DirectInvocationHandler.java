/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.shared.util.comm.stub;

import io.grpc.StatusRuntimeException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Invokes the method on a underlying stub.
 *
 * <p>This can be used to create a proxy of a stub interface. For example, creates an interface
 * FooGrpcStub.BlockingInterface that has the same API as the FooGrpc.FooBlockingStub. Then we can
 * create a proxy of FooGrpcStub.BlockingInterface from a FooGrpc.FooBlockingStub.
 *
 * <p>FooGrpcStub.BlockingInterface fooGrpcStub = Reflection.newProxy(
 * FooGrpcStub.BlockingInterface.class, new
 * DirectInvocationHandler(FooGrpc.newBlockingStub(channel)));
 *
 * <p>This is a workaround for the grpc stub interface. The FooGrpc.FooBlockingStub automatically
 * generated from the proto file is a final class and can't be proxied.
 */
public final class DirectInvocationHandler<T> implements InvocationHandler {

  private final T stub;

  public DirectInvocationHandler(T stub) {
    this.stub = stub;
  }

  /**
   * Invokes the method on the stub.
   *
   * @throws IllegalArgumentException if the method is not valid method of the stub.
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    try {
      Method stubMethod = stub.getClass().getMethod(method.getName(), method.getParameterTypes());
      return stubMethod.invoke(stub, args);
    } catch (ReflectiveOperationException e) {
      if (e instanceof InvocationTargetException
          && e.getCause() instanceof StatusRuntimeException) {
        throw (StatusRuntimeException) e.getCause();
      }
      throw new IllegalArgumentException(
          String.format(
              "Invalid method %s for the class %s", method.getName(), stub.getClass().getName()),
          e);
    }
  }
}
