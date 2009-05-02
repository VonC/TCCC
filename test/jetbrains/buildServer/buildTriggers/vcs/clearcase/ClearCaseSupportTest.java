package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsModification;
import jetbrains.buildServer.vcs.VcsFileModification;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.List;
import java.util.Date;

import org.apache.log4j.xml.DOMConfigurator;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gilles Philippart
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
/*
    public void testConnectionIssue() throws VcsException, IOException {
        // configure the VCS root
        String ruleTo = "isl_prd_2r2_lt1_dev/product";
        String viewPath = "C:/eprom/views/dev/isl_prd_2r2_lt1_dev/product";
        String streamName = "ISL_PRD_2R2_LT1_Dev";
        String from = "15-Apr-2009.09:00:00";
        String to = "29-Apr-2009.21:00:00";
        collectAndBuild(ccs, ruleTo, viewPath, streamName, from, to);
    }
*/

    private void collectAndBuild(ClearCaseSupport ccs, String ruleTo, String viewPath, String streamName, String from,
                                 String to) throws VcsException, IOException {
        MyVcsRoot myVcsRoot = new MyVcsRoot("Clearcase", streamName, 1, 2);
        myVcsRoot.addProperty(ClearCaseSupport.VIEW_PATH, viewPath);
        IncludeRule includeRule = new IncludeRule(".", ruleTo, null);
        //cleartool lshistory -r -nco -branch ISL_PRD_MDL_Dev -since 15-Apr-2009.09:00:00 -fmt %u#--#%Nd#--#%En#--#%m#--#%Vn#--#%o#--#%e#--#%Nc#--#%[activity]p###----###\n C:\eprom\views\dev\isl_prd_mdl_dev\isl\product_model
        ccs.collectChanges(myVcsRoot, from, to, includeRule);
        ccs.buildPatch(myVcsRoot, from, to, new MyAbstractPatchBuilder(), includeRule);
    }


}
