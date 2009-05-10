package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;

/**
 * @author Gilles Philippart
 */
class MyVcsRoot extends VcsRootImpl {

    private String name;
    private int rootVersion;

    public MyVcsRoot(String vcsName, String name, int id, int rootVersion) {
      super(id, vcsName);
      this.name = name;
      this.rootVersion = rootVersion;
    }


    public String getName() {
        return name;
    }

    public long getRootVersion() {
        return rootVersion;
    }

    public VcsRoot createSecureCopy() {
        throw new UnsupportedOperationException();
    }

}
