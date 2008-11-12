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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.io.FileUtil;
import java.io.*;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.CommandLineExecutor;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.ProcessListener;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.configSpec.ConfigSpec;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.configSpec.ConfigSpecParseUtil;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.process.ClearCaseFacade;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.process.InteractiveProcess;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.process.InteractiveProcessFacade;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.structure.ClearCaseStructureCache;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.Version;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.versionTree.VersionTree;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.MultiMap;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TCStreamUtil;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"SimplifiableIfStatement"})
public class ClearCaseConnection {
  private final String myViewName;
  private final String myNormalizedViewName;
  private final boolean myUCMSupported;

  private static final Map<String, Semaphore> viewName2Semaphore = new ConcurrentHashMap<String, Semaphore>();

  private InteractiveProcessFacade myProcess;
  
  private final static int CC_LOG_FILE_SIZE_LIMIT = getLogSizeLimit();

  private static int getLogSizeLimit() {
    try {
      return Integer.parseInt(System.getProperty("cc.log.size.limit"));
    } catch (Throwable e) {
      return 10;
    }
  }

  private final static LoggerToFile ourLogger = new LoggerToFile(new File("ClearCase.log"), 1000 * 1000 * CC_LOG_FILE_SIZE_LIMIT );
  private static final Logger LOG = Logger.getLogger(ClearCaseConnection.class);

  public final static String DELIMITER = "#--#";

  @NonNls public static final String LINE_END_DELIMITER = "###----###";
  public final static String FORMAT = "%u"              //user
                                      + DELIMITER + "%Nd"           //date
                                      + DELIMITER + "%En"           //object name
                                      + DELIMITER + "%m"           //object kind    
                                      + DELIMITER + "%Vn"           //objectversion
                                      + DELIMITER + "%o"            //operation
                                      + DELIMITER + "%e"            //event    
                                      + DELIMITER + "%Nc"           //comment
                                      + DELIMITER + "%[activity]p"  //activity    
                                      + LINE_END_DELIMITER + "\\n";
  private final MultiMap<String, HistoryElement> myChangesToIgnore = new MultiMap<String, HistoryElement>();
  private final MultiMap<String, HistoryElement> myDeletedVersions = new MultiMap<String, HistoryElement>();
  private static final Pattern END_OF_COMMAND_PATTERN = Pattern.compile("Command (.*) returned status (.*)");
  private static final boolean LOG_COMMANDS = System.getProperty("cc.log.commands") != null;

  private final ConfigSpec myConfigSpec;
  private static final String UPDATE_LOG = "teamcity.clearcase.update.result.log";
  public static ClearCaseFacade ourProcessExecutor = new ClearCaseFacade() {
    public ExecResult execute(final GeneralCommandLine commandLine, final ProcessListener listener) throws ExecutionException {
      CommandLineExecutor commandLineConnection = new CommandLineExecutor(commandLine);      
      commandLineConnection.addListener(listener);
      return commandLineConnection.runProcess();      
    }

    public InteractiveProcessFacade createProcess(final GeneralCommandLine generalCommandLine) throws ExecutionException {
      return createInteractiveProcess(generalCommandLine.createProcess());
    }

    public File getViewRoot(final String viewPath) throws VcsException {
      return CCParseUtil.getViewRoot(viewPath);
    }

  };

  private final ClearCaseStructureCache myCache;
  private final VcsRoot myRoot;
  private final boolean myConfigSpecWasChanged;

  public boolean isConfigSpecWasChanged() {
    return myConfigSpecWasChanged;
  }

  public ClearCaseConnection(String viewName,
                             boolean ucmSupported,
                             ClearCaseStructureCache cache,
                             VcsRoot root,
                             final File cacheDir,
                             final boolean checkCSChange) throws Exception {

    // Explanation of config specs at:
    // http://www.philforhumanity.com/ClearCase_Support_17.html

    myCache = cache;
    myRoot = root;
    
    myUCMSupported = ucmSupported;
    viewName = normalizeFileSeparators(viewName);

    viewName = cutOffLastSeparator(viewName);

    myViewName = viewName;

    final File configSpecFile = cacheDir != null ? new File(cacheDir, "cs") : null;

    ConfigSpec oldConfigSpec = null;
    if (checkCSChange && configSpecFile != null && configSpecFile.isFile()) {
      oldConfigSpec = ConfigSpecParseUtil.getConfigSpecFromStream(ourProcessExecutor.getViewRoot(viewName), new FileInputStream(configSpecFile));
    }

    myConfigSpec = checkCSChange && configSpecFile != null ?
                                  ConfigSpecParseUtil.getAndSaveConfigSpec(myViewName, configSpecFile) :
                                  ConfigSpecParseUtil.getConfigSpec(myViewName);

    myConfigSpecWasChanged = checkCSChange && configSpecFile != null && !myConfigSpec.equals(oldConfigSpec);

    if (myConfigSpecWasChanged && myCache != null) {
      myCache.clearCaches();
    }
    
    myNormalizedViewName = cutOffLastSeparatorWithDot();

    updateCurrentView();

    final GeneralCommandLine generalCommandLine = new GeneralCommandLine();
    generalCommandLine.setExePath("cleartool");
    generalCommandLine.addParameter("-status");
    generalCommandLine.setWorkDirectory(myViewName);
    myProcess = ourProcessExecutor.createProcess(generalCommandLine);

  }

