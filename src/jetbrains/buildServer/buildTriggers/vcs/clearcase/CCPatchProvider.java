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
import java.text.ParseException;

import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.apache.log4j.Logger;

public class CCPatchProvider {

  private final ClearCaseConnection myConnection;
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
    if (fromVersion == null) {
      //create the view from scratch
      myConnection.createViewAtDate(lastVersion);

    } else if (!myConnection.isConfigSpecWasChanged()) {
      // make the diff between previous view and new view
      //create the view from scratch
      myConnection.createViewAtDate(fromVersion);
      myConnection.createViewAtDate(lastVersion);

    } else {
      throw new RuntimeException("Don't know what to do in this case");
    }
    LOG.info("Finished building pach.");
  }


}
