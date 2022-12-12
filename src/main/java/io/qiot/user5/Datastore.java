package io.qiot.user5;

public class Datastore implements QiotResource {
 
    String imageName, version;

    public Datastore(String _imageName, String _version) {
        imageName = _imageName;
        version = _version;
    }

    public String getImageName() {
        return imageName;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getLogLevel() {
        return "INFO";
    }
}
