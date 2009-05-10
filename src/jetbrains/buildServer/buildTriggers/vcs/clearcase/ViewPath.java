package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import jetbrains.buildServer.vcs.FileRule;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author maxim.manuylov
 */
public class ViewPath {
  private final String myCCViewPath;
  private final String myRelativePath;

  private String myIncludeRuleFrom;
  private String myWholePath;

  public ViewPath(@NotNull final String ccViewPath, @Nullable final String relativePath) throws VcsException {
    myCCViewPath = CCPathElement.normalizePath(ccViewPath);
    myRelativePath = removeFirstSeparatorIfNeeded(CCPathElement.normalizePath(relativePath == null ? "" : relativePath));
    myIncludeRuleFrom = null;
    updateWholePath();
  }

  @NotNull
  private String removeFirstSeparatorIfNeeded(@NotNull final String path) {
    return path.startsWith(File.separator) && path.length() > 1 ? path.substring(1) : path;
  }

  @NotNull
  public String getClearCaseViewPath() {
    return myCCViewPath;
  }

  @NotNull
  public String getRelativePathWithinTheView() {
    return myRelativePath;
  }

  @NotNull
  public String getWholePath() {
    return myWholePath;
  }

  @NotNull
  public File getClearCaseViewPathFile() {
    return new File(myCCViewPath);
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

    myWholePath = CCPathElement.normalizePath(sb.toString());
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
}
