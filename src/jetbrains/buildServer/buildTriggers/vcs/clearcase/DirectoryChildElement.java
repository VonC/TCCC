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

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NonNls;


public class DirectoryChildElement {
  
  @NonNls private static final String DIRECTORY_ELEMENT = "directory element";
  @NonNls private static final String FILE_ELEMENT = "file element";
  @NonNls private static final String NOT_LOADED = "[not loaded]";
  


  public String getFullPath() {
    return myFullPath;
  }

  public String getStringVersion() {
    return myStringVersion;
  }

  public enum Type {
    FILE, DIRECTORY
  }
  
  private final Type myType;
  private final String myPath;
  private final int myVersion;
  private final String myFullPath;
  private final String myStringVersion;
  private final String myPathWithoutVersion;

  public static DirectoryChildElement readFromLSFormat(String line, ClearCaseConnection connection) throws VcsException {
    final Type type;
    String currentPath = line;
    if (currentPath.startsWith(DIRECTORY_ELEMENT)) {
      currentPath = currentPath.substring(DIRECTORY_ELEMENT.length()).trim();
      type = Type.DIRECTORY;
    }
    else if (currentPath.startsWith(FILE_ELEMENT)){
      type = Type.FILE;
      currentPath = currentPath.substring(FILE_ELEMENT.length()).trim();
    }
    else {
      type = null;
    }
          
    if (currentPath.endsWith(NOT_LOADED)) {
      currentPath = currentPath.substring(0, currentPath.length() - NOT_LOADED.length()).trim();
    }
    
    if (currentPath.endsWith(CCParseUtil.CC_VERSION_SEPARATOR)) {
      currentPath = currentPath.substring(0, currentPath.length() - CCParseUtil.CC_VERSION_SEPARATOR.length()).trim();
    }
    
    if (type != null) {
      return connection.getLastVersionElement(currentPath, type);      
    }
    return null;
  }


  public DirectoryChildElement(final Type type,
                               final String path,
                               final int version,
                               String fullPath,
                               String stringVersion,
                               final String pathWithoutVersion) {
    myType = type;
    myPath = path;
    myVersion = version;
    myFullPath = fullPath;
    myStringVersion = stringVersion;
    myPathWithoutVersion = pathWithoutVersion;
  }

  public String getPathWithoutVersion() {
    return myPathWithoutVersion;
  }

  public Type getType() {
    return myType;
  }

  public String getPath() {
    return myPath;
  }

  public int getVersion() {
    return myVersion;
  }


  public String toString() {
    return myPath;
  }
}
