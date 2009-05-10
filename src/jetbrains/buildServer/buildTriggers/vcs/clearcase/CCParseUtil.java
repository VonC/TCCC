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
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.*;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.log.Loggers;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.apache.log4j.Logger;


public class CCParseUtil {

  private static final Logger LOG = Logger.getLogger(CCParseUtil.class);
  
  @NonNls private static final String INPUT_DATE_FORMAT = "dd-MMMM-yyyy.HH:mm:ss";
  @NonNls static final String OUTPUT_DATE_FORMAT = "yyyyMMdd.HHmmss";
  @NonNls public static final String CC_VERSION_SEPARATOR = "@@";

  private CCParseUtil() {
  }

  public static void processChangedFiles(final ClearCaseConnection connection,
                                         final String fromVersion,
                                         final String currentVersion,
                                         final ChangedFilesProcessor fileProcessor)
    throws ParseException, IOException, VcsException {
    final @Nullable Date lastDate = currentVersion != null ? parseDate(currentVersion) : null;


    final InputStream inputStream = connection.getHistory(fromVersion);

    final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));


    try {
      String line = reader.readLine();
      while (line != null) {
        String nextLine = reader.readLine();
        if (!line.endsWith(ClearCaseConnection.LINE_END_DELIMITER)) {
          while (nextLine != null && !line.endsWith(ClearCaseConnection.LINE_END_DELIMITER)) {
            line += '\n' + nextLine;
            nextLine = reader.readLine();
          }
        }
        if (line.endsWith(ClearCaseConnection.LINE_END_DELIMITER)) {
          line = line.substring(0, line.length() - ClearCaseConnection.LINE_END_DELIMITER.length());
        }
        HistoryElement element = HistoryElement.readFrom(line);
        if (element != null) {
          final Date date = new SimpleDateFormat(OUTPUT_DATE_FORMAT).parse(element.getDate());
            if (lastDate == null || date.before(lastDate)) {
              if ("checkin".equals(element.getOperation())) {
                if ("create directory version".equals(element.getEvent())) {
                    fileProcessor.processChangedDirectory(element);
                } else if ("create version".equals(element.getEvent())) {
                    fileProcessor.processChangedFile(element);
                }
              } else if ("rmver".equals(element.getOperation())) {
                if ("destroy version on branch".equals(element.getEvent())) {
                  fileProcessor.processDestroyedFileVersion(element);
                }
              }
            }
        }
        line = nextLine;
      }

    } finally {
      reader.close();
    }
  }

  private static Date parseDate(final String currentVersion) throws ParseException {
    return getDateFormat().parse(currentVersion);
  }
  
  public static String formatDate(final Date date) {
    return getDateFormat().format(date);
    
  }


  public static SimpleDateFormat getDateFormat() {
    return new SimpleDateFormat(INPUT_DATE_FORMAT, Locale.US);
  }

  public static int getVersionInt(final String wholeVersion) {
    int versSeparator = wholeVersion.lastIndexOf(File.separator);
    return Integer.parseInt(wholeVersion.substring(versSeparator + 1));
  }

  static String toConfigSpecDate(Date d)  throws ParseException {
    DateFormat confSpecFormat = new SimpleDateFormat("dd-MMM-yyyy HH.mm:SS");
    return confSpecFormat.format(d);
  }

  /**
   * Parses a date formatted like this : 15-Apr-2009.09:00:00.
   *
   * @param teamcityBuildDate a teamcity build date
   * @return a java date
   * @throws ParseException if a formatting error occurs
   */
  static Date toDate(String teamcityBuildDate) throws ParseException {
    Locale currenLocale = Locale.getDefault();
    Loggers.VCS.info(String.format("Current Locale %s", currenLocale));
    return new SimpleDateFormat("dd-MMM-yyyy.HH:mm:SS").parse(teamcityBuildDate);
  }

  static String escapeDate(Date d) {
    return new SimpleDateFormat("dd_MMM_yyyy_HH_mm_SS").format(d);
  }
}
