package io.qiot.user5;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class FactorySpec {

    // Add Spec information here
    @JsonPropertyDescription("Registry URL for all images. Note: include trailing / in the value.")
    public String registry = "";

    @JsonPropertyDescription("Relative sizing for the deployments from 1 to 5 (largest quotas)")
    public int sizing = 1;

    @JsonPropertyDescription("Endpoint for Kafka.")
    public String kafkaBootstrapURL = "";

    @JsonPropertyDescription("Specifications for Factory Facility Manager Service")
    public FactoryFacilityManagerService factoryFacilityManagerService = new FactoryFacilityManagerService();

    @JsonPropertyDescription("Specifications for Factory Product Line Service")
    public FactoryProductLineService factoryProductLineService = new FactoryProductLineService();

    @JsonPropertyDescription("Specifications for Factory Product Line Service")
    public FactoryProductionValidatorService factoryProductionValidatorService = new FactoryProductionValidatorService();

    public String getFactoryFacilityManagerImageRef() {
        if(factoryFacilityManagerService.version.length() == 0) {
            factoryFacilityManagerService.version = "latest";
        }
        return registry+factoryFacilityManagerService.image+":"+factoryFacilityManagerService.version;
    }

    public String getFactoryProductLineImageRef() {
        if(factoryProductLineService.version.length() == 0) {
            factoryProductLineService.version = "latest";
        }
        return registry+factoryProductLineService.image+":"+factoryProductLineService.version;
    }

    public String getFactoryProductionValidatorImageRef() {
        if(factoryProductionValidatorService.version.length() == 0) {
            factoryProductionValidatorService.version = "latest";
        }
        return registry+factoryProductionValidatorService.image+":"+factoryProductionValidatorService.version;
    }

}
