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

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class LoggerToFile {
  private final File myFile;
  private final long myMaxFileLength;

  private FileOutputStream myStream;

  public LoggerToFile(final File file, final long maxFileLength) {
    myFile = file;
    myMaxFileLength = maxFileLength;
  }

  public synchronized void log(String s) {
    try {
      ensureStreamExist();
      myStream.write(s.getBytes());
    } catch (Throwable e) {
      //ignore
    }
    flush();
  }

  private void ensureStreamExist() throws FileNotFoundException {
    if (myFile.isFile() && myFile.length() > myMaxFileLength) {
      close();
      FileUtil.delete(myFile);
    }
    if (myStream == null) {
      //noinspection IOResourceOpenedButNotSafelyClosed
      myStream = new FileOutputStream(myFile, true);
    }
  }

  public synchronized void flush() {
    if (myStream != null) {
      try {
        myStream.flush();
      } catch (Throwable e) {
        //ignore
      }
    }
  }

  public synchronized void close() {
    if (myStream != null) {
      try {
        myStream.close();
      } catch (Throwable e) {
        //ignore
      } finally {
        myStream = null;
      }
    }
  }
}
