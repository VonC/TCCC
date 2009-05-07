package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import java.io.File;

/**
 * @author Gilles Philippart
 */
public abstract class AbstractDirectoryVisitor {

  /**
   * Process all files and directories under dir.
   *
   * @param dir a directory
   */
  public final void visitDirsAndFiles(File dir) {
    process(dir);
    if (dir.isDirectory()) {
      for (String child : dir.list()) {
        visitDirsAndFiles(new File(dir, child));
      }
    }
  }

  /**
   * Process only directories under dir.
   *
   * @param dir a directory
   */
  public final void visitDirsOnly(File dir) {
    if (dir.isDirectory()) {
      process(dir);
      for (String child : dir.list()) {
        visitDirsOnly(new File(dir, child));
      }
    }
  }

  /**
   * Process only files under dir.
   *
   * @param dir a directory
   */
  public final void visitFilesOnly(File dir) {
    if (dir.isDirectory()) {
      for (String child : dir.list()) {
        visitFilesOnly(new File(dir, child));
      }
    } else {
      process(dir);
    }
  }

  /**
   * Process the file or the directory.
   *
   * @param f a file or a directory depending on the API which was called .
   */
  protected abstract void process(File f);

}
