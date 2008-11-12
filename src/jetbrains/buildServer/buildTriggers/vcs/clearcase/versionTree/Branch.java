/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class Branch {
  private final String myName;
  private final Version myParentVersion;
  
  private Version myFirstVersion;


  public Branch(final Version parentVersion, final String name) {
    myParentVersion = parentVersion;
    myName = name;
  }


  public String getName() {
    return myName;
  }


  public String toString() {
    return myName;
  }

  public Version getFirstVersion() {
    return myFirstVersion;
  }

  public Version addVersion(final int intVersion, final List<String> comment) {
    Version last = getLastVersion();
    if (last == null) {
      myFirstVersion =  new Version(null, this, intVersion, comment);
      return myFirstVersion;
    }
    else {
      return last.addNext(intVersion, comment);
    }
  }

  @Nullable
  public Version getLastVersion() {
    if (myFirstVersion == null) {
      return null;
    }
    
    Version result = myFirstVersion;
    while (result.getNextVersion() != null) {
      result = result.getNextVersion();
    }
    
    return result;
  }


  @Nullable
  public Version findVersionByNumber(final int versionNumber) {
    if (myFirstVersion == null) {
      return null;
    }

    Version version = myFirstVersion;
    do {
      if (version.getVersion() == versionNumber) {
        return version;
      }
      version = version.getNextVersion();
    } while (version != null);

    return null;
  }

  public Version getParentVersion() {
    return myParentVersion;
  }

  @Nullable
  Branch findSubBranchByName(final String brancheName) {
    Version version = getFirstVersion();
    while (version != null) {
      Branch found = version.getInheritedBranchByName(brancheName);
      if (found != null) return found;
      version = version.getNextVersion();
    }
    return null;
  }

  @Nullable
  Version findVersionByNum(final int intVersion) {
    Version version = getFirstVersion();
    while (version != null) {
      if (intVersion == version.getVersion()) return version;
      version = version.getNextVersion();
    }
    return null;

  }

  @Nullable
  public Version findVersionWithComment(final String comment) {
    return findVersionWithComment(comment, true);
  }

  @Nullable
  public Version findVersionWithComment(final String comment, final boolean findInInheritedBranches) {
    Version version = getFirstVersion();
    while (version != null) {
      if (version.containsComment(comment)) return version;
      if (findInInheritedBranches) {
        List<Branch> branches = version.getInheritedBranches();
        for (Branch branche : branches) {
          Version found = branche.findVersionWithComment(comment);
          if (found != null) return found;
        }
      }
      version = version.getNextVersion();
    }

    return null;
  }

  public void addAllLastVersions(final ArrayList<Version> result) {
    Version current = myFirstVersion;
    while (current != null) {
      if (current.getNextVersion() == null) {
        result.add(current);
      }

      for (Branch inherited : current.getInheritedBranches()) {
        inherited.addAllLastVersions(result);
      }

      current = current.getNextVersion();
    }
  }
}
