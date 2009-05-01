package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.AbstractPatchBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gilles Philippart
 */
class MyAbstractPatchBuilder extends AbstractPatchBuilder {

    public void deleteFile(File file, boolean b) throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.deleteFile " + ", file=" + file);
    }

    public void deleteDirectory(File file, boolean b) throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.deleteDirectory" + ", file=" + file);
    }

    public void changeOrCreateTextFile(File file, String s, InputStream inputStream, long l, byte[] bytes)
            throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.changeOrCreateTextFile" + ", file=" + file + ", s=" + s);
    }

    public void changeOrCreateBinaryFile(File file, String s, InputStream inputStream, long l) throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.changeOrCreateBinaryFile" + ", file=" + file + ", s=" + s);
    }

    public void createDirectory(File file) throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.createDirectory" + ", file=" + file);
    }

    public void createBinaryFile(File file, String s, InputStream inputStream, long l) throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.createBinaryFile" + ", file=" + file);
    }

    public void createTextFile(File file, String s, InputStream inputStream, long l, byte[] bytes) throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.createTextFile" + ", file=" + file);
    }

    public void renameFile(File file, File file1, boolean b) throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.renameFile" + ", file=" + file + ", file1=" + file1);
    }

    public void renameDirectory(File file, File file1, boolean b) throws IOException {
        Loggers.VCS.info("ClearCaseSupportLiveTest.renameDirectory" + ", file=" + file + ", file1=" + file1);
    }

    public void setLastModified(File currentRelativeFile, long time) throws IOException {
        throw new UnsupportedOperationException();
    }
}
