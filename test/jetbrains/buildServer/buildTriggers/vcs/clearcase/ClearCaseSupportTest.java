package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import junit.framework.TestCase;
import org.apache.log4j.xml.DOMConfigurator;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

/**
 * TODO.DANIEL : create independant tests
 *
 * @author Gilles Philippart
 * 
 */
public class ClearCaseSupportTest extends TestCase {

  private ClearCaseSupport ccs;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Locale.setDefault(Locale.US);
    URL url = getClass().getResource("log4j.xml");
    DOMConfigurator.configure(url);
    Logger.setFactory(new Log4jFactory());
    ccs = new ClearCaseSupport();
  }

  public void testBuildPatch() throws VcsException, IOException {
    // configure the VCS root
    String ruleTo = "isl_prd_mdl_dev/isl/product_model";
    String viewPath = "C:/eprom/views/dev/isl_prd_mdl_dev/isl/product_model";
    String streamName = "ISL_PRD_MDL Dev";
    String from = "15-Apr-2009.09:00:00";
    String to = "29-Apr-2009.21:00:00";
    collectAndBuild(ccs, ruleTo, viewPath, streamName, from, to);
  }

  private void collectAndBuild(ClearCaseSupport ccs, String ruleTo, String viewPath, String streamName, String from,
                               String to) throws VcsException, IOException {
    MyVcsRoot myVcsRoot = new MyVcsRoot("Clearcase", streamName, 1, 2);
    myVcsRoot.addProperty(ClearCaseSupport.VIEW_PATH, viewPath);

    IncludeRule includeRule = new IncludeRule(".", ruleTo, null);
    //cleartool lshistory -r -nco -branch ISL_PRD_MDL_Dev -since 15-Apr-2009.09:00:00 -fmt %u#--#%Nd#--#%En#--#%m#--#%Vn#--#%o#--#%e#--#%Nc#--#%[activity]p###----###\n C:\eprom\views\dev\isl_prd_mdl_dev\isl\product_model
    ccs.collectChanges(myVcsRoot, from, to, includeRule);
    ccs.buildPatch(myVcsRoot, from, to, new MyAbstractPatchBuilder(), includeRule);
  }

  public void testFormat() throws ParseException {
    Date d = CCParseUtil.toDate("15-Apr-2009.09:00:00");
    String configspecTime = CCParseUtil.toConfigSpecDate(d);
    assertEquals("configspec time", "15-Apr-2009 09.00:00", configspecTime);
  }


}
