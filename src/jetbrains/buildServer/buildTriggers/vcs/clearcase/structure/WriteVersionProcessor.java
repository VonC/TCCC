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

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.ClearCaseConnection;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.VersionProcessor;
import jetbrains.buildServer.util.TCStreamUtil;
import jetbrains.buildServer.vcs.VcsException;


class WriteVersionProcessor implements VersionProcessor {
  private final DataOutputStream myOutputStream;

  public WriteVersionProcessor(final DataOutputStream outputStream) {
    myOutputStream = outputStream;
  }

  public void processFile(final String fileFullPath,
                          final String relPath,
                          final String pname,
                          final String version,
                          final ClearCaseConnection clearCaseConnection, final boolean text, final boolean executable) throws VcsException {
    writeFile(version, new File(relPath).getName(), text, executable);
  }

  public void writeFile(final String version, final String fileName, final boolean text, final boolean executable) throws VcsException {
    String versionWithMode = createVersionWithMode(version, text, executable);
    try {
      myOutputStream.writeByte(CacheElement.FILE_TYPE);      
      TCStreamUtil.writeString(myOutputStream, fileName);
      TCStreamUtil.writeString(myOutputStream, versionWithMode);
    } catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private String createVersionWithMode(final String version, final boolean text, final boolean executable) {
    if (!text && !executable) {
      return version;
    }
    if (text && executable) {
      return version + "|" + "tx";
    }
    else if (text) {
      return version + "|" + "t";
    }
    return version + "|" + "x";
  }

  public void processDirectory(final String fileFullPath,
                               final String relPath,
                               final String pname,
                               final String version,
                               final ClearCaseConnection clearCaseConnection) throws VcsException {
    writeDirOpen(version, new File(relPath).getName());
  }

  public void writeDirOpen(final String version, final String name) throws VcsException {
    try {
      myOutputStream.writeByte(CacheElement.DIR_OPEN_TYPE);
      
      TCStreamUtil.writeString(myOutputStream, name);
      TCStreamUtil.writeString(myOutputStream, version);
    } catch (IOException e) {
      throw new VcsException(e);
    }
  }

  public void finishProcessingDirectory() throws VcsException {
    writeDirClose();
  }

  public void writeDirClose() throws VcsException {
    try {
      myOutputStream.writeByte(CacheElement.DIR_CLOSE_TYPE);
    } catch (IOException e) {
      throw new VcsException(e);
    }
  }
}
