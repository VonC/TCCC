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

package jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Version {
  private Version myNextVersion;
  private final Version myPrevVersion;
  private final Branch myParentBranch;
  private final List<String> myComments = new ArrayList<String>();
  
  private final int myVersion;
  private final List<Branch> myInheritedBranches = new ArrayList<Branch>();


  public String toString() {
    return String.valueOf(myVersion);
  }

  public Version(final Version prevVersion, final Branch parentBranch, final int version,
                 List<String> comment) {
    myPrevVersion = prevVersion;
    myParentBranch = parentBranch;
    myVersion = version;
    myComments.addAll(comment);
  }

  public Version(final Version version) {
    myNextVersion = version.myNextVersion;
    myPrevVersion = version.myPrevVersion;
    myParentBranch = version.myParentBranch;
    myComments.addAll(version.myComments);
    myVersion = version.myVersion;
    myInheritedBranches.addAll(version.myInheritedBranches);
  }


  public Version getPrevVersion() {
    return myPrevVersion;
  }


  public List<Branch> getInheritedBranches() {
    return myInheritedBranches;
  }
  
  public void addInheritedBranch(Branch br) {
    myInheritedBranches.add(br);
  }


  public Version getNextVersion() {
    return myNextVersion;
  }

  public Version addNext(final int intVersion, List<String> comments) {
    final Version result = new Version(this, myParentBranch, intVersion, comments);
    myNextVersion = result;
    return result;
  }


  public Branch getParentBranch() {
    return myParentBranch;
  }

  public int getVersion() {
    return myVersion;
  }

  public String getWholeName() {
    final StringBuffer buffer = new StringBuffer();
    appendBranchNameTo(getParentBranch(), buffer);
    buffer.append(File.separator).append(myVersion);
    return buffer.toString();
  }

  private void appendBranchNameTo(final Branch parentBranch, final StringBuffer buffer) {
    if (parentBranch.getParentVersion() != null) {
      appendBranchNameTo(parentBranch.getParentVersion().getParentBranch(), buffer);
    }
    buffer.append(File.separator).append(parentBranch.getName());
  }

  public void setNextVersion(final Version version) {
    myNextVersion = version;
  }

  public Branch getInheritedBranchByName(final String brancheName) {
    for (Branch inheritedBranche : myInheritedBranches) {
      if (brancheName.equals(inheritedBranche.getName())) return inheritedBranche;
    }
    return null;
  }

  public void pruneInheritedBranch(final Branch branch) {
    myInheritedBranches.remove(branch);
  }

  public void removeAllInheritedBranches() {
    myInheritedBranches.clear();
  }


  public boolean containsComment(String comment) {
    return myComments.contains(comment);
  }

  public boolean isPrevVersion(final Version lastVersion) {
    Version versionParent = lastVersion.getPrevVersionOrParent();
      if (versionParent != null) {
        if (versionParent.equals(this)) {
          return true;
        }
        else {
          return isPrevVersion(versionParent);
        }
      }
      else {
        return false;
      }
    }
    
  public Version getPrevVersionOrParent() {
    if (myPrevVersion != null) return myPrevVersion;
    return myParentBranch.getParentVersion();
  }
}
