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

package jetbrains.buildServer.buildTriggers.vcs.clearcase.configSpec;

import java.io.File;
import java.io.IOException;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.CCPathElement;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.ClearCaseConnection;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.Version;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.VersionTree;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

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
    final String normalizedFullFileName = CCPathElement.normalizeFileName(fullFileName);
    final Version version = doGetCurrentVersion(normalizedFullFileName, versionTree, isFile);

    if (version == null) {
      Loggers.VCS.info("ClearCase: element \"" + fullFileName + "\" ignored, last version not found;");
    }

    return version;
  }

  public boolean isVersionIsInsideView(final ClearCaseConnection connection, final List<CCPathElement> pathElements, final boolean isFile) throws VcsException, IOException {
    StringBuilder filePath = new StringBuilder("");
    StringBuilder objectPath = new StringBuilder("");

    for (int i = 0; i < pathElements.size(); i++) {
      CCPathElement pathElement = pathElements.get(i);
      filePath.append(File.separatorChar).append(pathElement.getPathElement());
      objectPath.append(File.separatorChar).append(pathElement.getPathElement());

      final String pathElementVersion = pathElement.getVersion();
      if (pathElementVersion != null) {
        final Version version = connection.findVersion(CCPathElement.removeFirstSeparatorIfNeeded(objectPath), pathElementVersion);
        if (version == null) return false;
        objectPath.append(pathElementVersion);
        if (!doIsVersionIsInsideView(CCPathElement.removeFirstSeparatorIfNeeded(filePath), version, i == pathElements.size() - 1 && isFile)) {
          return false;
        }
      }
    }

    return true;
  }

  @NotNull
  public List<ConfigSpecLoadRule> getLoadRules() {
    return myLoadRules;
  }

  private boolean doIsVersionIsInsideView(final String fullFileName, final Version version, final boolean isFile) throws VcsException, IOException {
    final String normalizedFullFileName = CCPathElement.normalizeFileName(fullFileName);
    if (!isUnderLoadRules(normalizedFullFileName)) return false;

    final Version version_copy = new Version(version);

    boolean versionTreeHasBeenChanged;
    do {
      versionTreeHasBeenChanged = false;
      for (ConfigSpecStandardRule rule : myStandardRules) {
        if (!rule.matchesPath(normalizedFullFileName, isFile)) continue;
        final ConfigSpecStandardRule.ResultType result = rule.isVersionIsInsideView(version_copy);
        if (ConfigSpecStandardRule.ResultType.BRANCH_HAS_BEEN_MADE.equals(result)) {
          versionTreeHasBeenChanged = true;
          break;
        }
        else if (ConfigSpecStandardRule.ResultType.MATCHES.equals(result)) {
          return true;
        }
      }
    } while (versionTreeHasBeenChanged);

    return false;
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

  public boolean isUnderLoadRules(final String fullFileName) throws IOException {
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
