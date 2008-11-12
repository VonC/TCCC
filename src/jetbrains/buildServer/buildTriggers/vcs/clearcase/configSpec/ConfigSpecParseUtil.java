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

import java.io.*;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.ClearCaseConnection;
import jetbrains.buildServer.vcs.VcsException;

public class ConfigSpecParseUtil {
  public static ConfigSpec getAndSaveConfigSpec(final String viewName, final File configSpecFile) throws VcsException, IOException {
    final File viewRoot = ClearCaseConnection.ourProcessExecutor.getViewRoot(viewName);
    if (!configSpecFile.exists()) {
      configSpecFile.createNewFile();
    }
    return doGetConfigSpecFromStream(viewRoot, ClearCaseConnection.getConfigSpecInputStream(viewName), new FileOutputStream(configSpecFile));
  }

  public static ConfigSpec getConfigSpec(final String viewName) throws VcsException, IOException {
    final File viewRoot = ClearCaseConnection.ourProcessExecutor.getViewRoot(viewName);
    return getConfigSpecFromStream(viewRoot, ClearCaseConnection.getConfigSpecInputStream(viewName));
  }

  public static ConfigSpec getConfigSpecFromStream(final File viewRoot,
                                                   final InputStream configSpecInputStream) throws VcsException {
    return doGetConfigSpecFromStream(viewRoot, configSpecInputStream, null);
  }

  private static ConfigSpec doGetConfigSpecFromStream(final File viewRoot,
                                                   final InputStream configSpecInputStream,
                                                   final OutputStream configSpecOutputStream) throws VcsException {
    final ConfigSpecBuilder builder = new ConfigSpecBuilder(viewRoot);

    readConfigSpecFromStream(builder, configSpecInputStream, configSpecOutputStream);

    return builder.getConfigSpec();
  }

  private static void readConfigSpecFromStream(final ConfigSpecRulesProcessor processor,
                                               final InputStream configSpecInputStream,
                                               final OutputStream configSpecOutputStream) throws VcsException {
    BufferedReader reader = null;
    BufferedWriter writer = null;
    try {
      reader = new BufferedReader(new InputStreamReader(configSpecInputStream));
      if (configSpecOutputStream != null) {
        writer = new BufferedWriter(new OutputStreamWriter(configSpecOutputStream));
      }
      String line;

      while ((line = reader.readLine()) != null) {
        if (writer != null) {
          writer.write(line);
          writer.newLine();
        }
        processLine(processor, line);
      }
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
    finally {
      try {
        if (reader != null) {
          reader.close();
        }
      } catch (IOException ignored) {}
      try {
        if (writer != null) {
          writer.close();
        }
      } catch (IOException ignored) {}
    }
  }

  private static void processLine(final ConfigSpecRulesProcessor processor, final String line) throws VcsException {
    String[] lines = line.split(";");
    for (String aLine : lines) {
      final String trimmedLine = aLine.trim();
      if (trimmedLine.length() != 0) {
        doProcessLine(processor, trimmedLine, false);
      }
    }
  }

  private static void doProcessLine(final ConfigSpecRulesProcessor processor, final String line, final boolean lineIsBlockRuleEnd) throws VcsException {
    String firstWord = extractFirstWord(line), trimmedfirstWord = trimQuotes(firstWord.trim());
    String rule = line.substring(firstWord.length()).trim();

    if (ConfigSpecRuleTokens.BLOCK_RULE_END.equalsIgnoreCase(trimmedfirstWord)) {
      doProcessLine(processor, rule, true);
    } else if (ConfigSpecRuleTokens.TIME.equalsIgnoreCase(trimmedfirstWord)) {
      processor.processTimeRule(rule, !lineIsBlockRuleEnd);
    } else if (ConfigSpecRuleTokens.CREATE_BRANCH.equalsIgnoreCase(trimmedfirstWord)) {
      processor.processCreateBranchRule(rule, !lineIsBlockRuleEnd);
    } else if (lineIsBlockRuleEnd) {
      // ignoring wrong lines like "end element ..."
    } else if (ConfigSpecRuleTokens.LOAD.equalsIgnoreCase(trimmedfirstWord)) {
      processor.processLoadRule(rule);
    } else if (ConfigSpecRuleTokens.FILE_INCLUSION.equalsIgnoreCase(trimmedfirstWord)) {
      try {
        readConfigSpecFromStream(processor, new FileInputStream(rule), null); // todo new OutputStream instead of null
      } catch (FileNotFoundException e) {
        throw new VcsException("Invalid config spec rule: \"" + line + "\"", e);
      }
    } else if (ConfigSpecRuleTokens.STANDARD.equalsIgnoreCase(trimmedfirstWord)) {
      String secondWord = extractFirstWord(rule), trimmedSecondWord = trimQuotes(secondWord.trim());
      rule = rule.substring(secondWord.length()).trim();
      String thirdWord = extractFirstWord(rule), trimmedThirdWord = trimQuotes(thirdWord.trim());

      if (ConfigSpecRuleTokens.STANDARD_FILE.equals(trimmedSecondWord) || ConfigSpecRuleTokens.STANDARD_DIRECTORY.equals(trimmedSecondWord)) {
        rule = rule.substring(thirdWord.length()).trim();
        processor.processStandartRule(trimmedSecondWord + ":", trimmedThirdWord, rule);
      } else if (ConfigSpecRuleTokens.STANDARD_ELTYPE.equals(trimmedSecondWord)) {
        rule = rule.substring(thirdWord.length()).trim();
        String fourthWord = extractFirstWord(rule), trimmedFourthWord = trimQuotes(fourthWord.trim());
        rule = rule.substring(fourthWord.length()).trim();
        processor.processStandartRule(trimmedSecondWord + ":" + trimmedThirdWord, trimmedFourthWord, rule);
      } else {
        processor.processStandartRule(":", trimmedSecondWord, rule);
      }
    }
  }

  public static String extractFirstWord(final String line) {
    if (line.startsWith("\"")) {
      final int nextQuotePos = line.indexOf('\"', 1);
      return nextQuotePos == -1 ? line : line.substring(0, nextQuotePos + 1);
    } else if (line.startsWith("'")) {
      final int nextQuotePos = line.indexOf('\'', 1);
      return nextQuotePos == -1 ? line : line.substring(0, nextQuotePos + 1);
    } else {
      final int firstSpacePos = line.indexOf(' ');
      return firstSpacePos == -1 ? line : line.substring(0, firstSpacePos);
    }
  }

  private static String trimQuotes(final String s) {
    if ((s.startsWith("\"") && s.endsWith("\"") ||
         s.startsWith("'") && s.endsWith("'")) && s.length() > 1) {
      return s.substring(1, s.length() - 1).trim();
    }
    return s;
  }
}
