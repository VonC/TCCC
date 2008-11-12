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

package jetbrains.buildServer.buildTriggers.vcs.clearcase.configSpec;

import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.Version;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.VersionTree;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;
import java.util.List;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

public class ConfigSpecImpl implements ConfigSpec {
  private final List<ConfigSpecLoadRule> myLoadRules;
  private final List<ConfigSpecStandardRule> myStandardRules;

  public ConfigSpecImpl(final List<ConfigSpecLoadRule> loadRules, final List<ConfigSpecStandardRule> standardRules) {
    myLoadRules = loadRules;
    myStandardRules = standardRules;
  }

  @Nullable
  public Version getCurrentVersion(final String fullFileName, final VersionTree versionTree, final boolean isFile)
    throws IOException, VcsException {
    final Version version = doGetCurrentVersion(fullFileName, versionTree, isFile);

    if (version == null) {
      Loggers.VCS.info("ClearCase: element \"" + fullFileName + "\" ignored, last version not found;");
    }

    return version;
  }

  @Nullable
  private Version doGetCurrentVersion(final String fullFileName, final VersionTree versionTree, final boolean isFile)
    throws IOException, VcsException {
    if (!isUnderLoadRules(fullFileName)) {
      return null;
    }

    for (ConfigSpecStandardRule standardRule : myStandardRules) {
      if (standardRule.matchesPath(fullFileName, isFile)) {
        final Version version = standardRule.findVersion(versionTree, fullFileName);
        if (version != null) {
          return version;
        }
      }
    }

    return null;
  }

  private boolean isUnderLoadRules(final String fullFileName) throws IOException {
    for (ConfigSpecLoadRule loadRule : myLoadRules) {
      if (loadRule.isUnderLoadRule(fullFileName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ConfigSpecImpl)) return false;

    final ConfigSpecImpl that = (ConfigSpecImpl)o;

    if (!myLoadRules.equals(that.myLoadRules)) return false;
    if (!myStandardRules.equals(that.myStandardRules)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLoadRules.hashCode();
    result = 31 * result + myStandardRules.hashCode();
    return result;
  }
}
