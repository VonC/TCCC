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

import java.io.*;
import java.util.Stack;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.CCParseUtil;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.ClearCaseConnection;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.VersionProcessor;
import jetbrains.buildServer.util.TCStreamUtil;
import jetbrains.buildServer.vcs.VcsException;

public class CacheProcessor {
  private final VersionProcessor myVersionProcessor;
  private final ClearCaseConnection myConnection;
  private final File myCacheFile;

  public CacheProcessor(final VersionProcessor versionProcessor, final ClearCaseConnection connection, final File cacheFile) {

    myVersionProcessor = versionProcessor;
    myConnection = connection;
    myCacheFile = cacheFile;
  }

  public void processAllRevisions(final boolean processRoot) throws IOException, VcsException {
    final Stack<ReadCacheItem> readDirs = new Stack<ReadCacheItem>();
    DataInputStream input = new DataInputStream(new FileInputStream(myCacheFile));
    int index = 0;
    try {
      while (true) {
        byte type;
        try {
          type = input.readByte();
        } catch (EOFException e) {
          break;
        }
          if (type == CacheElement.FILE_TYPE) {
            String name = TCStreamUtil.readString(input);
            String version = TCStreamUtil.readString(input);

            boolean text = false;
            boolean executable = false;

            final int modeSep = version.indexOf("|");

            if (modeSep > 0) {
              String mode = version.substring(modeSep);
              version = version.substring(0, modeSep);
              text = mode.contains("t");
              executable = mode.contains("x");
            }
            
            readDirs.push(new ReadCacheItem(name, version));



            myVersionProcessor.processFile(createFullPath(readDirs, myConnection), createRelPath(readDirs), createIOPath(readDirs, myConnection), version, myConnection,
                                           text, executable);
            readDirs.pop();
          }
          else if (type == CacheElement.DIR_OPEN_TYPE) {
            String name = TCStreamUtil.readString(input);
            String version = TCStreamUtil.readString(input);
            readDirs.push(new ReadCacheItem(name, version));
            if (index > 0 || processRoot) {
              myVersionProcessor.processDirectory(createFullPath(readDirs, myConnection), createRelPath(readDirs), createIOPath(readDirs, myConnection), version, myConnection);
            }
          }
          else if (type == CacheElement.DIR_CLOSE_TYPE){ 
            readDirs.pop();
            myVersionProcessor.finishProcessingDirectory();
          }
          else {
            throw new IOException("Unexpected type "+ type);
          }
        index++;
      }
    } finally {
      input.close();
    }
    
  }
  
  private String createIOPath(final Stack<ReadCacheItem> readDirs, final ClearCaseConnection connection) {
    StringBuilder result = new StringBuilder();
    if (connection != null) {
      result.append(connection.getViewWholePath());
    }
    for (ReadCacheItem readDir : readDirs) {
      String name = readDir.getName();
      if (!isRoot(name)) {
        result.append(File.separatorChar);
        result.append(name);
      }
    }
    return result.toString();    
  }

  private boolean isRoot(final String name) {
    return "".equals(name);
  }

  private String createRelPath(final Stack<ReadCacheItem> readDirs) {
    StringBuilder result = new StringBuilder();
    for (ReadCacheItem readDir : readDirs) {
      if (result.length() > 0) {
        result.append(File.separatorChar);
      }
      String name = readDir.getName();
      if (!isRoot(name)) {
        result.append(name);
      }
    }
    return result.toString();
    
  }

  private String createFullPath(final Stack<ReadCacheItem> readDirs, final ClearCaseConnection connection) {
    StringBuilder result = new StringBuilder();
    if (connection != null) {
      result.append(connection.getViewWholePath());
    }
    for (ReadCacheItem readDir : readDirs) {
      if (!isRoot(readDir.getName())) {
        result.append(File.separatorChar);
        result.append(readDir.getName());
        result.append(readDir.getVersion());
      }
      else {
        result.append(CCParseUtil.CC_VERSION_SEPARATOR);
        result.append(readDir.getVersion());        
      }
    }
    return result.toString();
  }
  
}
