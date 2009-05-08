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

package jetbrains.buildServer.buildTriggers.vcs.clearcase.configSpec;

import jetbrains.buildServer.buildTriggers.vcs.clearcase.ViewPath;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class ConfigSpecImpl implements ConfigSpec {
  private final List<ConfigSpecLoadRule> myLoadRules;
  private final List<ConfigSpecStandardRule> myStandardRules;
  private boolean myViewIsDynamic;

  public ConfigSpecImpl(final List<ConfigSpecLoadRule> loadRules, final List<ConfigSpecStandardRule> standardRules) {
    myLoadRules = loadRules;
    myStandardRules = standardRules;
  }


  @NotNull
  public List<ConfigSpecLoadRule> getLoadRules() {
    return myLoadRules;
  }


  public boolean isUnderLoadRules(final String ccViewRoot, final String fullFileName) throws IOException, VcsException {
    return myViewIsDynamic || doIsUnderLoadRules(fullFileName) ||
        doIsUnderLoadRules((new ViewPath(ccViewRoot, fullFileName)).getWholePath());
  }

  public void setViewIsDynamic(final boolean viewIsDynamic) {
    myViewIsDynamic = viewIsDynamic;
  }

  private boolean doIsUnderLoadRules(final String fullFileName) throws IOException {
    for (ConfigSpecLoadRule loadRule : myLoadRules) {
      if (loadRule.isUnderLoadRule(fullFileName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ConfigSpecImpl)) return false;

    final ConfigSpecImpl that = (ConfigSpecImpl) o;

    if (!myLoadRules.equals(that.myLoadRules)) return false;
    if (!myStandardRules.equals(that.myStandardRules)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLoadRules.hashCode();
    result = 31 * result + myStandardRules.hashCode();
    return result;
  }
}
