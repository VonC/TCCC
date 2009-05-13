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
import jetbrains.buildServer.vcs.VcsSupportUtil;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import jetbrains.buildServer.log.Loggers;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    boolean configSpecWasChanged = myConnection.isConfigSpecWasChanged();
    Loggers.VCS.info(String.format("Building pach, calculating diff between %s and %s... (configspec changed=%s)", fromVersion, lastVersion, configSpecWasChanged));
    if (!myConnection.isUCM()) {
      throw new UnsupportedOperationException("Only supports UCM for now");
    }

    if (fromVersion == null || configSpecWasChanged) {
      Loggers.VCS.info("Sending whole content...");
      // we'll send down the whole view content
      String dynViewTag = null;
      try {
        dynViewTag = myConnection.createDynamicViewAtDate(lastVersion);
        VcsSupportUtil.exportFilesFromDisk(patchBuilder, new File(myConnection.getViewWholePath()));
      } finally {
        myConnection.removeView(dynViewTag);
      }
    } else if (!configSpecWasChanged) {
      // make the diff between previous view and new view
      String fromViewTag = null;
      String toViewTag = null;
      try {
        fromViewTag = myConnection.createDynamicViewAtDate(fromVersion);
        toViewTag = myConnection.createDynamicViewAtDate(lastVersion);

        File vobDir = new File(myConnection.getViewPath().getVob());
        File fromDir = new File(myConnection.getDynamicViewDirectory(fromViewTag) + File.separator + vobDir.getName());
        String toDir = myConnection.getDynamicViewDirectory(toViewTag) + File.separator + vobDir.getName();
        Set<FileEntry> filesInFrom = new DirectoryVisitor().getFileEntries(fromDir);
        Set<FileEntry> filesInTo = new DirectoryVisitor().getFileEntries(new File(toDir));

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
      } finally {
        myConnection.removeView(fromViewTag);
        myConnection.removeView(toViewTag);
      }



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
      fileEntries.add(new FileEntry(f, startingDir));
    }

    public Set<FileEntry> getFileEntries(File dir) {
      this.startingDir = dir;
      long t0 = System.currentTimeMillis();
      visitDirsAndFiles(dir);
      long t1 = System.currentTimeMillis();
      LOG.info(String.format("Finished inspecting directory %s : found %d elements in %d ms.", dir, fileEntries.size(), (t1 - t0)));
      return fileEntries;
    }
  }

  private abstract class AbstractTreeComparisonVisitor {

    public final void visitDirs(final File aDirA, final File aDirB) {
      String[] subElementsA = new String[0];
      if (aDirA != null && aDirA.exists()) {
        subElementsA = aDirA.list();
      }
      String[] subElementsB = new String[0];
      if (aDirB != null && aDirB.exists()) {
        subElementsB = aDirB.list();
      }
      final Set<String> processedA = new HashSet<String>();
      // Examine A sub-elements (subdirs and subfiles)
      for (final String aSubElementA : subElementsA) {
        final File aSubFileA = new File(aDirA, aSubElementA);
        final File aSubFileB = new File(aDirB, aSubElementA);
        process(aSubFileA, aSubFileB);
        if (aSubFileA.isDirectory()) {
          visitDirs(aSubFileA, aSubFileB);
        }
        processedA.add(aSubElementA);
      }
      // Examine B sub-elements (subdirs and subfiles), except those named like the ones found in A
      for (final String aSubElementB : subElementsB) {
        if (processedA.contains(aSubElementB) == false) {
          final File aSubFileA = new File(aDirA, aSubElementB);
          final File aSubFileB = new File(aDirB, aSubElementB);
          process(aSubFileA, aSubFileB);
          if (aSubFileB.isDirectory()) {
            visitDirs(null, aSubFileB);
          }
        }
      }
    }

    protected abstract void process(final File aDirOrFileA, final File aDirOrFileB);
  }

  private class TreeComparisonVisitor extends AbstractTreeComparisonVisitor {

    private File rootA = null;
    private File rootB = null;
    private List<FileEntry> removedEntries = new ArrayList<FileEntry>();
    private List<FileEntry> addedEntries = new ArrayList<FileEntry>();
    private List<FileEntry> modifiedEntries = new ArrayList<FileEntry>();
    private boolean hasBeenCompared = false;

    public TreeComparisonVisitor(final File aRootA, final File aRootB) {
      this.rootA = aRootA;
      this.rootB = aRootB;
    }
    /**
     * @return the removedEntries
     */
    public List<FileEntry> getRemovedEntries() {
      compare();
      return removedEntries;
    }

    /**
     * @return the addedEntries
     */
    public List<FileEntry> getAddedEntries() {
      compare();
      return addedEntries;
    }

    /**
     * @return the modifiedEntries
     */
    public List<FileEntry> getModifiedEntries() {
      compare();
      return modifiedEntries;
    }
    
    private void compare() {
			if(this.hasBeenCompared == false){
				this.hasBeenCompared = true;
			}
			process(this.rootA, this.rootB);
			long t0 = System.currentTimeMillis();
			visitDirs(this.rootA, this.rootB);
      long t1 = System.currentTimeMillis();
      final List<FileEntry> fileEntries = union(union(addedEntries, modifiedEntries), removedEntries);
      LOG.info("Finished inspecting directory " + this.rootA.getAbsolutePath() + " and " + this.rootB.getAbsolutePath() + " : found " + fileEntries.size() + " elements in "
          + (t1 - t0) + " ms.");
      OutputStreamWriter fw = null;
      try {
        fw = new FileWriter("listing-" + this.rootB.getName());
        for (FileEntry fileEntry : fileEntries) {
          fw.write(String.format("%s;%d\n", fileEntry.getRelativePath(), fileEntry.getModificationDate()));
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      finally {
        if(fw != null) {
          try {
            fw.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
		}

    @Override
    protected void process(File aDirOrFileA, File aDirOrFileB) {
      if (aDirOrFileA != null && aDirOrFileB != null) {
        final FileEntry aFileEntryA = new FileEntry(aDirOrFileA, this.rootA);
        final FileEntry aFileEntryB = new FileEntry(aDirOrFileB, this.rootB);
        if (aDirOrFileA.isDirectory() && aDirOrFileB.isDirectory() || (aDirOrFileA.isFile() && aDirOrFileB.isFile())) {
          if (aFileEntryA.equals(aFileEntryB) == false) {
            this.modifiedEntries.add(aFileEntryB);
          }
        } else {
          if (aDirOrFileA.exists()) {
            this.removedEntries.add(aFileEntryA);
          }
          if (aDirOrFileB.exists()) {
            this.addedEntries.add(aFileEntryB);
          }
        }
      } else if (aDirOrFileA == null && aDirOrFileB != null) {
        if (aDirOrFileB.exists()) {
          final FileEntry aFileEntryB = new FileEntry(aDirOrFileB, this.rootB);
          this.addedEntries.add(aFileEntryB);
        }
      } else if (aDirOrFileA != null && aDirOrFileB == null) {
        final FileEntry aFileEntryA = new FileEntry(aDirOrFileA, this.rootA);
        this.removedEntries.add(aFileEntryA);
      }
    }
  }

  private static class MapUtil {
    public static <K, V> Map<K, V> makeMap() {
      return new HashMap<K, V>();
    }
  }
  
  private static <K> List<K> union(final List<K> a, final List<K> b) {
    List<K> list = new ArrayList<K>();
    Map<K, Integer> mapa = getCardinalityMap(a);
    Map<K, Integer> mapb = getCardinalityMap(b);
    Set<K> elts = new HashSet<K>(a);
    elts.addAll(b);
    for (final K obj : elts) {
      for (int i = 0, m = Math.max(getFreq(obj, mapa), getFreq(obj, mapb)); i < m; i++) {
        list.add(obj);
      }
    }
    return list;
  }

  /** Constant to avoid repeated object creation */
  private static final Integer INTEGER_ONE = Integer.valueOf(1);
  
  private static <K> Map<K, Integer> getCardinalityMap(final Collection<K> coll) {
    Map<K, Integer> count = new HashMap<K, Integer>();
    for (final K obj : coll) {
      Integer c = (Integer) (count.get(obj));
      if (c == null) {
        count.put(obj, INTEGER_ONE);
      } else {
        count.put(obj, Integer.valueOf(c.intValue() + 1));
      }
    }
    return count;
  }
  

  private static final <K> int getFreq(final K obj, final Map<K, Integer> freqMap) {
    Integer count = freqMap.get(obj);
    if (count != null) {
      return count.intValue();
    }
    return 0;
  }
}

