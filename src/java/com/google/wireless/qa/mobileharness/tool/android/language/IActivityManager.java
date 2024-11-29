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

package android.app;

import android.content.res.Configuration;
import android.os.RemoteException;

/** Fake class for "extract" internal IActivityManager out from SDK. */
public interface IActivityManager {

  public abstract Configuration getConfiguration() throws RemoteException;

  public abstract void updateConfiguration(Configuration configuration) throws RemoteException;

  public abstract void updatePersistentConfiguration(Configuration configuration)
      throws RemoteException;
}
