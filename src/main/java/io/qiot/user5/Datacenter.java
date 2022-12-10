package io.qiot.user5;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("user5.qiot.io")
public class Datacenter extends CustomResource<DatacenterSpec, DatacenterStatus> implements Namespaced {}

