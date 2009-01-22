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

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import jetbrains.buildServer.BuildAgent;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.CCParseUtil;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.ClearCaseSupport;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.GeneralDataCleaner;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.Hash;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ClearCaseStructureCache {
  private final @NotNull File myBaseDir;
  private final @NotNull ClearCaseSupport myParentSupport;

  public ClearCaseStructureCache(final @NotNull File baseDir, final @NotNull ClearCaseSupport support) {
    myBaseDir = baseDir;
    myParentSupport = support;
  }

  public void register(final @NotNull SBuildServer server, final @NotNull EventDispatcher<BuildServerListener> dispatcher) {
    server.registerExtension(GeneralDataCleaner.class, ClearCaseStructureCache.class.getName(),
                             new ClearcaseCacheGeneralDataCleaner());

    dispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void sourcesVersionReleased(@NotNull final BuildType configuration) {
        doSourcesVersionReleased(configuration);
      }

      @Override
      public void sourcesVersionReleased(@NotNull final BuildAgent agent) {
        cleanup();
      }

      @Override
      public void sourcesVersionReleased(@NotNull final BuildType configuration, @NotNull final BuildAgent agent) {
        doSourcesVersionReleased(configuration);
      }

      private void doSourcesVersionReleased(final BuildType configuration) {
        final List<? extends VcsRoot> roots = configuration.getVcsRoots();
        final String vcsName = myParentSupport.getName();
        for (VcsRoot root : roots) {
          if (vcsName.equals(root.getVcsName())) {
            cleanup(root);
          }
        }
      }
    });
  }

  @Nullable
  public CacheElement getNearestExistingCache(final @NotNull Date version, final @NotNull String path, final @NotNull IncludeRule includeRule, final @NotNull VcsRoot vcsRoot) {
    File baseDir = createCacheBaseDir(path, vcsRoot);
    File[] cacheFiles = baseDir.listFiles();
    CacheElement result = null;
    if (cacheFiles != null) {
      for (File cacheFile : cacheFiles) {
        String fileName = cacheFile.getName();
        try {
          long currentCacheTime = Long.parseLong(fileName);
          if (currentCacheTime <= version.getTime()) {
            if (result == null || result.getVersion().getTime() < currentCacheTime) {
              result = getCache(new Date(currentCacheTime), path, includeRule, vcsRoot);
            }
          }
        } catch (NumberFormatException e) {
          //ignore
        }
      }
    }
    return result;
  }

  @Nullable
  public CacheElement getCache(final @NotNull String version, final @NotNull String path, final @NotNull IncludeRule includeRule, final @NotNull VcsRoot root) throws VcsException {
    try {
      Date date = CCParseUtil.getDateFormat().parse(version);
      final File cacheFile = createCacheFile(date, path, root);
      if (cacheFile == null) return null;
      return new CacheElement(date, cacheFile, this, path, version,
                              includeRule, myParentSupport, root);
    } catch (ParseException e) {
      throw new VcsException(e);
    }
  }
  
  @Nullable
  public CacheElement getCache(final @NotNull Date version, final @NotNull String path, final @NotNull IncludeRule includeRule, final @NotNull VcsRoot root) {
    return new CacheElement(version, createCacheFile(version, path, root), this, path, CCParseUtil.getDateFormat().format(version),
                            includeRule,
                            myParentSupport, root);
  }

  @Nullable
  private File createCacheFile(final @NotNull Date version, final @NotNull String path, final @NotNull VcsRoot root) {
    return new File(createCacheBaseDir(path, root), String.valueOf(version.getTime()));
  }

  @Nullable
  private File createCacheBaseDir(final @NotNull String path, final @NotNull VcsRoot vcsRoot) {
    return new File(getCacheDir(vcsRoot), String.valueOf(Hash.calc(path)));
  }

  public void cleanup() {
    doCleanup(true);
  }

  public void cleanup(final @NotNull VcsRoot root) {
    final File cacheDir = getCacheDir(root);
    if (cacheDir == null) return;
    cleanupFolder(cacheDir, true);
  }

  private void doCleanup(final boolean keepLastCache) {
    File[] folders = myBaseDir.listFiles();
    if (folders != null) {
      for (File folder : folders) {
        if (!folder.isDirectory()) {
          FileUtil.delete(folder);
        }
        cleanupFolder(folder, keepLastCache);
      }
    }
  }

  private void cleanupFolder(final @NotNull File dir, final boolean keepLastCache) {
    File[] subDirs = dir.listFiles();
    if (subDirs != null) {
      for (File subDir : subDirs) {
        cleanupSubFolder(subDir, keepLastCache);
      }
    }
  }

  private void cleanupSubFolder(final @NotNull File subDir, final boolean keepLastCache) {
    File[] versCaches = subDir.listFiles();
    if (versCaches == null) return;
    long lastCacheDate = -1;
    for (File versCach : versCaches) {
      try {
        long currentCacheDate = Long.parseLong(versCach.getName());
        if (currentCacheDate > lastCacheDate) {
          lastCacheDate = currentCacheDate;
        }
      } catch (NumberFormatException e) {
        //ignore
      }
    }

    String keepFileName = String.valueOf(lastCacheDate);

    for (File versCach : versCaches) {
      if (!keepLastCache || !versCach.getName().equals(keepFileName)) {
        FileUtil.delete(versCach);
      }
    }
    
    if (!keepLastCache) {
      FileUtil.delete(subDir);
    }
  }

  public void clearCaches(final @NotNull VcsRoot root) {
    final File dir = getCacheDir(root);
    if (dir != null) {
      cleanupFolder(dir, false);
    }
  }

  @Nullable
  public File getCacheDir(final @NotNull VcsRoot root) {
    return getCacheDir(root, false);
  }

  @Nullable
  public File getCacheDir(final VcsRoot root, final boolean createDirs) {
    final File cacheDir = new File(myBaseDir, root.getId() + "." + root.getRootVersion());

    if (createDirs && !cacheDir.exists() && !cacheDir.mkdirs()) return null;

    return cacheDir;
  }

  private class ClearcaseCacheGeneralDataCleaner implements GeneralDataCleaner {
    public void performCleanup(final @NotNull Connection connection) throws SQLException {
      cleanup();
    }
  }
}
