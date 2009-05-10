package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import jetbrains.buildServer.vcs.AbstractPatchBuilder;
import jetbrains.buildServer.log.Loggers;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gilles Philippart
 */
class MyAbstractPatchBuilder extends AbstractPatchBuilder {

  private int deleteFile, deleteDirectory, changesOrCreateTextFile,changeOrCreateBinaryFile,createDirectory ;

  private static final Logger LOG = Logger.getLogger(MyAbstractPatchBuilder.class);

  public void deleteFile(File file, boolean b) throws IOException {
    deleteFile++;
    Loggers.VCS.debug(String.format("ClearCaseSupportLiveTest.deleteFile , file=%s", file));
  }

  public void deleteDirectory(File file, boolean b) throws IOException {
    deleteDirectory++;
    Loggers.VCS.debug(String.format("ClearCaseSupportLiveTest.deleteDirectory, file=%s", file));
  }

  public void changeOrCreateTextFile(File file, String s, InputStream inputStream, long l, byte[] bytes)
      throws IOException {
    changesOrCreateTextFile++;
    Loggers.VCS.debug(String.format("changeOrCreateTextFile, file=%s, s=%s", file, s));
  }

  public void changeOrCreateBinaryFile(File file, String s, InputStream inputStream, long l) throws IOException {
    changeOrCreateBinaryFile++;
    Loggers.VCS.debug(String.format("changeOrCreateBinaryFile, file=%s, s=%s", file, s));
  }

  public void createDirectory(File file) throws IOException {
    createDirectory++;
    Loggers.VCS.debug(String.format("createDirectory, file=%s", file));
  }

  public void createBinaryFile(File file, String s, InputStream inputStream, long l) throws IOException {
    Loggers.VCS.debug(String.format("createBinaryFile, file=%s", file));
  }

  public void createTextFile(File file, String s, InputStream inputStream, long l, byte[] bytes) throws IOException {
    Loggers.VCS.debug(String.format("createTextFile, file=%s", file));
  }

  public void renameFile(File file, File file1, boolean b) throws IOException {
    Loggers.VCS.debug(String.format("renameFile, file=%s, file1=%s", file, file1));
  }

  public void renameDirectory(File file, File file1, boolean b) throws IOException {
    Loggers.VCS.debug(String.format("renameDirectory, file=%s, file1=%s", file, file1));
  }

  public void setLastModified(File currentRelativeFile, long time) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return String.format("MyAbstractPatchBuilder{deleteFile=%d, deleteDirectory=%d, changesOrCreateTextFile=%d, changeOrCreateBinaryFile=%d, createDirectory=%d'}'",
        deleteFile, deleteDirectory, changesOrCreateTextFile, changeOrCreateBinaryFile, createDirectory);
  }
}
