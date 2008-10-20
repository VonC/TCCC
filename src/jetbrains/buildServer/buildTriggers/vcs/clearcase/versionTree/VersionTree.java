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

import com.intellij.util.PatternUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.vcs.VcsException;

public class VersionTree {
  private final List<Branch> myTopBranches = new ArrayList<Branch>();
  //private final List<Version> myLeaves = new ArrayList<Version>();


  public List<Branch> getTopBranches() {
    return myTopBranches;
  }

  public void addVersion(String version) throws VcsException {
    
    if (version.contains("CHECKEDOUT view ")) return;
    
    List<String> branches = new ArrayList<String>();
    int intVersion = -1;

    final String[] versions = version.split(PatternUtil.convertToRegex(File.separator));

    if (versions.length == 0) return;

    String lastVers = versions[versions.length - 1];

    if (lastVers.startsWith("CHECKEDOUT view ")) {
      return;
    }


    for (int i = 0; i < versions.length - 1; i++) {
      String s = versions[i];
      branches.add(s);
    }

    final List<String> comments = new ArrayList<String>();

    if (lastVers.contains("(")) {

      final int commentBegin = lastVers.indexOf('(');
      final int commentEnd = lastVers.indexOf(')');
      if (commentEnd >= 0) {
        String[] strings = lastVers.substring(commentBegin + 1, commentEnd).split(",");
        for (String comment : strings) {
          if (comment.length() > 0) {
            comments.add(comment.trim());
          }
        }
      }
      lastVers = lastVers.substring(0, commentBegin).trim();
    }

    try {
      intVersion = Integer.parseInt(lastVers);
    } catch (NumberFormatException e) {
      branches.add(lastVers);
    }

    Branch currentBranch = null;
    for (String branch : branches) {
      currentBranch = addBranchToLevel(currentBranch, branch);
    }

    if (currentBranch != null && intVersion != -1) {
      currentBranch.addVersion(intVersion, comments);
    }

  }

  private Branch addBranchToLevel(final Branch currentBranch, final String branchName) throws VcsException {
    if (currentBranch == null) {
      for (Branch branch : myTopBranches) {
        if (branch.getName().equals(branchName)) {
          return branch;
        }
      }
      final Branch result = new Branch(null, branchName);
      myTopBranches.add(result);
      return result;
    } else {
      Version version = currentBranch.getFirstVersion();
      Version last = null;
      while (version != null) {
        last = version;
        final List<Branch> inhBranches = version.getInheritedBranches();
        for (Branch branch : inhBranches) {
          if (branch.getName().equals(branchName)) {
            return branch;
          }
        }
        version = version.getNextVersion();
      }

      if (last != null) {
        final Branch result = new Branch(last, branchName);
        last.addInheritedBranch(result);
        return result;
      } else {
        throw new VcsException("Cannot add branch " + branchName + " to level " + currentBranch);
      }
    }

  }

  public Version getLastVersion(String branchName, List<String> baselines) {

    List<Version> leaves = collectLeaves();

    for (Version leaf : leaves) {
      if (leaf.getParentBranch().getName().equals(branchName)) {
        return getLastVersionForLeaf(leaf);
      }
    }

    if (!baselines.isEmpty()) {
      for (String baseline : baselines) {
        for (Version leaf : leaves) {
          if (leaf.containsComment(baseline)) {
            return getLastVersionForLeaf(leaf);
          }
        }
      }

    }


    return null;
  }

  private Version getLastVersionForLeaf(final Version leaf) {
    if (leaf.getVersion() == 0) {
      return leaf.getParentBranch().getParentVersion();
    } else {
      return leaf;
    }
  }

  private List<Version> collectLeaves() {
    final ArrayList<Version> result = new ArrayList<Version>();
    for (Branch branch : myTopBranches) {
      collectLeaves(branch, result);
    }
    return result;
  }

  private void collectLeaves(final Branch branch, final ArrayList<Version> result) {
    Version version = branch.getFirstVersion();
    while (version != null) {
      List<Branch> subBranches = version.getInheritedBranches();
      for (Branch subBranch : subBranches) {
        collectLeaves(subBranch, result);
      }
      Version nextVersion = version.getNextVersion();
      if (nextVersion == null) {
        result.add(version);
        break;
      } else {
        version = nextVersion;
      }

    }
  }

  public void pruneBranch(final String objectVersion) {
    Version versionToPruneFrom = findVersionByPath(objectVersion);
    if (versionToPruneFrom != null) {
      if (versionToPruneFrom.getVersion() == 0) {
        final Branch parentBranch = versionToPruneFrom.getParentBranch();
        parentBranch.getParentVersion().pruneInheritedBranch(parentBranch);
      } else {
        versionToPruneFrom.getPrevVersion().setNextVersion(null);
      }

    }
  }

  public void pruneBranchAfter(final String objectVersion) {
    Version versionToPruneFrom = findVersionByPath(objectVersion);
    if (versionToPruneFrom != null) {
      pruneBranchAfter(versionToPruneFrom, true);
    }
  }

  public void pruneBranchAfter(final Version version, boolean includeSubBranches) {
    version.setNextVersion(null);
    if (includeSubBranches) {
      version.removeAllInheritedBranches();
    }

  }


  public Version findVersionByPath(final String version) {
    List<String> branches = new ArrayList<String>();
    int intVersion = -1;

    final String[] versions = version.split(PatternUtil.convertToRegex(File.separator));
    for (int i = 0; i < versions.length - 1; i++) {
      String s = versions[i];
      if (s.length() > 0) {
        branches.add(s);
      }
    }

    String lastVers = versions[versions.length - 1];

    if (lastVers.contains("(")) {
      lastVers = lastVers.substring(0, lastVers.indexOf('(')).trim();
    }

    try {
      intVersion = Integer.parseInt(lastVers);
    } catch (NumberFormatException e) {
      return null;
    }

    Branch currentBranch = null;
    for (String branch : branches) {
      if (currentBranch == null) {
        currentBranch = findRootByName(branch);
      } else {
        currentBranch = currentBranch.findSubBranchByName(branch);
      }

      if (currentBranch == null) return null;
    }

    return currentBranch.findVersionByNum(intVersion);
  }

  private Branch findRootByName(final String branch) {
    for (Branch topBranch : myTopBranches) {
      if (branch.equals(topBranch.getName())) return topBranch;
    }
    return null;
  }

  public Version findVersionWithComment(final String comment) {
    for (Branch branch : myTopBranches) {
      Version found = branch.findVersionWithComment(comment);
      if (found != null) return found;
    }

    return null;
  }

  public boolean isBefore(final Version versionByPath, final Version lastVersion) {
    Version current = lastVersion;
    while (current != null) {
      if (current == versionByPath) return true;
      if (current.getPrevVersion() == null) {
        final Branch parentBranch = current.getParentBranch();
          if (parentBranch == null) {
            return false;
          } else {
            current = parentBranch.getParentVersion();
          }
        } else {
        current = current.getPrevVersion();
        }
      }

    return false;
  }

  public List<Version> getAllLastVersions() {
    final ArrayList<Version> result = new ArrayList<Version>();
    for (Branch topBranch : myTopBranches) {
      topBranch.addAllLastVersions(result);
    }
    return result;
  }
}


