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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.Version;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.VersionTree;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;

public class BaseViewDetails implements ViewDetails{
  
  private List<LoadRule> myPatterns = new ArrayList<LoadRule>();

  public Version getLastVersion(final String elementPath, final VersionTree tree) throws IOException, VcsException {
    List<Version> versions = tree.getAllLastVersions();
    for (LoadRule pattern : myPatterns) {
      for (Version version : versions) {
        if (pattern.matches(elementPath, version.getWholeName(), true)) {
          return version;
        }
      }
      
    }

    final StringBuffer vers = new StringBuffer();
    for (Version version : versions) {
      vers.append(version.getWholeName()).append(";");
    }
    
    Loggers.VCS.info("ClearCase: element " + elementPath + " ignored, last version not found; \n" + vers.toString());
    
    return null;
  }
  
  public void addRule(String rule) {
    final LoadRule loadRule = LoadRule.createRule(rule);
    if (loadRule != null) {
      myPatterns.add(loadRule);
    }
  }
}
