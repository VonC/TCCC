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

import jetbrains.buildServer.buildTriggers.vcs.clearcase.CCPathElement;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Pattern;

public class ConfigSpecStandardRule {
  private final ScopeType myScopeType;
  protected final Pattern myScopePattern;
  protected final Pattern myBranchPattern;
  protected final String myVersion;


  public ConfigSpecStandardRule(final String scope, final String scopePattern, final String versionSelectorWithOptions) {
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
    if (versionSelectorWithOptions.startsWith("{")) {
      //todo
    }
    final String versionSelector = ConfigSpecParseUtil.extractFirstWord(versionSelectorWithOptions);
    getMkBranchOption(versionSelectorWithOptions.substring(versionSelector.length()).trim());
    final String normalizedVersionSelector = CCPathElement.normalizeSeparators(versionSelector.trim());
    int lastSeparatorPos = normalizedVersionSelector.lastIndexOf(File.separatorChar);
    myBranchPattern = lastSeparatorPos == -1 ? Pattern.compile(".*") : createPattern(normalizedVersionSelector.substring(0, lastSeparatorPos), true);
    myVersion = normalizedVersionSelector.substring(lastSeparatorPos + 1);
    if (myVersion.startsWith("{")) {
      //todo
    }
  }

  @Nullable
  private String getMkBranchOption(final String s) {
    boolean wordShouldBeReturned = false;
    String ss = s;
    while (ss.length() > 0) {
      final String word = ConfigSpecParseUtil.extractFirstWord(ss), trimmedWord = word.trim();
      if (wordShouldBeReturned) return trimmedWord;
      ss = ss.substring(word.length()).trim();
      if (ConfigSpecRuleTokens.MKBRANCH_OPTION.equalsIgnoreCase(trimmedWord)) wordShouldBeReturned = true;      
    }
    return null;
  }

  private static Pattern createPattern(final String pattern, final boolean isVersionPattern) {
    return Pattern.compile(createCommonPattern(escapeBackSlash(CCPathElement.normalizeSeparators(pattern)), isVersionPattern));
  }

  private static String escapeBackSlash(final String s) {
    return s.replace("\\","\\\\");
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

  public enum ResultType {
    MATCHES,
    DOES_NOT_MATCH,
    BRANCH_HAS_BEEN_MADE,
    BRANCH_HAS_NOT_BEEN_MADE
  }
}