  public static InteractiveProcess createInteractiveProcess(final Process process) {
    return new ClearCaseInteractiveProcess(process);
  }
  
  private String cutOffLastSeparatorWithDot() {
    return myViewName.endsWith(File.separator + ".") ? myViewName.substring(0, myViewName.length() - 2) : myViewName;
  }

  private String cutOffLastSeparator(String viewName) {
    if (viewName.endsWith(File.separator)) {
      viewName = viewName.substring(0, viewName.length() - 1);
    }
    return viewName;
  }

  private String normalizeFileSeparators(String viewName) {
    if (File.separatorChar == '/') {
      viewName = viewName.replace('\\', '/');
    } else {
      viewName = viewName.replace('/', '\\');
    }
    return viewName;
  }

  public void dispose() throws IOException {
    try {
      myProcess.destroy();
    } finally {
      ourLogger.close();
    }
    

  }

  public String getViewName() {
    return myViewName;
  }

  @Nullable
  public DirectoryChildElement getLastVersionElement(final String pathWithoutVersion, final DirectoryChildElement.Type type)
    throws VcsException {
    final Version lastElementVersion = getLastVersion(pathWithoutVersion, DirectoryChildElement.Type.FILE.equals(type));

    if (lastElementVersion != null) {
      return new DirectoryChildElement(type, extractElementPath(pathWithoutVersion), lastElementVersion.getVersion(),
                                       pathWithoutVersion + lastElementVersion.getWholeName(), lastElementVersion.getWholeName(), pathWithoutVersion);
    } else {
      Loggers.VCS.info("ClearCase: last element version not found for " + pathWithoutVersion);
      return null;
    }
  }

  public Version getLastVersion(String path, final boolean isFile) throws VcsException {
    try {
      final VersionTree versionTree = new VersionTree();

      readVersionTree(path, versionTree);

      return getLastVersion(path, versionTree, isFile);
    } catch (IOException e) {
      throw new VcsException(e);
    }

  }

  @Nullable
  private Version getLastVersion(final String path, final VersionTree versionTree, final boolean isFile) throws IOException, VcsException {
    final String elementPath = extractElementPath(path);

    if (myChangesToIgnore.containsKey(elementPath)) {
      final List<HistoryElement> historyElements = myChangesToIgnore.get(elementPath);
      if (historyElements != null) {
        for (HistoryElement element : historyElements) {
          Loggers.VCS.info("ClearCase: element " + elementPath + ", branch ignored: " + element.getObjectVersion());
          versionTree.pruneBranch(element.getObjectVersion());
        }
      }

    }

    return myConfigSpec.getCurrentVersion(getPathWithoutVersions(path), versionTree, isFile);
  }

  private static String extractElementPath(final String fullPath) {
    String currentPath = fullPath;
    String result = "";
    while (true) {
      final int fileSep = currentPath.lastIndexOf(File.separator);
      String dirName = currentPath.substring(fileSep + 1);
      if (result.length() > 0) {
        result = dirName + File.separator + result;
      } else {
        result = dirName;
      }
      currentPath = currentPath.substring(0, fileSep);
      final int versSep = currentPath.lastIndexOf(CCParseUtil.CC_VERSION_SEPARATOR);
      if (versSep >= 0) {
        currentPath = currentPath.substring(0, versSep);
      } else {
        result = currentPath + File.separator + result;
        break;
      }
    }
    return result;
  }

