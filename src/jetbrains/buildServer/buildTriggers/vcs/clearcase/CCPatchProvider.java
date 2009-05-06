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

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.text.ParseException;
import java.util.Map;
import java.util.Date;
import java.util.HashMap;

import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.apache.log4j.Logger;

public class CCPatchProvider {

  private static final Logger LOG = Logger.getLogger(CCPatchProvider.class);

  private final ClearCaseConnection myConnection;
  private static final String EXECUTABLE_ATTR = "ugo+x";

  public CCPatchProvider(ClearCaseConnection connection) {
    myConnection = connection;
  }

  public void buildPatch(final PatchBuilder builder, String fromVersion, String lastVersion)
      throws IOException, VcsException, ExecutionException, ParseException {
    LOG.info("Building pach, calculating diff between " + fromVersion + " and " + lastVersion + "...");
    if (!myConnection.isUCM()) {
      throw new UnsupportedOperationException("Only supports UCM for now");
    }
    if (fromVersion == null) {
      //create the view from scratch
      myConnection.createViewAtDate(lastVersion);
    } else if (!myConnection.isConfigSpecWasChanged()) {
      // make the diff between previous view and new view
      //create the view from scratch
      String fromViewTag = myConnection.createViewAtDate(fromVersion);
      String toViewTag = myConnection.createViewAtDate(lastVersion);

      Map<File, Long> fromFilesToLastModif = new DirectoryVisitor().getFilesToLastModif(new File("M:\\" + fromViewTag));
      Map<File, Long> toFilesToLastModif = new DirectoryVisitor().getFilesToLastModif(new File("M:\\" + toViewTag));

      

      myConnection.removeView(fromViewTag);
      myConnection.removeView(toViewTag);
    } else {
      throw new RuntimeException("Don't know what to do in this case");
    }
    LOG.info("Finished building pach.");
  }

 private String getRelativePath(final String path) {
    return myConnection.getRelativePath(path);
  }

  private void loadFile(final File file, final String line, final PatchBuilder builder, String relativePath) throws VcsException {
    try {
      myConnection.loadFileContent(file, line);
      if (file.isFile()) {
        final String pathWithoutVersion =
          CCPathElement.replaceLastVersionAndReturnFullPathWithVersions(line, myConnection.getViewWholePath(), null);
        ClearCaseFileAttr fileAttr = myConnection.loadFileAttr(pathWithoutVersion + CCParseUtil.CC_VERSION_SEPARATOR);

        final String fileMode = fileAttr.isIsExecutable() ? EXECUTABLE_ATTR : null;
        if (fileAttr.isIsText()) {
          final FileInputStream input = new FileInputStream(file);
          try {
            builder.changeOrCreateTextFile(new File(relativePath), fileMode, input, file.length(), null);
          } finally {
            input.close();
          }
        }
        else {
          final FileInputStream input = new FileInputStream(file);
          try {
            builder.changeOrCreateBinaryFile(new File(relativePath), fileMode, input, file.length());
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

  private class DirectoryVisitor extends AbstractDirectoryVisitor {

    Map<File, Long> filesToLastModif = new HashMap<File, Long>();

    protected void process(File f) {
      filesToLastModif.put(f, f.lastModified());
    }

    public Map<File, Long> getFilesToLastModif(File dir) {
      long t0 = System.currentTimeMillis();
      visitDirsAndFiles(dir);
      long t1 = System.currentTimeMillis();
      LOG.info("Finished inspecting directory " + dir + " : found " + filesToLastModif.size() + " elements in "
          + (t1 - t0) + " ms.");
      return filesToLastModif;
    }
  }
}
