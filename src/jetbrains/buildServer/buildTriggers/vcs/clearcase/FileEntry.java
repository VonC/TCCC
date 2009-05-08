package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import java.io.File;

/**
 * @author Gilles Philippart
 */
public class FileEntry {
  private File file;
  private String relativePath;
  private long modificationDate;

  public FileEntry(File file, long modificationDate, String relativePath) {
    this.file = file;
    this.relativePath = relativePath;
    this.modificationDate = modificationDate;
  }

  public File getFile() {
    return file;
  }

  public String getRelativePath() {
    return relativePath;
  }

  public long getModificationDate() {
    return modificationDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileEntry that = (FileEntry) o;

    if (!relativePath.equals(that.relativePath)) return false;
    if (modificationDate != that.modificationDate) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = relativePath.hashCode();
    result = 31 * result + (int) (modificationDate ^ (modificationDate >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return String.format("file=%s, modificationDate=%d", file, modificationDate);
  }
}
