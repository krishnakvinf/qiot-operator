package io.qiot.user5;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class DatacenterSpec {

    // Add Spec information here
    // @JsonProperty("registrationService.version")
    @JsonPropertyDescription("Registry URL for all images. Note: include trailing / in the value.")
    public String registry = "";

    @JsonPropertyDescription("Relative sizing for the deployments from 1 to 5 (largest quotas)")
    public int sizing = 1;

    @JsonPropertyDescription("Endpoint for Kafka.")
    public String kafkaBootstrapURL = "";

    @JsonPropertyDescription("Specifications for Registration Service")
    public RegistrationService registrationService = new RegistrationService();

    @JsonPropertyDescription("Specifications for Plant Manager Service")
    public PlantManagerService plantManagerService = new PlantManagerService();

    @JsonPropertyDescription("Specifications for Global Produc tLine Service")
    public GlobalProductLineService globalProductLineService = new GlobalProductLineService();

    @JsonPropertyDescription("Specifications for Event Collector Service")
    public EventCollectorService eventCollectorService = new EventCollectorService();

    public String getRegistry() {
        return registry;
    }

    public int getSizing() {
        return sizing;
    }

    public String getRegistrationServiceImageRef() {
        if(registrationService.version.length() == 0) {
            registrationService.version = "latest";
        }
        return registry+registrationService.image+":"+registrationService.version;
    }

    public String getPlantManagerImageRef() {
        if(plantManagerService.version.length() == 0) {
            plantManagerService.version = "latest";
        }
        return registry+plantManagerService.image+":"+plantManagerService.version;
    }

    public String getGlobalProductLineServiceImageRef() {
        if(globalProductLineService.version.length() == 0) {
            globalProductLineService.version = "latest";
        }
        return registry+globalProductLineService.image+":"+globalProductLineService.version;
    }

    public String getEventCollectorServiceImageRef() {
        if(eventCollectorService.version.length() == 0) {
            eventCollectorService.version = "latest";
        }
        return registry+eventCollectorService.image+":"+eventCollectorService.version;
    }

    public String getRegistrationServiceImage() {
        return registrationService.image;
    }

    public String getPlantManagerImage() {
        return plantManagerService.image;
    }

    public String getGlobalProductLineServiceImage() {
        return globalProductLineService.image;
    }

    public String getEventCollectorServiceImage() {
        return eventCollectorService.image;
    }

}
