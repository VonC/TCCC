package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import junit.framework.TestCase;
import junit.framework.Assert;
import org.apache.log4j.xml.DOMConfigurator;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

/**
 * TODO.DANIEL : create independant tests
 *
 * @author Gilles Philippart
 * 
 */
public class ClearCaseSupportTest extends TestCase {

  private static org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(ClearCaseSupportTest.class);

  private ClearCaseSupport ccs;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
//    Locale.setDefault(Locale.US);
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
    collectAndBuild(ccs, ruleTo, viewPath, streamName, "01-Apr-2009.09:00:00", "29-Apr-2009.21:00:00");
  }

  private void collectAndBuild(ClearCaseSupport ccs, String ruleTo, String viewPath, String streamName, String from,
                               String to) throws VcsException, IOException {
    MyVcsRoot myVcsRoot = new MyVcsRoot("Clearcase", streamName, 1, 2);
    myVcsRoot.addProperty(ClearCaseSupport.VIEW_PATH, viewPath);

    IncludeRule includeRule = new IncludeRule(".", ruleTo, null);
    ccs.collectChanges(myVcsRoot, from, to, includeRule);
    MyAbstractPatchBuilder builder = new MyAbstractPatchBuilder();
    ccs.buildPatch(myVcsRoot, from, to, builder, includeRule);
    LOG.info(String.format("Patch builder summary %s", builder.getSummary()));
  }

  public void testFormatDate() throws ParseException {
    CCParseUtil.toDate("10-May-2009.11:56:43");
  }

  public void testConfigSpecDate() throws ParseException {
    Date d = CCParseUtil.toDate("15-Apr-2009.09:00:00");
    String configspecTime = CCParseUtil.toConfigSpecDate(d);
    assertEquals("configspec time", "15-avr.-2009 09.00:00", configspecTime);
  }


  public void testGetFileContent() throws VcsException {
    MyVcsRoot root = new MyVcsRoot("Clearcase", "ISL_PRD_MDL Dev", 1, 2);
    root.addProperty(ClearCaseSupport.VIEW_PATH, "C:/eprom/views/dev/isl_prd_mdl_dev/isl/product_model");
    byte[] content = ccs.getContent("product_model/component/isl_product_model/component-dev.xml", root, "10-May-2009.11:56:43");
    Assert.assertTrue(content.length > 0);
  }


}
