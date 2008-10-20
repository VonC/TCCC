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

package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class CCMockProcess {
  private final File myOutputFile;

  private final Collection<String> myExistingCommands = new HashSet<String>();

  public CCMockProcess(final File outputFile) {
    myOutputFile = outputFile;
  }

  public void saveCommand(String command, byte[] output) {
    if (!myExistingCommands.contains(command)) {
      try {
        final DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(myOutputFile, true));
        try {
          outputStream.writeInt(command.length());
          for (int i = 0; i < command.length(); i++) {
            outputStream.writeChar(command.charAt(i));
          }
          outputStream.writeInt(output.length);
          outputStream.write(output);
        } finally {
          outputStream.close();
        }
      } catch (IOException e) {
        //ignore
      }

      myExistingCommands.add(command);
    }
  }
}
