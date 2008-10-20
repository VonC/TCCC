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

import java.util.LinkedHashSet;
import java.util.Set;

public class CommentHolder {
  private final Set<String> myActivities = new LinkedHashSet<String>();
  private final Set<String> myDescriptions = new LinkedHashSet<String>();
  private final Set<String> myComments = new LinkedHashSet<String>();

  public void addActivity(String str) {
    if (str != null && str.length() > 0) {
      myActivities.add(str);
    }
  }

  public void addDescription(String str) {
    if (str != null && str.length() > 0) {
      myDescriptions.add(str);
    }
  }

  public void addComment(String str) {
    if (str != null && str.length() > 0) {
      if (!myDescriptions.contains(str)) {
        myComments.add(str);
      }
    }
  }

  public String toString() {
    final StringBuffer result = new StringBuffer();

    if (!myActivities.isEmpty()) {
      result.append(myActivities.size() > 1 ? "Activities" : "Activity").append(":");
      for (String activity : myActivities) {
        result.append('\n').append('\t').append(activity);
      }
      
      result.append('\n');
    }

    if (!myDescriptions.isEmpty()) {
      result.append(myDescriptions.size() > 1 ? "Descriptions" : "Description").append(":");
      for (String activity : myDescriptions) {
        result.append('\n').append('\t').append(activity);
      }
    }


    for (String comment : myComments) {
      result.append('\n').append(comment);
    }
    return result.toString();

  }

  public void update(final String activity, final String comment, final String versionDescription) {
    addActivity(activity);
    addDescription(versionDescription);
    addComment(comment);
  }
}
