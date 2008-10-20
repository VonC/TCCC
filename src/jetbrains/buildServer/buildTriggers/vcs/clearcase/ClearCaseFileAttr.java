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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ClearCaseFileAttr {
  private final boolean myIsExecutable;
  private final boolean myIsText;
  private static final String ELEMENT_TYPE = "  element type: ";
  private static final String USER = "    User : ";
  private static final String GROUP = "    Group : ";
  private static final String OTHER = "    Other : ";

  public ClearCaseFileAttr(final boolean isExecutable, final boolean isText) {
    myIsExecutable = isExecutable;
    myIsText = isText;
  }

  public boolean isIsExecutable() {
    return myIsExecutable;
  }

  public boolean isIsText() {
    return myIsText;
  }

  public static ClearCaseFileAttr readFrom(final InputStream input) throws IOException {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    String line;
    String fileType = null;
    boolean executable = false;
    while ((line  = reader.readLine() ) != null) {
      if (line.startsWith(ELEMENT_TYPE)) {
        fileType = line.substring(ELEMENT_TYPE.length());
      }
      else if (line.startsWith(USER) || line.startsWith(GROUP) || line.startsWith(OTHER)) {
        String mode = line.substring(line.lastIndexOf(":"));
        executable = executable || mode.contains("x");
      }
    }


    return new ClearCaseFileAttr(executable, "text_file".equals(fileType));
  }
}
