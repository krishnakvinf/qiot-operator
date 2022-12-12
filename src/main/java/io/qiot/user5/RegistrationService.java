package io.qiot.user5;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class RegistrationService implements QiotResource {

    @JsonPropertyDescription("The image name for Registration Service")
    public String image;

    @JsonPropertyDescription("The image tag for Registration Service")
    public String version;

    @JsonPropertyDescription("Log level for this service.")
    public String logLevel;

    public String getImageName() {
        return image;
    }

    public String getVersion() {
        return version;
    }

        
    public String getLogLevel() {
        if(logLevel.length() == 0) {
            return "INFO";
        }
        else {
            return logLevel;
        }
    }


}
