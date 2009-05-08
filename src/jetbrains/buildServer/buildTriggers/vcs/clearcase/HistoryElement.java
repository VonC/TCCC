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

import java.io.IOException;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

public class HistoryElement {
  
  private final String myUser;
  private final String myDate;
  private final String myObjectName;
  private final String myObjectKind;
  private final String myObjectVersion;
  private final String myOperation;
  private final String myEvent;
  private final String myComment;
  private final String myActivity;
  private String myPreviousVersion;
  private static final int EXPECTED_CHANGE_FIELD_COUNT = 10;


  public HistoryElement(
      final String user,
      final String date,
      final String objectName,
      final String objectKind,
      final String objectVersion,
      final String operation,
      final String event,
      final String comment,
      final String activity,
      final String previousVersion) {
    myUser = user;
    myDate = date;
    myObjectName = objectName;
    myObjectKind = objectKind;
    myObjectVersion = objectVersion;
    myOperation = operation;
    myEvent = event;
    myComment = comment;
    myActivity = activity;
    myPreviousVersion = previousVersion;
  }

  private static HistoryElement createHistoryElement(final String user,
                                                     final String date,
                                                     final String objectName,
                                                     final String objectKind,
                                                     final String objectVersion,
                                                     final String operation,
                                                     final String event,
                                                     final String comment,
                                                     final String activity, String previousVersion) {
    String kind = objectKind, version = objectVersion;
    if ("rmver".equals(operation) && "destroy version on branch".equals(event)) {
      final String extractedVersion = extractVersion(comment);
      if (extractedVersion != null) {
        kind = "version";
        version = extractedVersion;
      }
    }
    return new HistoryElement(user, date, objectName, kind, version, operation, event, comment, activity, previousVersion);

  }

  @Nullable
  private static String extractVersion(final String comment) {
    int firstPos = comment.indexOf("\""), lastPos = comment.lastIndexOf("\"");
    if (firstPos != -1 && lastPos != -1 && firstPos < lastPos) {
      return comment.substring(firstPos + 1, lastPos);
    }
    return null;
  }

  public static HistoryElement readFrom(final String line) {
    final String[] strings = line.split(ClearCaseConnection.DELIMITER, EXPECTED_CHANGE_FIELD_COUNT);
    if (strings.length < EXPECTED_CHANGE_FIELD_COUNT - 1) {
      return null;
    }
    else {
      String user = strings[0];
      String date = strings[1];
      String objectName = strings[2];
      String objectKind = strings[3];
      String objectVersion = strings[4];
      String operation = strings[5];
      String event = strings[6];
      String comment = strings[7];
      String previousVersion = strings[8];
      String activity = "";
      if (strings.length != EXPECTED_CHANGE_FIELD_COUNT - 1) {
        activity = strings[9];
      }
      return createHistoryElement(user, date, objectName, objectKind, objectVersion, operation, event, comment, activity, previousVersion);

    }
  }

  public String getDate() {
    return myDate;
  }

  public String getObjectName() {
    return myObjectName;
  }

  public String getObjectVersion() {
    return myObjectVersion;
  }

  public String getOperation() {
    return myOperation;
  }

  public String getEvent() {
    return myEvent;
  }

  public String getComment() {
    return myComment;
  }
  

  public String getUser() {
    return myUser;
  }

  public int getObjectVersionInt() {
    return CCParseUtil.getVersionInt(myObjectVersion);
  }

  public String getActivity() {
    return myActivity;
  }

  public String getPreviousVersion() {
    return myPreviousVersion;
  }

  public String getLogRepresentation() {
    return "\"" + getObjectName() + "\", version \"" + getObjectVersion() + "\", date \"" + getDate() + "\", operation \"" + getOperation() + "\", event \"" + getEvent() + "\"";
  }
}
