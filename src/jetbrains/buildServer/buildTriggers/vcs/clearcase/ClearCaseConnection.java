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
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.CommandLineExecutor;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.ProcessListener;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.configSpec.ConfigSpec;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.configSpec.ConfigSpecParseUtil;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.process.ClearCaseFacade;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.process.InteractiveProcess;
import jetbrains.buildServer.buildTriggers.vcs.clearcase.process.InteractiveProcessFacade;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.MultiMap;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"SimplifiableIfStatement"})
public class ClearCaseConnection {
  private final ViewPath myViewPath;

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

  public final static String FORMAT = "%u"                                    //user
                                      + DELIMITER + "%Nd"                     //date
                                      + DELIMITER + "%En"                     //object name
                                      + DELIMITER + "%m"                      //object kind
                                      + DELIMITER + "%Vn"                     //object version
                                      + DELIMITER + "%o"                      //operation
                                      + DELIMITER + "%e"                      //event
                                      + DELIMITER + "%Nc"                     //comment
                                      + DELIMITER + "%[version_predecessor]p" //previous version
                                      + DELIMITER + "%[activity]p"            //activity
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
  };

  private final boolean myConfigSpecWasChanged;

  public boolean isConfigSpecWasChanged() {
    return myConfigSpecWasChanged;
  }

  public ClearCaseConnection(ViewPath viewPath,
                             boolean ucmSupported,
                             VcsRoot root,
                             final boolean checkCSChange) throws Exception {

    // Explanation of config specs at:
    // http://www.philforhumanity.com/ClearCase_Support_17.html

    Loggers.VCS.info(String.format("Creating clearcase connection , root=%s, viewPath=%s, ucmSupported pc=%s, checkCSChange=%s", root, viewPath, ucmSupported, checkCSChange));
    myUCMSupported = ucmSupported;
    myViewPath = viewPath;

    if (!isClearCaseView(myViewPath.getClearCaseViewRoot())) {
      throw new VcsException("Invalid ClearCase view: \"" + myViewPath.getClearCaseViewRoot() + "\"");
    }

    final File configSpecFile = new File("cs");

    ConfigSpec oldConfigSpec = null;
    if (checkCSChange && configSpecFile.isFile()) {
      oldConfigSpec = ConfigSpecParseUtil.getConfigSpecFromStream(myViewPath.getClearCaseViewRootAsFile(), new FileInputStream(configSpecFile), configSpecFile);
    }

    myConfigSpec = checkCSChange ?
        ConfigSpecParseUtil.getAndSaveConfigSpec(myViewPath, configSpecFile) :
        ConfigSpecParseUtil.getConfigSpec(myViewPath);

    myConfigSpec.setViewIsDynamic(isViewIsDynamic());

    myConfigSpecWasChanged = checkCSChange && !myConfigSpec.equals(oldConfigSpec);


    if (!myConfigSpec.isUnderLoadRules(getClearCaseViewPath(), myViewPath.getWholePath())) {
      throw new VcsException("The path \"" + myViewPath.getWholePath() + "\" is not loaded by ClearCase view \"" + myViewPath.getClearCaseViewRoot() + "\" according to its config spec.");
    }

    updateCurrentView();

    final GeneralCommandLine generalCommandLine = new GeneralCommandLine();
    generalCommandLine.setExePath("cleartool");
    generalCommandLine.addParameter("-status");
    generalCommandLine.setWorkDirectory(getViewWholePath());
    myProcess = ourProcessExecutor.createProcess(generalCommandLine);
  }

  public static InteractiveProcess createInteractiveProcess(final Process process) {
    return new ClearCaseInteractiveProcess(process);
  }

  public void dispose() throws IOException {
    try {
      myProcess.destroy();
    } finally {
      ourLogger.close();
    }
  }

  /**
   *  Returns the view path which has been declared in the vcs root.
   * eg : C:\eprom\views\dev\isl_prd_mdl_dev\isl\product_model
   * @return the vcs root view path
   */
  public String getViewWholePath() {
    return myViewPath.getWholePath();
  }


  public InputStream getHistory(String since) throws IOException, VcsException {
    String[] args;
    if (this.myUCMSupported) {
      String streamName = getStreamName();
      args = new String[]{"lshistory", "-r", "-nco", "-branch", streamName, "-since", since, "-fmt", FORMAT, myViewPath.getWholePath()};
    } else {
      args = new String[]{"lshistory", "-all", "-since", since, "-fmt", FORMAT, insertDotAfterVOB(getViewWholePath())};
    }
    return executeSimpleProcess(getViewWholePath(), args);
  }

  public void loadFileContent(final File tempFile, final String line)
    throws ExecutionException, InterruptedException, IOException, VcsException {
    myProcess.copyFileContentTo(this, line, tempFile);
  }


  public void collectChangesToIgnore(final String lastVersion) throws VcsException {
    try {
      CCParseUtil.processChangedFiles(this, lastVersion, null, new ChangedFilesProcessor() {
        public void processChangedFile(final HistoryElement element) {
          myChangesToIgnore.putValue(element.getObjectName(), element);
          LOG.debug("Change was ignored: changed file " + element.getLogRepresentation());
        }

        public void processChangedDirectory(final HistoryElement element) throws IOException, VcsException {
          myChangesToIgnore.putValue(element.getObjectName(), element);
          LOG.debug("Change was ignored: changed directory " + element.getLogRepresentation());
        }

        public void processDestroyedFileVersion(final HistoryElement element) throws VcsException {
          myDeletedVersions.putValue(element.getObjectName(), element);        
          LOG.debug("Change was ignored: deleted version of " + element.getLogRepresentation());
        }
      });
    } catch (ParseException e) {
      throw new VcsException(e);
    } catch (IOException e) {
      throw new VcsException(e);
    }

  }

  public String testConnection() throws IOException, VcsException {
    final StringBuffer result = new StringBuffer();

    final String[] params;
    if (myUCMSupported) {
      params = new String[]{"lsstream", "-long"};
    }
    else {
      params = new String[]{"describe", insertDotAfterVOB(getViewWholePath())};
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
    Loggers.VCS.info(String.format("%s (simple,dir=%s)", commandLine.getCommandLineString(), viewPath));
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();
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

  /**
   * I don't know why they do this.
   *
   * @param fullPath
   * @return
   */
  public String getVersionDescription(final String fullPath) {
    try {
      String[] params = {"describe", "-fmt", "%c", "-pname", insertDotAfterVOB(fullPath)};
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


  public void updateCurrentView() throws VcsException {
    //updateView(getViewWholePath());
  }

  private boolean isViewIsDynamic() throws VcsException, IOException {
    final InputStream inputStream = executeSimpleProcess(getViewWholePath(), new String[] {"lsview", "-cview", "-long"});
    final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

    try {
      String line = reader.readLine();
      while (line != null) {
        if (line.startsWith("View attributes:")) {
          return !line.contains("snapshot");
        }
        line = reader.readLine();
      }
    }
    finally {
      reader.close();
    }

    return true;
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
        LOG.debug("Please ignore the error above if you use dynamic view.");
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

  public String getClearCaseViewPath() {
    return myViewPath.getClearCaseViewRoot();
  }

  public static InputStream getConfigSpecInputStream(final String viewName) throws VcsException {
    return executeSimpleProcess(viewName, new String[]{"catcs"});
  }


  public ClearCaseFileAttr loadFileAttributes(final String path) throws VcsException {
    try {
      final InputStream input = executeAndReturnProcessInput(new String[]{"describe", path});
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

  @NotNull
  public static String getClearCaseViewRoot(@NotNull final String viewPath) throws VcsException, IOException {
    final String normalPath = CCPathElement.normalizePath(viewPath);

    final InputStream inputStream = executeSimpleProcess(normalPath, new String[] {"pwv", "-root"});
    final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

    try {
      final String viewRoot = reader.readLine();
      if (viewRoot == null || "".equals(viewRoot.trim())) {
        final int sep = normalPath.indexOf(File.separatorChar);
        if (sep == -1) return normalPath;
        return normalPath.substring(0, sep);
      }
      return viewRoot;
    }
    finally {
      reader.close();
    }
  }

  public static boolean isClearCaseView(@NotNull final String ccViewPath) throws IOException {
    try {
      final String normalPath = CCPathElement.normalizePath(ccViewPath);
      return normalPath.equalsIgnoreCase(getClearCaseViewRoot(normalPath));
    } catch (VcsException ignored) {
      return false;
    }
  }

  /**
   * Example of transformation :
   * <p/>
   * C:\eprom\views\dev\isl_prd_mdl_dev\isl\product_model\component\isl_product_model\component-dev.xml
   *
   * @param fullPath
   * @return
   * @throws VcsException
   */
  @NotNull
  private String insertDotAfterVOB(@NotNull final String fullPath) throws VcsException {
    final List<CCPathElement> filePath = CCPathElement.splitIntoPathElements(CCPathElement.normalizePath(fullPath));
    final List<CCPathElement> ccViewPath = CCPathElement.splitIntoPathElements(myViewPath.getClearCaseViewRoot());

    if (filePath.size() < ccViewPath.size() + 1) return fullPath;

    final CCPathElement vobElement = filePath.get(ccViewPath.size());

    if (vobElement.getVersion() == null) return fullPath;

    final CCPathElement dotElement = new CCPathElement(".", false);

    dotElement.setVersion(vobElement.getVersion());
    vobElement.setVersion(null);

    filePath.add(ccViewPath.size() + 1, dotElement);

    return CCPathElement.createPath(filePath, filePath.size(), true);
  }


  /**
   * Creates a dynamic view in time.
   * @param version a clearcase time (ex 15-Apr-2009.09:00:00)
   * @return the clearcase tag of the dynamic view
   * @throws VcsException if a cc error occurs
   * @throws IOException if an io error occurs
   * @throws java.text.ParseException if the date couldn't be parsed
   */
  public String createDynamicViewAtDate(String version) throws VcsException, IOException, ParseException {
    String uuid = String.valueOf(new Date().getTime());
    Date date = CCParseUtil.toDate(version);
    String escapedDate = CCParseUtil.escapeDate(date);
    String streamName = getStreamName();
    String user = "teamcity";
    String dynViewTag = StringUtils.lowerCase(user + "_" + streamName + "_" + escapedDate + "_" + uuid);
    String hostname = StringUtils.lowerCase(getHostname());
    String localViewDir = getViewWholePath();
    String dynViewDir = "M:\\" + dynViewTag;

    // create dynamic view
    LOG.info(String.format("Creating dynamic view %s", dynViewTag));
    executeSimpleProcess(localViewDir, new String[]{"mkview", "-tag", dynViewTag, "-stream", streamName + "@\\ideapvob", "-stg", hostname + "_ccstg_c_views"});

    // mount vob if necessary
    String vobToMount = "\\isl";
    if (!new File(new File(dynViewDir).getAbsolutePath() + vobToMount).exists()) {
      executeSimpleProcess(dynViewDir, new String[]{"mount", vobToMount});
    }

    // put config spec of local view into dynamic view with the -time attribute
    String csWithDate = "config_spec_" + dynViewTag + ".cs";
    LOG.info(String.format("Altering configspec of view %s with -time %s", dynViewTag, version));
    InputStream inputStream = executeSimpleProcess(dynViewDir, new String[]{"catcs"});
    final BufferedWriter writer = new BufferedWriter(new FileWriter(csWithDate));
    final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    try {
      String line = reader.readLine();
      while (line != null) {
        if (line.endsWith("LATEST")) {
          line = line + " -time " + version;
        }
        writer.write(line + "\n");
        line = reader.readLine();
      }

    }
    finally {
      reader.close();
      writer.close();

    }
    File file = new File(csWithDate);
    executeSimpleProcess(dynViewDir, new String[]{"setcs", file.getAbsolutePath()});
    file.delete();
    return dynViewTag;
  }

  /** Removes a view.
   *
   * @param viewTag the tag of the view to delete. It can be null if the view creation failed in the first place.
   * @throws VcsException if an error occurs, for example if the view doesn't exist
   */
  public void removeView(String viewTag) throws VcsException {
    if (viewTag != null) {
      executeSimpleProcess(getViewWholePath(), new String[]{"rmview", "-tag", viewTag});
    }
  }

  public String getDynamicViewDirectory(String fromViewTag) {
    return "M:\\" + fromViewTag;
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
      Loggers.VCS.info(commandLine.toString());
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

    public void copyFileContentTo(final ClearCaseConnection connection, final String version, final File destFile) throws IOException, VcsException {
      final InputStream input = executeAndReturnProcessInput(new String[]{"get", "-to", connection.insertDotAfterVOB(destFile.getAbsolutePath()), connection.insertDotAfterVOB(version)});
      input.close();
    }
  }

  public boolean isUCM() {
    return myUCMSupported;
  }
  
  private String getStreamName() throws VcsException, IOException {
    InputStream inputStream = executeSimpleProcess(myViewPath.getClearCaseViewRoot(), new String[]{"lsstream", "-fmt", "%n"});
    return new BufferedReader(new InputStreamReader(inputStream)).readLine();
  }

  /**
   * Return the computer full name. <br>
   *
   * @return the name or <b>null</b> if the name cannot be found
   */
  public static String getHostname() {
    String hostName = null;
    try {
      final InetAddress addr = InetAddress.getLocalHost();
      hostName = addr.getHostName();
    } catch (final Exception e) {
    }//end try
    return hostName;
  }

  public static boolean exists(String viewTag) {
    // TODO.DANIEL : implement
    throw new UnsupportedOperationException();
  }


  public ViewPath getViewPath() {
    return myViewPath;
  }
}
