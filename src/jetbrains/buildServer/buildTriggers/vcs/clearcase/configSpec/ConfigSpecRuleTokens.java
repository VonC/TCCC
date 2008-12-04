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

import org.jetbrains.annotations.NonNls;

public class ConfigSpecRuleTokens {
  public static final @NonNls String LOAD = "load";
  public static final @NonNls String TIME = "time";
  public static final @NonNls String FILE_INCLUSION = "include";
  public static final @NonNls String CREATE_BRANCH = "mkbranch";
  public static final @NonNls String STANDARD = "element";

  public static final @NonNls String STANDARD_FILE = "-file";
  public static final @NonNls String STANDARD_DIRECTORY = "-directory";
  public static final @NonNls String STANDARD_ELTYPE = "-eltype";

  public static final @NonNls String BLOCK_RULE_END = "end";

  public static final @NonNls String CHECKEDOUT = "CHECKEDOUT";
  public static final @NonNls String LATEST = "LATEST";

  public static final @NonNls String MKBRANCH_OPTION = "-mkbranch";
}
