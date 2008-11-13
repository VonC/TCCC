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
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;

public class ConfigSpecParseUtil {
  public static ConfigSpec getAndSaveConfigSpec(final String viewName, final File outputConfigSpecFile) throws VcsException, IOException {
    final File viewRoot = ClearCaseConnection.ourProcessExecutor.getViewRoot(viewName);

    clearOldSavedVersion(outputConfigSpecFile);    
    outputConfigSpecFile.createNewFile();

    return doGetConfigSpecFromStream(viewRoot, ClearCaseConnection.getConfigSpecInputStream(viewName), null,
                                     new FileOutputStream(outputConfigSpecFile), outputConfigSpecFile);
  }

  private static void clearOldSavedVersion(final File outputConfigSpecFile) {
    int includesIndex = 0;
    final String path = outputConfigSpecFile.getAbsolutePath();
    File fileToDelete = outputConfigSpecFile;
    while (fileToDelete.exists()) {
      FileUtil.delete(fileToDelete);
      fileToDelete = new File(path + "." + ++includesIndex);
    }
  }

  public static ConfigSpec getConfigSpec(final String viewName) throws VcsException, IOException {
    final File viewRoot = ClearCaseConnection.ourProcessExecutor.getViewRoot(viewName);
    return getConfigSpecFromStream(viewRoot, ClearCaseConnection.getConfigSpecInputStream(viewName), null);
  }

  public static ConfigSpec getConfigSpecFromStream(final File viewRoot,
                                                   final InputStream configSpecInputStream,
                                                   final File inputConfigSpecFile) throws VcsException {
    return doGetConfigSpecFromStream(viewRoot, configSpecInputStream, inputConfigSpecFile, null, null);
  }

  private static ConfigSpec doGetConfigSpecFromStream(final File viewRoot,
                                                      final InputStream configSpecInputStream,
                                                      final File inputConfigSpecFile,
                                                      final OutputStream configSpecOutputStream,
                                                      final File outputConfigSpecFile) throws VcsException {
    final ConfigSpecBuilder builder = new ConfigSpecBuilder(viewRoot);

    readConfigSpecFromStream(builder, configSpecInputStream, inputConfigSpecFile, configSpecOutputStream, outputConfigSpecFile, 0);

    return builder.getConfigSpec();
  }

  private static int readConfigSpecFromStream(final ConfigSpecRulesProcessor processor,
                                              final InputStream configSpecInputStream,
                                              final File inputConfigSpecFile,
                                              final OutputStream configSpecOutputStream,
                                              final File outputConfigSpecFile,
                                              final int configSpecIncludesIndex) throws VcsException {
    BufferedReader reader = null;
    BufferedWriter writer = null;
    try {
      reader = new BufferedReader(new InputStreamReader(configSpecInputStream));
      if (configSpecOutputStream != null) {
        writer = new BufferedWriter(new OutputStreamWriter(configSpecOutputStream));
      }
      String line;

      int result = configSpecIncludesIndex;

      while ((line = reader.readLine()) != null) {
        if (writer != null) {
          writer.write(line);
          writer.newLine();
        }
        result = processLine(processor, line, inputConfigSpecFile, outputConfigSpecFile, result);
      }

      return result;
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

  private static int processLine(final ConfigSpecRulesProcessor processor,
                                  final String line,
                                  final File inputConfigSpecFile,
                                  final File outputConfigSpecFile,
                                  final int configSpecIncludesIndex) throws VcsException, IOException {
    String[] lines = line.split(";");
    int result = configSpecIncludesIndex;
    for (String aLine : lines) {
      final String trimmedLine = aLine.trim();
      if (trimmedLine.length() != 0) {
        result = doProcessLine(processor, trimmedLine, false, inputConfigSpecFile, outputConfigSpecFile, result);
      }
    }
    return result;
  }

  private static int doProcessLine(final ConfigSpecRulesProcessor processor,
                                    final String line,
                                    final boolean lineIsBlockRuleEnd,
                                    final File inputConfigSpecFile,
                                    final File outputConfigSpecFile,
                                    final int configSpecIncludesIndex) throws VcsException, IOException {
    String firstWord = extractFirstWord(line), trimmedfirstWord = trimQuotes(firstWord.trim());
    String rule = line.substring(firstWord.length()).trim();

    int includesIndex = configSpecIncludesIndex;

    if (ConfigSpecRuleTokens.BLOCK_RULE_END.equalsIgnoreCase(trimmedfirstWord)) {
      doProcessLine(processor, rule, true, inputConfigSpecFile, outputConfigSpecFile, includesIndex);
    } else if (ConfigSpecRuleTokens.TIME.equalsIgnoreCase(trimmedfirstWord)) {
      processor.processTimeRule(rule, !lineIsBlockRuleEnd);
    } else if (ConfigSpecRuleTokens.CREATE_BRANCH.equalsIgnoreCase(trimmedfirstWord)) {
      processor.processCreateBranchRule(rule, !lineIsBlockRuleEnd);
    } else if (lineIsBlockRuleEnd) {
      // ignoring wrong lines like "end element ..."
    } else if (ConfigSpecRuleTokens.LOAD.equalsIgnoreCase(trimmedfirstWord)) {
      processor.processLoadRule(rule);
    } else if (ConfigSpecRuleTokens.FILE_INCLUSION.equalsIgnoreCase(trimmedfirstWord)) {
      includesIndex++;
      final File inputFile;
      if (inputConfigSpecFile != null) {
        inputFile = new File(inputConfigSpecFile.getAbsolutePath() + "." + includesIndex);
      } else {
        inputFile = new File(trimQuotes(rule));
      }
      OutputStream outputStream = null;
      if (outputConfigSpecFile != null) {
        final File outputFile = new File(outputConfigSpecFile.getAbsolutePath() + "." + includesIndex);
        if (!outputFile.exists()) {
          outputFile.createNewFile();
        }
        outputStream = new FileOutputStream(outputFile);
      }
      try {
        includesIndex = readConfigSpecFromStream(processor, new FileInputStream(inputFile), inputConfigSpecFile, outputStream, outputConfigSpecFile, includesIndex);
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

    return includesIndex;
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
