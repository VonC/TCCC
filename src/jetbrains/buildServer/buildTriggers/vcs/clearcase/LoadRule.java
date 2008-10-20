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

import java.io.File;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class LoadRule {
  @NonNls private static final String ELEMENT_FILE = "element -file";
  @NonNls private static final String ELEMENT_DIRECTORY = "element -directory";
  @NonNls private static final String ELEMENT = "element";
  @NonNls private static final String COMMENT_PREFIX = "#";
  @NonNls private static final String LATEST = "/LATEST";

  enum ScopeType {
    ANY(true, true), FILE(true, false), DIRECTORY(false, true);
    
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
  
  private final ScopeType myScopeType;
  private final Pattern myScopePattern;
  private final Pattern myVersionSelector;

  private LoadRule(final ScopeType scopeType, final Pattern scopePattern, final Pattern versionSelector) {
    myScopeType = scopeType;
    myScopePattern = scopePattern;
    myVersionSelector = versionSelector;
  }

  @Nullable
  public static LoadRule createRule(String pattern) {
    if (pattern.startsWith(COMMENT_PREFIX)) return null;
    if (pattern.trim().length() == 0) return null;
    final ScopeType scope;
    if (pattern.startsWith(ELEMENT_FILE)) {
      scope = ScopeType.FILE;
      pattern = pattern.substring(ELEMENT_FILE.length()).trim();
    }
    else if (pattern.startsWith(ELEMENT_DIRECTORY)) {
      scope = ScopeType.DIRECTORY;
      pattern = pattern.substring(ELEMENT_DIRECTORY.length()).trim();
    }
    else if (pattern.startsWith(ELEMENT)) {
      scope = ScopeType.ANY;
      pattern = pattern.substring(ELEMENT.length()).trim();
    }
    else {
      return null;
    }

    final String[] strings = pattern.split(" ");
    if (strings.length < 2) return null;
    
    String scopePattern = strings[0];
    String versionPattern = strings[1];
    
    return new LoadRule(scope, 
                        createPattern(scopePattern),
                        createVersionPattern(versionPattern));

  }

  public static Pattern createPattern(final String scopePattern) {
    return Pattern.compile(escapeBackSlash(normalizeFileSeparators(createCommonPattern(scopePattern))));
  }

  private static String escapeBackSlash(final String s) {
    return s.replace("\\","\\\\");
  }

  private static String normalizeFileSeparators(final String result) {
    return result.replace('/', File.separatorChar);
  }

  private static String createCommonPattern(final String scopePattern) {
    String result = scopePattern;
    result = result.replace("*", ".*");
    result = result.replace("?", ".");

    if (!result.startsWith("/") && !result.startsWith(".*") && !result.startsWith(".../")) {
      result = ".*/" + result;
    }

    result = result.replace("/...", "[[\\][^\\]+]*");
    result = result.replace(".../", "[[^\\]+[\\]]*");
    return result;
  }

  public static Pattern createVersionPattern(final String scopePattern) {
    String result = createCommonPattern(scopePattern);
    if (result.toUpperCase().endsWith(LATEST)) {
      result = result.substring(0, result.length() - LATEST.length()) + "/[^/]*";
    }    
    return Pattern.compile(escapeBackSlash(normalizeFileSeparators(result)));
  }
  
  public boolean matches(String elemPath, String version, final boolean isFile) {
    if (!myScopeType.matches(isFile)) return false;
    if (!myScopePattern.matcher(elemPath).matches()) return false;
    return myVersionSelector.matcher(version).matches();
  }
}
