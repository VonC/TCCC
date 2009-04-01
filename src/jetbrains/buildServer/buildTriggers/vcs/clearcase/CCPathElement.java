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

package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import com.intellij.util.PatternUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CCPathElement {
  private final String myPathElement;
  private String myVersion = null;

  private boolean myIsFromViewPath;

  private CCPathElement(final String pathElement, String fromViewPath) {
    myPathElement = pathElement;
    myIsFromViewPath = pathElement.equals(fromViewPath);
  }

  public CCPathElement(final String pathElement, final boolean isFromViewPath) {
    myPathElement = pathElement;
    myIsFromViewPath = isFromViewPath;
  }

  private void appendVersion(String version) {
    if (myVersion == null) {
      myVersion = CCParseUtil.CC_VERSION_SEPARATOR;
    }
    myVersion += File.separator + version;
  }

  public String getPathElement() {
    return myPathElement;
  }

  public String getVersion() {
    return myVersion;
  }

  public void setVersion(final String objectVersion) {
    if (objectVersion == null) {
      myVersion = null;
    } else if (objectVersion.startsWith(CCParseUtil.CC_VERSION_SEPARATOR)) {
      myVersion = objectVersion;
    } else {
      myVersion = CCParseUtil.CC_VERSION_SEPARATOR + objectVersion;
    }
  }

  public boolean isIsFromViewPath() {
    return myIsFromViewPath;
  }

  public static boolean isInsideView(String objectName, String viewPath) {
    final List<CCPathElement> pathElements = splitIntoPathElements(objectName);
    final List<String> viewPathElements = createViewPathElementList(viewPath, pathElements);

    if (viewPathElements.size() > pathElements.size()) {
      return false;
    }

    for (int i = 0; i < viewPathElements.size(); i++) {
      if (!viewPathElements.get(i).equals(pathElements.get(i).getPathElement())) {
        return false;
      }
    }

    return true;
  }

  private static List<String> createViewPathElementList(final String viewPath, final List<CCPathElement> pathElements) {
    final String regex = PatternUtil.convertToRegex(File.separator);
    final List<String> viewPathElements = new ArrayList<String>(Arrays.asList(viewPath.split(regex)));

    if (pathElements.size() > 0 && pathElements.get(0).getPathElement().length() == 0) {
      if (viewPathElements.size() > 0) {
        viewPathElements.set(0, "");
      }
    }
    return viewPathElements;
  }

  public static List<CCPathElement> splitIntoPathAntVersions(String objectName, String viewPath, final int skipAtBeginCount) {
    List<CCPathElement> result = splitIntoPathElements(objectName);

    setInViewAttributes(result, viewPath, skipAtBeginCount);

    return result;

  }

  public static List<CCPathElement> splitIntoPathElements(final String objectName) {
    List<CCPathElement> result = new ArrayList<CCPathElement>();

    final String regex = PatternUtil.convertToRegex(File.separator);
    List<String> subNames = new ArrayList<String>(Arrays.asList(objectName.split(regex)));

    boolean versionMode = false;
    for (int i = 0; i < subNames.size(); i++) {

      String currentViewPath = null;

      final String subName = subNames.get(i);
      final boolean beginOfVersion = subName.endsWith(CCParseUtil.CC_VERSION_SEPARATOR);

      if (beginOfVersion || versionMode) {
        final CCPathElement currentPair;
        if (beginOfVersion) {
          currentPair =
            new CCPathElement(subName.substring(0, subName.length() - CCParseUtil.CC_VERSION_SEPARATOR.length()), currentViewPath);
          versionMode = true;
        } else {
          currentPair = new CCPathElement(subName, currentViewPath);
        }

        result.add(currentPair);

        for (i += 1; i < subNames.size(); i++) {
          currentPair.appendVersion(subNames.get(i));
          try {
            Integer.parseInt(subNames.get(i));
            break;
          } catch (NumberFormatException e) {
            //ignore
          }
        }
      } else {
        result.add(new CCPathElement(subName, currentViewPath));
      }
    }
    return result;
  }

  private static void setInViewAttributes(List<CCPathElement> pathElements, String viewPath, int skipAtBeginCount) {
    final List<String> viewPathElements = createViewPathElementList(viewPath, pathElements);

    for (int i = 0; i < skipAtBeginCount; i++) {
      if (viewPathElements.isEmpty()) break;
      viewPathElements.remove(viewPathElements.size() - 1);
    }


    for (CCPathElement pathElement : pathElements) {

      String currentViewPath = null;

      if (viewPathElements.size() > 0) {
        currentViewPath = viewPathElements.remove(0);
      }

      pathElement.setCorrespondingViewElement(currentViewPath);

    }
  }

  private void setCorrespondingViewElement(final String currentViewPath) {
    myIsFromViewPath = myPathElement.equals(currentViewPath);
  }

  public static String createPath(final List<CCPathElement> ccPathElements, int length, boolean appentVersion) {
    StringBuffer result = new StringBuffer();
    boolean first = true;
    for (int i = 0; i < length; i++) {
      final CCPathElement element = ccPathElements.get(i);
      try {
        if (!first) {
          result.append(File.separatorChar);
        }
      } finally {
        first = false;
      }
      result.append(element.getPathElement());

      if (appentVersion && element.getVersion() != null) {
        result.append(element.getVersion());
      }
    }
    return result.toString();
  }

  private static void appendNameToBuffer(final StringBuffer result, final String subName) {
    result.append(File.separatorChar);
    result.append(subName);
  }

  public static String createRelativePathWithVersions(final List<CCPathElement> pathElementList) {
    StringBuffer result = new StringBuffer();
    for (CCPathElement pathElement : pathElementList) {
      if (!pathElement.isIsFromViewPath()) {
        if (pathElement.getVersion() == null) {
          appendNameToBuffer(result, pathElement.getPathElement());
        } else {
          appendNameToBuffer(result, pathElement.getPathElement() + pathElement.getVersion());
        }
      }
    }

    return removeFirstSeparatorIfNeeded(result.toString());
  }

  public static String removeFirstSeparatorIfNeeded(final CharSequence s) {
    return String.valueOf(s.length() > 0 ? s.subSequence(1, s.length()) : "");
  }

  public static String createPathWithoutVersions(final List<CCPathElement> pathElementList) {
    StringBuffer result = new StringBuffer();
    for (CCPathElement pathElement : pathElementList) {
      appendNameToBuffer(result, pathElement.getPathElement());
    }

    return removeFirstSeparatorIfNeeded(result.toString());
  }

  public static String replaceLastVersionAndReturnFullPathWithVersions(final String parentDirFullPath,
                                                                       final String viewName,
                                                                       final String version) {
    final List<CCPathElement> pathElements =
      splitIntoPathAntVersions(parentDirFullPath, viewName, 0);
    pathElements.get(pathElements.size() - 1).setVersion(version);
    return createPath(pathElements, pathElements.size(), true);

  }

  public static String normalizeFileName(final String fullFileName) throws VcsException {
    final String[] dirs = fullFileName.split(File.separator.replace("\\", "\\\\"));
    Stack<String> stack = new Stack<String>();

    for (String dir : dirs) {
      if (".".equals(dir)) continue;
      if ("..".equals(dir)) {
        if (stack.isEmpty()) {
          throw new VcsException("Invalid parent links balance: \"" + fullFileName + "\"");
        }
        stack.pop();
      }
      else {
        stack.push(dir);
      }
    }

    StringBuilder sb = new StringBuilder("");

    for (String dir : stack) {
      sb.append(File.separator).append(dir);
    }

    return removeFirstSeparatorIfNeeded(sb);
  }

  @NotNull
  private static String removeLastSeparatorIfNeeded(@NotNull final String path) {
    return path.endsWith(File.separator) && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
  }

  public static String normalizePath(final String path) throws VcsException {
    return normalizeFileName(removeLastSeparatorIfNeeded(normalizeSeparators(path.trim())));

  }

  public static String normalizeSeparators(String path) {
    return path.replace('/', File.separatorChar).replace('\\', File.separatorChar);
  }

  public static boolean areFilesEqual(@NotNull final File file1, @NotNull final File file2) throws VcsException {
    return normalizePath(file1.getAbsolutePath()).equals(normalizePath(file2.getAbsolutePath()));
  }
}
