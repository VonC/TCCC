package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import jetbrains.buildServer.vcs.FileRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.log.Loggers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.commons.lang.StringUtils;

import java.io.File;

/**
 * Example of view path :
 *
 * myCCViewPath='', myRelativePath='eprom\tools', myIncludeRuleFrom='null', myWholePath='C:\eprom\views\dev\epr_tls_dev\eprom\tools'
 *
 *
 * @author maxim.manuylov
 */
public class ViewPath {

  /**
   *  the CC view path ex : C:\eprom\views\dev\isl_prd_mdl_dev.
   */
  private final String myCCViewPath;

  /**
   *  The relative path ex : isl\product_model.
   */
  private final String myRelativePath;

  /**
   *  For now, we set this to null.
   */
  private String myIncludeRuleFrom;
  private String myWholePath;
  private String myVob;

  public ViewPath(@NotNull final String ccViewPath, @Nullable final String relativePath) throws VcsException {
    myCCViewPath = CCPathElement.normalizePath(ccViewPath);
    myRelativePath = removeFirstSeparatorIfNeeded(CCPathElement.normalizePath(relativePath == null ? "" : relativePath));
    myVob = StringUtils.split(myRelativePath, "\\")[0];
    myIncludeRuleFrom = null;
    updateWholePath();
  }

  @NotNull
  private String removeFirstSeparatorIfNeeded(@NotNull final String path) {
    return path.startsWith(File.separator) && path.length() > 1 ? path.substring(1) : path;
  }


  /**
   * Returns the CC view root ex : C:\eprom\views\dev\isl_prd_mdl_dev.
   *
   * @return the CC view root
   */
  @NotNull
  public String getClearCaseViewRoot() {
    return myCCViewPath;
  }

  @NotNull
  public File getClearCaseViewRootAsFile() {
    return new File(myCCViewPath);
  }

  @NotNull
  public String getRelativePathWithinTheView() {
    return myRelativePath;
  }

  @NotNull
  public String getWholePath() {
    return myWholePath;
  }

  public void setIncludeRuleFrom(@Nullable final FileRule includeRule) throws VcsException {
    if (includeRule == null) {
      myIncludeRuleFrom = null;
    }
    else {
      myIncludeRuleFrom = removeFirstSeparatorIfNeeded(CCPathElement.normalizeSeparators(includeRule.getFrom().trim()));
    }

    updateWholePath();
  }

  private void updateWholePath() throws VcsException {
    final StringBuilder sb = new StringBuilder(myCCViewPath);

    appendPath(sb, myRelativePath);
    appendPath(sb, myIncludeRuleFrom);

    String path = sb.toString();
    myWholePath = CCPathElement.normalizePath(path);
  }

  private void appendPath(@NotNull final StringBuilder sb, @Nullable final String additionalPath) {
    if (additionalPath != null && !"".equals(additionalPath)) {
      sb.append(File.separatorChar);
      sb.append(additionalPath);
    }
  }

  @Override
  public String toString() {
    return "ViewPath{" +
        "myCCViewPath='" + myCCViewPath + '\'' +
        ", myRelativePath='" + myRelativePath + '\'' +
        ", myIncludeRuleFrom='" + myIncludeRuleFrom + '\'' +
        ", myWholePath='" + myWholePath + '\'' +
        '}';
  }

  public String getVob() {
    return myVob;
  }
}
