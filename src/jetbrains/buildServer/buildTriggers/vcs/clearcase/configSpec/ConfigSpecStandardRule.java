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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.HashSet;
import java.util.regex.Pattern;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.Branch;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.Version;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.VersionTree;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

public class ConfigSpecStandardRule {
  private final ScopeType myScopeType;
  protected final Pattern myScopePattern;
  protected final Pattern myBranchPattern;
  protected final String myVersion;

  enum ScopeType {
    ANY(true, true),
    FILE(true, false),
    DIRECTORY(false, true);

    private final boolean myCanBeFile;

    private final boolean myCanBeDirectory;

    ScopeType(final boolean canBeFile, final boolean canBeDirectory) {
      myCanBeFile = canBeFile;
      myCanBeDirectory = canBeDirectory;
    }

    public boolean matches(final boolean file) {
      return file ? myCanBeFile : myCanBeDirectory;
    }

  }

  @Nullable
  public Version findVersion(final VersionTree versionTree, final String fullFileName) throws VcsException {
    final Collection<Branch> branches = findBranches(versionTree);
    if (branches == null) {
      return null;
    }

    if (StringUtil.isNumber(myVersion)) {
      int versionNumber = Integer.parseInt(myVersion);
      return findVersionByNumber(branches, versionNumber, fullFileName);
    }
    else {
      if (ConfigSpecRuleTokens.CHECKEDOUT.equalsIgnoreCase(myVersion)) {
        return null; //todo
      } else if (ConfigSpecRuleTokens.LATEST.equalsIgnoreCase(myVersion)) {
        return getLastVersion(branches, fullFileName);
      } else { // label
        return findVersionWithComment(branches, myVersion, fullFileName);
      }
    }
  }

  @Nullable
  private Version findVersionWithComment(final Collection<Branch> branches, final String labelName, final String fullFileName) throws VcsException {
    Collection<Version> versions = new HashSet<Version>();
    for (Branch branch : branches) {
      Version version = branch.findVersionWithComment(labelName, false);
      if (version != null) {
        versions.add(version);
      }
    }
    return processVersions(versions, fullFileName);
  }

  @Nullable
  private Version getLastVersion(final Collection<Branch> branches, final String fullFileName) throws VcsException {
    Collection<Version> versions = new HashSet<Version>();
    for (Branch branch : branches) {
      versions.add(branch.getLastVersion());
    }
    return processVersions(versions, fullFileName);
  }

  @Nullable
  private Version findVersionByNumber(final Collection<Branch> branches, final int versionNumber, final String fullFileName) throws VcsException {
    Collection<Version> versions = new HashSet<Version>();
    for (Branch branch : branches) {
      Version version = branch.findVersionByNumber(versionNumber);
      if (version != null) {
        versions.add(version);
      }
    }
    return processVersions(versions, fullFileName);
  }

  @Nullable
  private Version processVersions(final Collection<Version> versions, final String fullFileName) throws VcsException {
    if (versions.size() == 0) {
      return null;
    }
    if (versions.size() > 1) {
      throw new VcsException("Version of \"" + fullFileName + "\" is ambiguous: " + collectVersions(versions) + ".");
    }
    return versions.iterator().next();
  }

  private String collectVersions(final Collection<Version> versions) {
    StringBuilder sb = new StringBuilder("");
    for (Version version : versions) {
      sb.append(version.getWholeName()).append("; ");
    }
    if (sb.length() > 1) {
      return sb.substring(0, sb.length() - 2);
    }
    return sb.toString();
  }