  private void readVersionTree(final String path, final VersionTree versionTree) throws IOException, VcsException {
    final InputStream inputStream = executeAndReturnProcessInput(new String[]{"lsvtree", "-obs", "-all", path});

    final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));


    try {

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().length() > 0) {
          String elementVersion = readVersion(line);
          //System.out.println("add version " + elementVersion);
          versionTree.addVersion(elementVersion);
        }
      }

      final List<HistoryElement> deletedVersions = myDeletedVersions.get(getPathWithoutVersions(path));
      for (HistoryElement deletedVersion : deletedVersions) {
        versionTree.addVersion(normalizeVersion(deletedVersion.getObjectVersion()));
      }
    } finally {
      reader.close();
    }
  }

  public static String readVersion(final String line) {
    final int versSeparatorIndex = line.lastIndexOf(CCParseUtil.CC_VERSION_SEPARATOR);
    String result = line.substring(versSeparatorIndex + CCParseUtil.CC_VERSION_SEPARATOR.length());
    return normalizeVersion(result);
  }

  private static String normalizeVersion(String version) {
    if (version.startsWith(File.separator)) {
      version = version.substring(1);
    }
    return version;
  }

  public static void writeBinary(final InputStream input, final OutputStream output) throws IOException {
    final byte[] buffer = TCStreamUtil.getByteBuffer();
    try {
      int read = 0;
      while (read != -1) {
        read = input.read(buffer, 0, buffer.length);
        if (input.available() == 0) break;
        if (read != -1) {
          output.write(buffer, 0, read);
        }
      }
    } finally {
      TCStreamUtil.disposeByteBuffer(buffer);
    }

  }


  public InputStream getChanges(String since) throws IOException, VcsException {
    /*
    execute(new String[]{"lshistory", "-all","-since", since, "-fmt",FORMAT,myViewName});
    return readFromProcessInput();
    */

    return executeSimpleProcess(myViewName, new String[]{"lshistory", "-all", "-since", since, "-fmt", FORMAT, myViewName});
  }

  public InputStream listDirectoryContent(final String dirPath) throws ExecutionException, IOException, VcsException {
    return executeAndReturnProcessInput(new String[]{"ls", "-long", dirPath});
  }

  public void loadFileContent(final File tempFile, final String line)
    throws ExecutionException, InterruptedException, IOException, VcsException {
    myProcess.copyFileContentTo(line, tempFile);
  }


  public void collectChangesToIgnore(final String lastVersion) throws VcsException {
    try {
      CCParseUtil.processChangedFiles(this, lastVersion, null, new ChangedFilesProcessor() {
        public void processChangedFile(final HistoryElement element) {
          myChangesToIgnore.putValue(element.getObjectName(), element);
        }


        public void processChangedDirectory(final HistoryElement element) throws IOException, VcsException {
          myChangesToIgnore.putValue(element.getObjectName(), element);
        }

        public void processDestroyedFileVersion(final HistoryElement element) throws VcsException {
          myDeletedVersions.putValue(element.getObjectName(), element);        
        }
      });
    } catch (ParseException e) {
      throw new VcsException(e);
    } catch (IOException e) {
      throw new VcsException(e);
    }

  }

  public boolean versionIsInsideView(String objectPath, final String objectVersion, final boolean isFile)
    throws IOException, VcsException {
    final VersionTree versionTree = new VersionTree();

    readVersionTree(objectPath, versionTree);

    final Version lastVersion = getLastVersion(objectPath, versionTree, isFile);
    
    if (lastVersion == null) {
      return false;
    }

    final Version versionByPath = versionTree.findVersionByPath(objectVersion);

    if (versionByPath == null) {
      Loggers.VCS.info("ClearCase: version by path not found for " + objectPath + " by " + objectVersion);
      return false;
    }

    return true;
  }

  public String testConnection() throws IOException, VcsException {
    final StringBuffer result = new StringBuffer();

    final String[] params;
    if (myUCMSupported) {
      params = new String[]{"lsstream", "-long"};
    }
    else {
      params = new String[]{"describe", myViewName};
    }

    final InputStream input = executeAndReturnProcessInput(params);
    final BufferedReader reader = new BufferedReader(new InputStreamReader(input));

    try {
      String line;
      while ((line = reader.readLine()) != null) {
        result.append(line).append('\n');
      }
    } finally {
      reader.close();
    }

    return result.toString();
  }

  public static InputStream executeSimpleProcess(String viewPath, String[] arguments) throws VcsException {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("cleartool");
    commandLine.setWorkDirectory(viewPath);
    commandLine.addParameters(arguments);

    if (LOG_COMMANDS) {
      Loggers.VCS.info("ClearCase executing " + commandLine.getCommandLineString());
      ourLogger.log("\n" + commandLine.getCommandLineString());
    }
    LOG.info("simple execute: " + commandLine.getCommandLineString());
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();
    if (LOG_COMMANDS) {
      ourLogger.log("\n");
    }

    final ExecResult execResult;
    try {
      execResult = ourProcessExecutor.execute(commandLine, createProcessHandlerListener(out, err)); 
    } catch (ExecutionException e) {
      throw new VcsException(e);
    }
    LOG.debug("result: " + execResult.toString());

    final int processResult = execResult.getExitCode();
    if (processResult != 0) {
      if (err.size() > 0) {
        final String errDescr = new String(err.toByteArray());
        if (!errDescr.contains("A snapshot view update is in progress") && !errDescr.contains("An update is already in progress")) {
          throw new VcsException(errDescr);
        } else {
          return new ByteArrayInputStream(out.toByteArray());
        }
      } else {
        throw new VcsException("Process " + commandLine.getCommandLineString() + " returns " + processResult);
      }
    } else {
      if (LOG_COMMANDS) {
        ourLogger.log("\n" + new String(out.toByteArray()));
      }
      return new ByteArrayInputStream(out.toByteArray());
    }
  }

  private static ProcessListener createProcessHandlerListener(
    final ByteArrayOutputStream out,
    final ByteArrayOutputStream err
  ) {
    return new ProcessListener() {
      public void onOutTextAvailable(final byte[] buff, final int offset, final int length, final OutputStream output) {
        
      }

      public void onErrTextAvailable(final byte[] buff, final int offset, final int length, final OutputStream output) {
        
      }

      public void onOutTextAvailable(final String text, final OutputStream output){
        if (LOG_COMMANDS) {
          ourLogger.log(text);
        }
        try {
          out.write(text.getBytes());
        } catch (IOException e) {
          //ignore
        }
      }

      public void onErrTextAvailable(final String text, final OutputStream output) {
        try {
          err.write(text.getBytes());
          output.write('\n');
          output.flush();
        } catch (IOException e) {
          //ignore
        }

      }

    };
  }

  public String getVersionDescription(final String fullPath) {
    try {
      String[] params = {"describe", "-fmt", "%c", "-pname", fullPath};
      final InputStream input = executeAndReturnProcessInput(params);
      final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      try {
        final String line = reader.readLine();
        if (line != null) {
          return line;
        } else {
          return "";
        }
      } finally {
        reader.close();
      }
    } catch (Exception e) {
      //ignore
      return "";
    }
  }

  private InputStream executeAndReturnProcessInput(final String[] params) throws IOException, VcsException {
    return myProcess.executeAndReturnProcessInput(params);
  }

  public String getObjectRelativePathWithVersions(final String path, final boolean isFile) throws VcsException {
    return getRelativePathWithVersions(path, 0, 1, true, isFile);

  }

  public String getParentRelativePathWithVersions(final String path, final boolean isFile) throws VcsException {
    return getRelativePathWithVersions(path, 1, 1, true, isFile);

  }

  private String getRelativePathWithVersions(String path,
                                             final int skipAtEndCount,
                                             int skipAtBeginCount,
                                             final boolean appentVersion,
                                             final boolean isFile)
    throws VcsException {
    final List<CCPathElement> pathElementList = CCPathElement.splitIntoPathAntVersions(path, getViewName(), skipAtBeginCount);
    for (int i = 0; i < pathElementList.size() - skipAtEndCount; i++) {
      final CCPathElement pathElement = pathElementList.get(i);
      if (appentVersion) {
        if (!pathElement.isIsFromViewPath() && pathElement.getVersion() == null) {
          final Version lastVersion = getLastVersion(CCPathElement.createPath(pathElementList, i + 1, appentVersion), isFile);
          if (lastVersion != null) {
            pathElement.setVersion(lastVersion.getWholeName());
          }
        }
      } else {
        pathElement.setVersion(null);
      }
    }

    return CCPathElement.createRelativePathWithVersions(pathElementList);
  }

  public String getPathWithoutVersions(String path)
    throws VcsException {
    return CCPathElement.createPathWithoutVersions(CCPathElement.splitIntoPathAntVersions(path, getViewName(), 0));
  }
  
  public void updateCurrentView() throws VcsException {
    updateView(getViewName());
  }

  public static void updateView(final String viewPath) throws VcsException {
    Semaphore semaphore;
    synchronized (viewName2Semaphore) {
      semaphore = viewName2Semaphore.get(viewPath);
      if (semaphore == null) {
        semaphore = new Semaphore(1);
        viewName2Semaphore.put(viewPath, semaphore);
      }
    }

    try {
      semaphore.acquire();
      executeSimpleProcess(viewPath, new String[]{"update", "-force", "-rename", "-log", UPDATE_LOG}).close();
    } catch (VcsException e) {
      if (e.getLocalizedMessage().contains("is not a valid snapshot view path")) {
        //ignore, it is dynamic view
      } else {
        throw e;
      }
    } catch (IOException e) {
      throw new VcsException(e);
    } catch (InterruptedException e) {
      throw new VcsException(e);
    } finally {
      semaphore.release();
      FileUtil.delete(new File(viewPath, UPDATE_LOG));
    }
  }

  public String getObjectRelativePathWithoutVersions(final String path, final boolean isFile) throws VcsException {
    return getRelativePathWithVersions(path, 0, 0, false, isFile);
  }

  public boolean isInsideView(final String objectName) {
    return CCPathElement.isInsideView(objectName, myNormalizedViewName);
  }

  public String getRelativePath(final String path) {
    final String viewName = myNormalizedViewName;
    if (path.length() == viewName.length()) {
      return ".";
    } else {
      return path.substring(viewName.length() + 1);
    }

  }

  public static InputStream getConfigSpecInputStream(final String viewName) throws VcsException {
    return executeSimpleProcess(viewName, new String[]{"catcs"});
    
  }

  public void processAllVersions(final String version, final VersionProcessor versionProcessor, boolean processRoot, boolean useCache) throws VcsException {
    
    if (useCache && myCache != null) {
      myCache.getCache(version, getViewName(), IncludeRule.createDefaultInstance(), myRoot)
        .processAllVersions(versionProcessor, processRoot, this);
    }
    else {
      final String directoryVersion = prepare(version).getWholeName();
    
      String dirPath = getViewName() + CCParseUtil.CC_VERSION_SEPARATOR + directoryVersion;
    
      if (processRoot) {
        versionProcessor.processDirectory(dirPath, "", getViewName(), directoryVersion, this);
      }

      try {
        processAllVersionsInternal(dirPath, versionProcessor, "./");
      } finally {
        if (processRoot) {
          versionProcessor.finishProcessingDirectory();
        }      
      }      
    }

    
  }

  public void processAllVersions(final String fullPath, String relPath, final VersionProcessor versionProcessor) throws VcsException {
    processAllVersionsInternal(fullPath, versionProcessor, relPath);
    
  }
  
  private void processAllVersionsInternal(final String dirPath,
                                          final VersionProcessor versionProcessor,
                                          String relativePath
  )
    throws VcsException {
    List<DirectoryChildElement> subfiles = CCParseUtil.readDirectoryVersionContent(this, dirPath);


    for (DirectoryChildElement subfile : subfiles) {
      if (subfile.getStringVersion() != null) {
        final String fileFullPath = subfile.getFullPath();
        String newRelPath = "./".equals(relativePath) ? CCParseUtil.getFileName(subfile.getPath()) : relativePath + File.separator + CCParseUtil.getFileName(subfile.getPath());
        String elemPath = getViewName() + File.separator + newRelPath;
        if (subfile.getType() == DirectoryChildElement.Type.FILE) {
          final ClearCaseFileAttr fileAttr = loadFileAttr(subfile.getPathWithoutVersion() + CCParseUtil.CC_VERSION_SEPARATOR);
          versionProcessor.processFile(fileFullPath, newRelPath, elemPath, subfile.getStringVersion(), this, fileAttr.isIsText(), fileAttr.isIsExecutable());
        } else {
          versionProcessor.processDirectory(fileFullPath, newRelPath, elemPath, subfile.getStringVersion(), this);
          try {
            processAllVersionsInternal(fileFullPath, versionProcessor, newRelPath);
          } finally {
            versionProcessor.finishProcessingDirectory();
          }
        }
      }

    }
    
  }

  public Version prepare(final String lastVersion) throws VcsException {
    collectChangesToIgnore(lastVersion);
    final Version viewLastVersion = getLastVersion(getViewName(), false);
    if (viewLastVersion == null) {
      throw new VcsException("Cannot get version in view '" + getViewName() + "' for the directory " +
                             getViewName());
    }
    return viewLastVersion;
  }

  public void mklabel(final String version, final String pname, final String label) throws VcsException, IOException {
    //    //cleartool mklabel -version main\lesya_testProject\1 test_label C:\ClearCaseTests\lesya_testProject\lesyaTestVOB\project_root\f1\f14@@\main\lesya_testProject\4\dir\main\lesya_testProject\6\newFileName.txt
    try {
      InputStream inputStream = executeAndReturnProcessInput(new String[]{"mklabel", "-version", version, label, pname});
      try {
        inputStream.close();
      } catch (IOException e) {
        //ignore
      }
    } catch (IOException e) {
      if (!e.getLocalizedMessage().contains("already on element")) throw e;
      else {
        try {
          myProcess.destroy();
        } catch (Throwable e1) {
          //ignore
        }
        try {
          final GeneralCommandLine generalCommandLine = new GeneralCommandLine();
          generalCommandLine.setExePath("cleartool");
          generalCommandLine.addParameter("-status");
          generalCommandLine.setWorkDirectory(myViewName);
          myProcess = ourProcessExecutor.createProcess(generalCommandLine);
        } catch (ExecutionException e1) {
          throw new VcsException(e1.getLocalizedMessage(), e1);
        }
      }
    } catch (VcsException e) {
      if (!e.getLocalizedMessage().contains("already on element")) throw e;
      else {
        try {
          myProcess.destroy();
        } catch (Throwable e1) {
          //ignore
        }
        try {
          final GeneralCommandLine generalCommandLine = new GeneralCommandLine();
          generalCommandLine.setExePath("cleartool");
          generalCommandLine.addParameter("-status");
          generalCommandLine.setWorkDirectory(myViewName);
          myProcess = ourProcessExecutor.createProcess(generalCommandLine);
        } catch (ExecutionException e1) {
          throw new VcsException(e1.getLocalizedMessage(), e1);
        }
        
      }
    }
  }

  public ClearCaseFileAttr loadFileAttr(final String path) throws VcsException {
    try {
      final InputStream input = executeAndReturnProcessInput(new String[]{"describe", cutOffVersion(path)});
      try {
        return ClearCaseFileAttr.readFrom(input);
      } finally {
        try {
          input.close();
        } catch (IOException e1) {
          //ignore
        }
      }
    } catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private String cutOffVersion(final String path) {
    final int versionSep = path.lastIndexOf(CCParseUtil.CC_VERSION_SEPARATOR);
    if (versionSep != -1) {
      return path.substring(0, versionSep + CCParseUtil.CC_VERSION_SEPARATOR.length());
    }
    else return path + CCParseUtil.CC_VERSION_SEPARATOR;
  }

  public static class ClearCaseInteractiveProcess extends InteractiveProcess {
    private final Process myProcess;

    public ClearCaseInteractiveProcess(final Process process) {
      super(process.getInputStream(), process.getOutputStream());
      myProcess = process;
    }

    protected void execute(final String[] args) throws IOException {
      super.execute(args);
      final StringBuffer commandLine = new StringBuffer();
      commandLine.append("cleartool");    
      for (String arg : args) {
        commandLine.append(' ');
        if (arg.contains(" ")) {
          commandLine.append("'").append(arg).append("'");
        } else {
          commandLine.append(arg);
        }

      }
      if (LOG_COMMANDS) {
        Loggers.VCS.info("ClearCase executing " + commandLine.toString());
        ourLogger.log("\n" + commandLine.toString());
      }
      LOG.info("interactive execute: " + commandLine.toString());
    }

    protected boolean isEndOfCommandOutput(final String line, final String[] params) throws IOException {
      final Matcher matcher = END_OF_COMMAND_PATTERN.matcher(line);
      if (matcher.matches()) {
        if (!"0".equals(matcher.group(2))) {
          String error = readError();
          throw new IOException("Error executing " + StringUtil.join(params, " ") + ": " + error);
        }
        return true;
      }
        
      return false;
    }

    protected void lineRead(final String line) {
      super.lineRead(line);
      if (LOG_COMMANDS) {
        ourLogger.log("\n" + line);
      }          
      LOG.info("output line read: " + line);
    }

    protected InputStream getErrorStream() {
      return myProcess.getErrorStream();
    }

    protected void destroyOSProcess() {
      myProcess.destroy();
    }

    protected void executeQuitCommand() throws IOException {
      super.executeQuitCommand();
      execute(new String[]{"quit"});
    }

    public void copyFileContentTo(final String version, final File destFile) throws IOException {
      final InputStream input = executeAndReturnProcessInput(new String[]{"get", "-to", destFile.getAbsolutePath(), version});
      input.close();      
    }
  }
}
