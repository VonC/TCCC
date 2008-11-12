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

package jetbrains.buildServer.buildTriggers.vcs.clearcase.configSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigSpecBuilder implements ConfigSpecRulesProcessor {
  final private List<ConfigSpecLoadRule> myLoadRules = new ArrayList<ConfigSpecLoadRule>();
  final private List<ConfigSpecStandardRule> myStandardRules = new ArrayList<ConfigSpecStandardRule>();
  final private File myViewRoot;

  public ConfigSpecBuilder(final File viewRoot) {
    myViewRoot = viewRoot;
  }

  public void processLoadRule(final String rule) {
    myLoadRules.add(new ConfigSpecLoadRule(myViewRoot, rule));
  }

  public void processTimeRule(final String rule, final boolean isBlockStart) {
    //todo
  }

  public void processCreateBranchRule(final String rule, final boolean isBlockStart) {
    //todo
  }

  public void processStandartRule(final String scope, final String pattern, final String rule) {
    final String versionSelector = ConfigSpecParseUtil.extractFirstWord(rule);
    myStandardRules.add(new ConfigSpecStandardRule(scope, normalizePattern(pattern), versionSelector));
  }

  private String normalizePattern(final String pattern) {
    String result = pattern;
    if (pattern.startsWith("[")) {
      int pos1 = pattern.indexOf("="), pos2 = pattern.indexOf("]");
      result = pattern.substring(pos1 + 1, pos2) + pattern.substring(pos2 + 1);
      if (result.startsWith(File.separator)) {
        result = result.substring(1);
      }
    }
    return result;
  }

  public ConfigSpec getConfigSpec() {
    return new ConfigSpecImpl(myLoadRules, myStandardRules);
  }
}
