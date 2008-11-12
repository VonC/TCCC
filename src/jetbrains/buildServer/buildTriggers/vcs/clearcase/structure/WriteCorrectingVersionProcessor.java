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

package jetbrains.buildServer.buildTriggers.vcs.clearcase.structure;

import com.intellij.util.containers.HashMap;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.*;
import jetbrains.buildServer.vcs.VcsException;


class WriteCorrectingVersionProcessor implements VersionProcessor {
  private final Map<String, ChangedElementInfo> myChangedElements = new HashMap<String, ChangedElementInfo>();
  private final WriteVersionProcessor myWriteProcessor;
  private final Stack<String> myIgnoreStack;

  public WriteCorrectingVersionProcessor(final List<ChangedElementInfo> changedElements,
                                         final WriteVersionProcessor writeProcessor) {
    for (ChangedElementInfo changedElement : changedElements) {
      ChangedElementInfo prev = myChangedElements.get(changedElement.myRelativePath);
      if (prev == null) {
        myChangedElements.put(changedElement.myRelativePath, changedElement);
      }
      else if (changedElement.myChangeType != ChangedElementInfo.ChangeType.DELETED_VERSION) {
        ChangedElementInfo latest = findLatest(prev, changedElement);
        if (latest.myChangeType != ChangedElementInfo.ChangeType.DELETED_DIR) {
          for (ChangedElementInfo added : changedElement.getAddedElements()) {
            latest.addAddedElement(added);
          }
        }
        myChangedElements.put(latest.myRelativePath, latest);
      }
      else {
        myChangedElements.put(changedElement.myRelativePath, changedElement);
      }
    }
    myWriteProcessor = writeProcessor;
    myIgnoreStack = new Stack<String>();
  }

  private ChangedElementInfo findLatest(final ChangedElementInfo elem1, final ChangedElementInfo elem2) {
    int vers1 = CCParseUtil.getVersionInt(elem1.myVersion);
    int vers2 = CCParseUtil.getVersionInt(elem2.myVersion);
    
    return vers1 > vers2 ? elem1 : elem2;
  }

  public void processFile(final String fileFullPath,
                          final String relPath,
                          final String pname,
                          final String version,
                          final ClearCaseConnection clearCaseConnection, final boolean text, final boolean executable) throws VcsException {
    if (myIgnoreStack.isEmpty()) {
      ChangedElementInfo changedElement = myChangedElements.get(relPath);
      if (changedElement == null) {
        myWriteProcessor.writeFile(version, new File(relPath).getName(), text, executable);
      }
      else if (changedElement.myChangeType == ChangedElementInfo.ChangeType.DELETED_FILE) {
        //ignore
      }
      else if (changedElement.myChangeType == ChangedElementInfo.ChangeType.CHANGED_FILE) {
        myWriteProcessor.writeFile(changedElement.myVersion, new File(relPath).getName(), text, executable);
      }
      else if (changedElement.myChangeType == ChangedElementInfo.ChangeType.DELETED_VERSION) {
        final String lastVersion = clearCaseConnection.getLastVersion(pname, true).getWholeName();
        myWriteProcessor.writeFile(lastVersion, new File(relPath).getName(), text, executable);
      }
    }
  }

  public void processDirectory(final String fileFullPath,
                               final String relPath,
                               final String pname,
                               final String version,
                               final ClearCaseConnection clearCaseConnection) throws VcsException {
    if (!myIgnoreStack.isEmpty()) {
      myIgnoreStack.push(relPath);
      return;
    }
    ChangedElementInfo changedElement = myChangedElements.get(relPath);
    if (changedElement == null) {
      myWriteProcessor.writeDirOpen(version, new File(relPath).getName());
    }
    else if (changedElement.myChangeType == ChangedElementInfo.ChangeType.DELETED_DIR) {
      myIgnoreStack.push(changedElement.myRelativePath);
    }
    else if (changedElement.myChangeType == ChangedElementInfo.ChangeType.CHANGED_DIR) {
      myWriteProcessor.writeDirOpen(changedElement.myVersion, new File(relPath).getName());
      processAddedElements(changedElement, myWriteProcessor, clearCaseConnection, fileFullPath);
    }
  }

  private void processAddedElements(final ChangedElementInfo changedElement,
                                    final WriteVersionProcessor writeProcessor,
                                    final ClearCaseConnection clearCaseConnection, final String parentDirFullPath)
    throws VcsException {
    final String parentPathWithNewVersion = getParentWithNewVersion(parentDirFullPath, changedElement, clearCaseConnection);

    for (ChangedElementInfo addedElem : changedElement.getAddedElements()) {

      if (addedElem.myChangeType == ChangedElementInfo.ChangeType.ADDED_FILE) {
        final String fileName = new File(addedElem.myRelativePath).getName();
        final ClearCaseFileAttr attr = clearCaseConnection.loadFileAttr(parentPathWithNewVersion + File.separator + fileName + CCParseUtil.CC_VERSION_SEPARATOR);
        writeProcessor.writeFile(addedElem.myVersion, fileName, attr.isIsText(), attr.isIsExecutable());
      }
      else if (addedElem.myChangeType == ChangedElementInfo.ChangeType.ADDED_DIR) {
        writeProcessor.writeDirOpen(addedElem.myVersion, new File(addedElem.myRelativePath).getName());
        try {
          final String addedDirName = new File(addedElem.myRelativePath).getName();
          final String addedFileElementFullPath =
            parentPathWithNewVersion + File.separator + addedDirName + addedElem.myVersion;
          processAddedElements(addedElem, writeProcessor, clearCaseConnection, addedFileElementFullPath);
        } finally {
          writeProcessor.writeDirClose();
        }
        
      }
    }
  }

  private String getParentWithNewVersion(final String parentDirFullPath,
                                         final ChangedElementInfo changedElement,
                                         final ClearCaseConnection clearCaseConnection) {
    return CCPathElement.replaceLastVersionAndReturnFullPathWithVersions(parentDirFullPath, clearCaseConnection.getViewName(), changedElement.myVersion);    
  }

  public void finishProcessingDirectory() throws VcsException {
    if (!myIgnoreStack.isEmpty()) {
      myIgnoreStack.pop();
    }        
    else {
      myWriteProcessor.writeDirClose();
    }
  }
}
