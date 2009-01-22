/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.clearcase.structure;

import java.util.ArrayList;
import java.util.List;


class ChangedElementInfo {

  enum ChangeType {
    ADDED_FILE, ADDED_DIR, DELETED_FILE, DELETED_DIR, CHANGED_FILE, CHANGED_DIR, DELETED_VERSION
  }

  final String myRelativePath;
  final String myVersion;
  final ChangeType myChangeType;
  
  private final List<ChangedElementInfo> myAddedElements = new ArrayList<ChangedElementInfo>();

  ChangedElementInfo(final String relativePath,
                     final String version,
                     final ChangeType changeType) {

    myRelativePath = ".".equals(relativePath) ? "" : relativePath;
    myVersion = version;
    myChangeType = changeType;
  }
  
  public void addAddedElement(ChangedElementInfo el) {
    myAddedElements.add(el);
  }

  public List<ChangedElementInfo> getAddedElements() {
    return myAddedElements;
  }

  public String toString() {
    return myChangeType.name() + ": " + myRelativePath + ": " + myVersion;
  }
}
