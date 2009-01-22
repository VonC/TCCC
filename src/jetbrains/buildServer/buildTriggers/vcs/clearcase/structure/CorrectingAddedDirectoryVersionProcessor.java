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

package jetbrains.buildServer.buildTriggers.vcs.clearcase.structure;

import com.intellij.util.containers.Stack;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.ClearCaseConnection;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.VersionProcessor;
import jetbrains.buildServer.vcs.VcsException;


class CorrectingAddedDirectoryVersionProcessor implements VersionProcessor {
  
  private final Stack<ChangedElementInfo> myCurrentDirs = new Stack<ChangedElementInfo>();
  
  public CorrectingAddedDirectoryVersionProcessor(final ChangedElementInfo topDir) {
    myCurrentDirs.push(topDir);
  }

  public void processFile(final String fileFullPath,
                          final String relPath,
                          final String pname,
                          final String version,
                          final ClearCaseConnection clearCaseConnection, final boolean text, final boolean executable) throws
                                                                         VcsException {
    myCurrentDirs.peek().addAddedElement(new ChangedElementInfo(relPath, version, ChangedElementInfo.ChangeType.ADDED_FILE));      
  }

  public void processDirectory(final String fileFullPath,
                               final String relPath,
                               final String pname,
                               final String version,
                               final ClearCaseConnection clearCaseConnection) throws VcsException {
    ChangedElementInfo newDir = new ChangedElementInfo(relPath, version, ChangedElementInfo.ChangeType.ADDED_DIR);
    myCurrentDirs.peek().addAddedElement(newDir);
    myCurrentDirs.push(newDir);
  }
  
  public static void processAddedDirectory(final String relPath,
                                     final String fullPath,
                                     final String version,
                                     final ClearCaseConnection connection,
                                     final ChangedElementInfo parentChangedDir) throws VcsException {
    final ChangedElementInfo newParentDir = new ChangedElementInfo(relPath, version, ChangedElementInfo.ChangeType.ADDED_DIR);
    parentChangedDir.addAddedElement(newParentDir);
    connection.processAllVersions(fullPath, relPath, new CorrectingAddedDirectoryVersionProcessor(newParentDir));
  }

  

  public void finishProcessingDirectory() {
    myCurrentDirs.pop();
  }
}
