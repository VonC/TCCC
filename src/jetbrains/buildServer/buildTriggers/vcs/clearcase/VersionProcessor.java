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

import jetbrains.buildServer.vcs.VcsException;

public interface VersionProcessor {
  void processFile(final String fileFullPath,
                   final String relPath,
                   final String pname,
                   final String version,
                   final ClearCaseConnection clearCaseConnection, final boolean text, final boolean executable) throws VcsException;

  void processDirectory(final String fileFullPath,
                        final String relPath,
                        final String pname,
                        final String version,
                        final ClearCaseConnection clearCaseConnection) throws VcsException;

  void finishProcessingDirectory() throws VcsException;
}
