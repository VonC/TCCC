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

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
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


public class ClearCaseStructureCache {
  private final File myBaseDir;
  private final ClearCaseSupport myParentSupport;

  public ClearCaseStructureCache(final File baseDir, ClearCaseSupport support) {
    myBaseDir = baseDir;
    myParentSupport = support;
  }
  
  public void register(SBuildServer server, final EventDispatcher<BuildServerListener> dispatcher) {
    server.registerExtension(GeneralDataCleaner.class, ClearCaseStructureCache.class.getName(),
                             new ClearcaseCacheGeneralDataCleaner());

    dispatcher.addListener(new BuildServerAdapter(){
      public void sourcesVersionReleased(@NotNull final BuildType configuration) { //todo: fix to clean only if affected        
        cleanup();
      }
    });
  }

  public CacheElement getNearestExistingCache(final Date version, String path, final IncludeRule includeRule, final VcsRoot vcsRoot) {
    File baseDir = createCacheBaseDir(path);
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

  public CacheElement getCache(final String version, String path, final IncludeRule includeRule, final VcsRoot root) throws VcsException {
    try {
      Date date = CCParseUtil.getDateFormat().parse(version);
      return new CacheElement(date, createCacheFile(date, path), this, path, version,
                              includeRule, myParentSupport, root);
    } catch (ParseException e) {
      throw new VcsException(e);
    }
  }
  
  public CacheElement getCache(final Date version, String path, final IncludeRule includeRule, final VcsRoot root) {
    return new CacheElement(version, createCacheFile(version, path), this, path, CCParseUtil.getDateFormat().format(version),
                            includeRule,
                            myParentSupport, root);
  }

  private File createCacheFile(final Date version, final String path) {
    return new File(createCacheBaseDir(path), String.valueOf(version.getTime()));
  }

  private File createCacheBaseDir(final String path) {
    return new File(myBaseDir, String.valueOf(Hash.calc(path)));
  }

  public void cleanup() {
    doCleanup(true);
  }

  private void doCleanup(final boolean keepLastCache) {
    File[] subDirs = myBaseDir.listFiles();
    if (subDirs != null) {
      for (File subDir : subDirs) {
        cleanup(subDir, keepLastCache);
      }
    }
  }

  private void cleanup(final File subDir, final boolean keepLastCache) {
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

  public void clearCaches() {
    doCleanup(false);
  }

  private class ClearcaseCacheGeneralDataCleaner implements GeneralDataCleaner {
    public void performCleanup(final Connection connection) throws SQLException {
      cleanup();
    }
  }
}
