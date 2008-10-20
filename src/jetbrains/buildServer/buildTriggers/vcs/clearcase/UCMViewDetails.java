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

package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.Version;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.VersionTree;
import jetbrains.buildServer.vcs.VcsException;

public class UCMViewDetails implements ViewDetails {
  private final String myStreamName;
  private final List<String> myBaselines = new ArrayList<String>();
  private final List<File> myLoadRules = new ArrayList<File>();
  private final File myViewRoot;

  public UCMViewDetails(final String streamName, File viewRoot) {
    myStreamName = streamName;
    myViewRoot = viewRoot;
  }
  
  public void addBaseline(String baseline) {
    myBaselines.add(baseline);
  }

  public String getStreamName() {
    return myStreamName;
  }

  public List<String> getBaselines() {
    return myBaselines;
  }

  private void cutBranchAfterCurrent(final VersionTree versionTree) throws IOException, VcsException {
    if (!getBaselines().isEmpty()) {
      final List<String> baselines = getBaselines();
      for (String baseline : baselines) {
        Version version = versionTree.findVersionWithComment(baseline);
        if (version != null) {
          versionTree.pruneBranchAfter(version, false);
        }
      }
    }
  }
  
  public Version getLastVersion(final String elementPath, final VersionTree tree) throws IOException, VcsException {
    if (!isUnderLoadRule(elementPath)) {
      return null;
    }
    cutBranchAfterCurrent(tree);
    return tree.getLastVersion(getStreamName(), getBaselines());
  }

  private boolean isUnderLoadRule(final String elementPath) throws IOException {
    File elementFile = new File(elementPath);
    for (File loadRule : myLoadRules) {
      if (FileUtil.isAncestor(elementFile, loadRule, false)) {
        return true;
      }
      if (FileUtil.isAncestor(loadRule, elementFile, false)) {
        return true;
      }
      
    }
    return false;
  }

  public void addRule(final String string) {
    myLoadRules.add(new File(myViewRoot, string));
  }
}
