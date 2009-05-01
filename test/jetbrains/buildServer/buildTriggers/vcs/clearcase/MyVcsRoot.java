package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import com.intellij.util.containers.HashMap;
import jetbrains.buildServer.vcs.VcsRoot;

import java.util.Map;

/**
 * @author Gilles Philippart
 */
class MyVcsRoot implements VcsRoot {

    private String vcsName;
    private HashMap<String, String> properties = new HashMap<String, String>();
    private String name;
    private int id;
    private int rootVersion;

    public MyVcsRoot(String vcsName, String name, int id, int rootVersion) {
        this.vcsName = vcsName;
        this.name = name;
        this.id = id;
        this.rootVersion = rootVersion;
    }

    public String getVcsName() {
        return vcsName;
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public String getProperty(String key, String defaultIfNull) {
        String s = properties.get(key);
        return s != null ? s : defaultIfNull;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String convertToString() {
        return "VCS Root " + vcsName;
    }

    public String convertToPresentableString() {
        throw new UnsupportedOperationException();
    }

    public long getPropertiesHash() {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public long getRootVersion() {
        return rootVersion;
    }

    public VcsRoot createSecureCopy() {
        throw new UnsupportedOperationException();
    }

    public void addProperty(String key, String value) {
        properties.put(key, value);
    }
}
