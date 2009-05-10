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
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CCPatchProvider {

  private static final Logger LOG = Logger.getLogger(CCPatchProvider.class);

  private final ClearCaseConnection myConnection;
  private static final String EXECUTABLE_ATTR = "ugo+x";

  public CCPatchProvider(ClearCaseConnection connection) {
    myConnection = connection;
  }

  /**
   * Builds the patch.
   *
   * TODO.DANIEL : create a faster version for diffing dirs
   *
   * @param patchBuilder
   * @param fromVersion
   * @param lastVersion
   * @throws IOException
   * @throws VcsException
   * @throws ExecutionException
   * @throws ParseException
   */
  public void buildPatch(final PatchBuilder patchBuilder, String fromVersion, String lastVersion)
      throws IOException, VcsException, ExecutionException, ParseException {
    LOG.info("Building pach, calculating diff between " + fromVersion + " and " + lastVersion + "...");
    if (!myConnection.isUCM()) {
      throw new UnsupportedOperationException("Only supports UCM for now");
    }
    if (fromVersion == null) {
      //create the view from scratch
      myConnection.createDynamicViewAtDate(lastVersion);
    } else if (!myConnection.isConfigSpecWasChanged()) {
      // make the diff between previous view and new view
      //create the view from scratch
      String fromViewTag = myConnection.createDynamicViewAtDate(fromVersion);
      String toViewTag = myConnection.createDynamicViewAtDate(lastVersion);

      Set<FileEntry> filesInFrom = new DirectoryVisitor().getFileEntries(new File("M:\\" + fromViewTag + "\\isl\\product_model"));
      Set<FileEntry> filesInTo = new DirectoryVisitor().getFileEntries(new File("M:\\" + toViewTag + "\\isl\\product_model"));

      Collection intersection = CollectionUtils.intersection(filesInFrom, filesInTo);
      filesInFrom.removeAll(intersection);
      filesInTo.removeAll(intersection);

      LOG.info(String.format("Found %d added/removed/changed files or dirs.", CollectionUtils.union(filesInFrom, filesInTo).size()));

      // detect removed files
      for (FileEntry from : filesInFrom) {
        boolean removed = true;
        for (FileEntry to : filesInTo) {
          if (from.getRelativePath().equals(to.getRelativePath())) {
            removed = false;
            break;
          }
        }
        if (removed) {
          if (from.getFile().isDirectory()) {
            patchBuilder.deleteDirectory(from.getFile(), false);
          } else {
            patchBuilder.deleteFile(from.getFile(), false);
          }
        }
      }

      // detect changed or added files
      for (FileEntry to : filesInTo) {
        File file = to.getFile();
        if (!file.isDirectory()) {
          ClearCaseFileAttr fileAttr = myConnection.loadFileAttributes(file.getAbsolutePath());
          final String fileMode = fileAttr.isIsExecutable() ? EXECUTABLE_ATTR : null;
          final FileInputStream input = new FileInputStream(file);
          try {
            if (fileAttr.isIsText()) {
              patchBuilder.changeOrCreateTextFile(new File(to.getRelativePath()), fileMode, input, file.length(), null);
            } else {
              patchBuilder.changeOrCreateBinaryFile(new File(to.getRelativePath()), fileMode, input, file.length());
            }
          } finally {
            input.close();
          }
        }
      }

      myConnection.removeView(fromViewTag);
      myConnection.removeView(toViewTag);

    } else {
      throw new RuntimeException("Don't know what to do in this case");
    }
    LOG.info("Finished building pach.");
  }


  private class DirectoryVisitor extends AbstractDirectoryVisitor {

    Set<FileEntry> fileEntries = new HashSet<FileEntry>();

    /** directory in which the search begins */
    private File startingDir;

    protected void process(File f) {
      f.getPath();
      String relativePath = StringUtils.replace(f.getAbsolutePath(), startingDir.getAbsolutePath(), "");
      fileEntries.add(new FileEntry(f, f.lastModified(), relativePath));
    }

    public Set<FileEntry> getFileEntries(File dir) {
      this.startingDir = dir;
      long t0 = System.currentTimeMillis();
      visitDirsAndFiles(dir);
      long t1 = System.currentTimeMillis();
      LOG.info(String.format("Finished inspecting directory %s : found %d elements in %d ms.", dir, fileEntries.size(), (t1 - t0)));
      try {
        String listingFile = "listing_" + dir.getParentFile().getParentFile().getName() +"_"+ dir.getParentFile().getName() +"_"+ dir.getName() + ".log";
        OutputStreamWriter fw = new FileWriter(listingFile);
        for (FileEntry fileEntry : fileEntries) {
          fw.write(String.format("%s;%d\n", fileEntry.getRelativePath(), fileEntry.getModificationDate()));
        }
        fw.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return fileEntries;
    }

  }
}
