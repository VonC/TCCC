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

import java.util.ArrayList;
import java.util.List;


public class VersionHolder {
  private final List<Branch> myInheritedBranches = new ArrayList<Branch>();

  public List<Branch> getInheritedBranches() {
    return myInheritedBranches;
  }

  public void addInheritedBranch(Branch br) {
    myInheritedBranches.add(br);
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
}
