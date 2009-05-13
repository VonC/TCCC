/**
 * 
 */
package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import java.net.URL;

import jetbrains.buildServer.log.Log4jFactory;

import org.apache.log4j.xml.DOMConfigurator;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.intellij.openapi.diagnostic.Logger;

/**
 * Stand-alone tests for TeamCity ClearCase plugin. <br />
 * Test BuildPatch and
 * 
 * @author <a href="mailto:vonc@laposte.net">VonC</a>
 */
@Test
public class ClearCaseSupportStandAloneTest {

  private static org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(ClearCaseSupportStandAloneTest.class);
  private ClearCaseSupport ccs;

  @BeforeClass
  public void setup() {
    System.out.println("setup");
    URL url = getClass().getResource("log4j.xml");
    DOMConfigurator.configure(url);
    Logger.setFactory(new Log4jFactory());
    ccs = new ClearCaseSupport();
  }

  public void test() {
    System.out.println("test");
  }

  @AfterClass
  public void teardown() {
    System.out.println("teardown");
  }

}