  @Nullable
  private Collection<Branch> findBranches(final VersionTree versionTree) {
    Map<String, Branch> branches = versionTree.getAllBranchesWithFullNames();
    Collection<Branch> result = new ArrayList<Branch>();
    for (Map.Entry<String, Branch> entry : branches.entrySet()) {
      if (myBranchPattern.matcher(entry.getKey()).matches()) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  public ConfigSpecStandardRule(final String scope, final String scopePattern, final String versionSelector) {
    String scopeTypeName = scope.substring(0, scope.indexOf(':'));
    if (ConfigSpecRuleTokens.STANDARD_FILE.equalsIgnoreCase(scopeTypeName)) {
      myScopeType = ScopeType.FILE;
    } else if (ConfigSpecRuleTokens.STANDARD_DIRECTORY.equalsIgnoreCase(scopeTypeName)) {
      myScopeType = ScopeType.DIRECTORY;
    } else if (ConfigSpecRuleTokens.STANDARD_ELTYPE.equalsIgnoreCase(scopeTypeName)) {
      myScopeType = ScopeType.ANY; //todo
    } else {
      myScopeType = ScopeType.ANY;
    }
    myScopePattern = createPattern(scopePattern, false);
    if (versionSelector.startsWith("{")) {
      //todo
    }
    final String normalizedVersionSelector = normalizeFileSeparators(versionSelector);
    int lastSeparatorPos = normalizedVersionSelector.lastIndexOf(File.separatorChar);
    myBranchPattern = lastSeparatorPos == -1 ? Pattern.compile(".*") : createPattern(normalizedVersionSelector.substring(0, lastSeparatorPos), true);
    myVersion = normalizedVersionSelector.substring(lastSeparatorPos + 1);
    if (myVersion.startsWith("{")) {
      //todo
    }
  }

  private static Pattern createPattern(final String pattern, final boolean isVersionPattern) {
    return Pattern.compile(createCommonPattern(escapeBackSlash(normalizeFileSeparators(pattern)), isVersionPattern));
  }

  private static String escapeBackSlash(final String s) {
    return s.replace("\\","\\\\");
  }

  private static String normalizeFileSeparators(final String result) {
    return result.replace('/', File.separatorChar).replace('\\', File.separatorChar);
  }

  private static String createCommonPattern(final String pattern, final boolean isVersionPattern) {
    String result = pattern;
    result = escapeDots(result);
    result = result.replace("*", ".*");
    result = result.replace("?", ".");

    String escapedSeparator = escapeBackSlash(File.separator);

    final String s = isVersionPattern ? escapedSeparator : "/";
    if (!result.startsWith(s) && !result.startsWith(".*")) {
      result = "(.*" + escapedSeparator + ")?" + result;
    }

    result = result.replace(escapedSeparator + "...", "(" + escapedSeparator + "[^" + escapedSeparator + "]+)*");
    result = result.replace("..." + escapedSeparator, "([^" + escapedSeparator + "]+" + escapedSeparator + ")*");

    return result;
  }

  private static String escapeDots(final String string) {
    StringBuilder sb = new StringBuilder();
    int i = 0, len = string.length();
    while (i < len) {
      if (string.charAt(i) == '.') {
        if (i + 2 >= len || string.charAt(i + 1) != '.' || string.charAt(i + 2) != '.') {
          sb.append("\\.");
          i++;
        }
        else {
          sb.append("...");
          i += 3;
        }
      }
      else {
        sb.append(string.charAt(i));
        i++;
      }
    }
    return sb.toString();
  }

  public boolean matchesPath(String fullFilePath, final boolean isFile) {
    return myScopeType.matches(isFile) && myScopePattern.matcher(fullFilePath).matches();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ConfigSpecStandardRule)) return false;

    final ConfigSpecStandardRule that = (ConfigSpecStandardRule)o;

    if (!myBranchPattern.pattern().equals(that.myBranchPattern.pattern())) return false;
    if (!myScopePattern.pattern().equals(that.myScopePattern.pattern())) return false;
    if (myScopeType != that.myScopeType) return false;
    if (!myVersion.equals(that.myVersion)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myScopeType.hashCode();
    result = 31 * result + myScopePattern.pattern().hashCode();
    result = 31 * result + myBranchPattern.pattern().hashCode();
    result = 31 * result + myVersion.hashCode();
    return result;
  }
}
