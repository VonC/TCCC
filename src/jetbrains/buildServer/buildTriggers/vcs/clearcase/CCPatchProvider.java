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

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;

import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsSupportUtil;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.Version;
import org.apache.log4j.Logger;

public class CCPatchProvider {

  private File myTempFile;
  private final ClearCaseConnection myConnection;
  private static final String EXECUTABLE_ATTR = "ugo+x";
  private static final Logger LOG = Logger.getLogger(CCPatchProvider.class);


  public CCPatchProvider(ClearCaseConnection connection) {
    myConnection = connection;
  }

  public void buildPatch(final PatchBuilder builder, String fromVersion, String lastVersion)
      throws IOException, VcsException, ExecutionException, ParseException {
    LOG.info("Building pach, calculating diff between " + fromVersion + " and " + lastVersion + "...");
    if (!myConnection.isUCM()) {
      throw new UnsupportedOperationException("Only supports UCM for now");
    }
    try {
      if (fromVersion == null) {
        //create the view from scratch
        myConnection.createViewAtTime(lastVersion);

      } else if (!myConnection.isConfigSpecWasChanged()) {
        // make the diff between previous view and new view
        //create the view from scratch
        myConnection.createViewAtTime(fromVersion);
        myConnection.createViewAtTime(lastVersion);

      } else {
        throw new RuntimeException("Don't know what to do in this case");
      }
    } finally {
      if (myTempFile != null) {
        FileUtil.delete(myTempFile);
      }
    }
    LOG.info("Finished building pach.");
  }

  private VersionProcessor createFileProcessor(final PatchBuilder builder) {
    return new VersionProcessor() {
      public void processFile(final String fileFullPath,
                              final String relPath,
                              final String pname,
                              final String version,
                              final ClearCaseConnection clearCaseConnection,
                              final boolean text,
                              final boolean executable)
        throws VcsException {
        loadFile(fileFullPath, builder, relPath);
      }

      public void processDirectory(final String fileFullPath,
                                   final String relPath,
                                   final String pname,
                                   final String version, final ClearCaseConnection clearCaseConnection)
        throws VcsException {
        try {
          builder.createDirectory(new File(relPath));
        } catch (IOException e) {
          throw new VcsException(e);
        }            
      }

      public void finishProcessingDirectory() {
        
      }
    };
  }

  private String getRelativePath(final String path) {
    return myConnection.getRelativePath(path);
  }

  private void loadFile(final String line, final PatchBuilder builder, String relativePath) throws VcsException {
    try {
      final File tempFile = getTempFile();
      FileUtil.delete(tempFile);

      myConnection.loadFileContent(tempFile, line);
      if (tempFile.isFile()) {
        final String pathWithoutVersion =
          CCPathElement.replaceLastVersionAndReturnFullPathWithVersions(line, myConnection.getViewWholePath(), null);
        ClearCaseFileAttr fileAttr = myConnection.loadFileAttr(pathWithoutVersion + CCParseUtil.CC_VERSION_SEPARATOR);

        final String fileMode = fileAttr.isIsExecutable() ? EXECUTABLE_ATTR : null;
        if (fileAttr.isIsText()) {
          final FileInputStream input = new FileInputStream(tempFile);
          try {
            builder.changeOrCreateTextFile(new File(relativePath), fileMode, input, tempFile.length(), null);
          } finally {
            input.close();
          }
        }
        else {
          final FileInputStream input = new FileInputStream(tempFile);
          try {
            builder.changeOrCreateBinaryFile(new File(relativePath), fileMode, input, tempFile.length());
          } finally {
            input.close();
          }
        }

      }
    } catch (ExecutionException e) {
      throw new VcsException(e);
    } catch (InterruptedException e) {
      throw new VcsException(e);
    } catch (IOException e) {
      throw new VcsException(e);
    }
  }

  public void dispose() {
    if (myTempFile != null) {
      FileUtil.delete(myTempFile);
      myTempFile = null;
    }
  }

  private synchronized File getTempFile() throws IOException {
    if (myTempFile == null) {
      myTempFile = FileUtil.createTempFile("cc", "temp");
    }
    return myTempFile;
  }
}
