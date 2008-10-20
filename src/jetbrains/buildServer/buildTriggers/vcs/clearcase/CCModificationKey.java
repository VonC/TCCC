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

public class CCModificationKey {
  private final String myDate;
  private final String myUser;
  private final CommentHolder myCommentHolder = new CommentHolder();


  public CCModificationKey(final String date, final String user) {
    myDate = date;
    myUser = user;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final CCModificationKey that = (CCModificationKey)o;

    if (!myDate.equals(that.myDate)) return false;
    if (!myUser.equals(that.myUser)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myDate.hashCode();
    result = 31 * result + myUser.hashCode();
    return result;
  }


  public String getDate() {
    return myDate;
  }

  public String getUser() {
    return myUser;
  }

  public CommentHolder getCommentHolder() {
    return myCommentHolder;
  }
}
