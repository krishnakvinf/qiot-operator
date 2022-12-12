package io.qiot.user5;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkus.logging.Log;

public class FactoryReconciler implements Reconciler<Factory> { 
  private final KubernetesClient client;

  public FactoryReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public UpdateControl<Factory> reconcile(Factory resource, Context context) {

    // Common Properties thread thorugh all the services so that details such as 
    // database URL can be shared with subsewquent services
    // Prefixes are: PG_ , RS_, PMS_, GPLS_, ECS_ 
    Map<String, String> commonProperties = new HashMap<String, String>();

    // TOTO configure applications to connect to datastores
    commonProperties = createBindingSecret(resource, commonProperties);
    commonProperties = reconcilePostgresDatastore(resource, commonProperties);
    commonProperties = reconcileMongoDatastore(resource, commonProperties);
    commonProperties = reconcileFactoryFacilityManagerService(resource, commonProperties);
    commonProperties = reconcileFactoryProductLineService(resource, commonProperties);
    commonProperties = reconcileFactoryProductionValidatorService(resource, commonProperties);

    return UpdateControl.noUpdate();
  }

  private Map<String, String> createLabels(Factory resource, QiotResource component) {
    var name = resource.getMetadata().getName()+"-"+component.getImageName();
    if(name.length() > 63) {
      name = name.substring(0,62);
    }
    
    return Map.of(
      "app.kubernetes.io/name", name,
      "app.kubernetes.io/version", component.getVersion(),
      "app.kubernetes.io/component", component.getImageName(),
      "app.kubernetes.io/part-of", resource.getCRDName(),
      "app.kubernetes.io/managed-by", resource.getKind()
    );
  }

  private ObjectMeta createMetadata(Factory resource, QiotResource component, Map<String, String> labels) {
    final var metadata = resource.getMetadata();
    // Example of a Kubernetes specific check
    var name = metadata.getName()+"-"+component.getImageName();
    Log.info("Name of the metadata: "+name);
    Log.info("Name of the component name: "+component.getImageName());
    if(name.length() > 63) {
      name = name.substring(0,62);
    }

    return new ObjectMetaBuilder()
      .withName(name)
      .withNamespace(metadata.getNamespace())
      .addNewOwnerReference()
        .withUid(metadata.getUid())
        .withApiVersion(resource.getApiVersion())
        .withName(metadata.getName())
        .withKind(resource.getKind())
      .endOwnerReference()
      .withLabels(labels)
    .build();
      
  }

  Random random = new SecureRandom();

  private String generatePassword(int length) {

    var allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    var allowedCharsLen = allowedChars.length();
    StringBuilder password = new StringBuilder();
    for(int i = 0; i < length; i++) {
      password.append(allowedChars.charAt(random.nextInt(0, allowedCharsLen)));
    }

    return password.toString();

  }

  private Map<String, String> createBindingSecret(Factory resource, Map<String, String> commonProperties) {

    final var pg_password = generatePassword(16);
    final var idb_password = generatePassword(16);
    final var mdb_password = generatePassword(16);
    final var ds = new Datastore("bindings", "v1");
    final var labels = createLabels(resource, ds);
    final var metadata = createMetadata(resource, ds, labels);

    var secretsInNs = client.secrets().inNamespace(metadata.getNamespace()).list().getItems();
    Log.info("Secrets list is: "+secretsInNs+" in namespace: "+metadata.getNamespace());
    
    for(var s : secretsInNs) {
      // If secret already exists, exit the function does not need to be recreated
      Log.info("The name of the current secret in the loop is: "+s.getMetadata().getName());
      Log.info("The name of the comparison secret in the loop is: "+metadata.getName());
      if(s.getMetadata().getName().equals(metadata.getName())) {
        Base64.Decoder decoder = Base64.getDecoder();
        Log.info("Now checking with secret: "+s.getMetadata().getName());
        commonProperties = s.getData();
        for(Map.Entry<String, String> e: commonProperties.entrySet()) {
          commonProperties.put(e.getKey(), new String(decoder.decode(e.getValue())));
        }
        commonProperties.put("BINDING_SECRET_NAME", metadata.getName());
        return commonProperties;
      }
    }

    commonProperties = new HashMap<>(Map.of(
      "PG_USER", "admin",
      "PG_PASSWORD", pg_password,
      "INFLUXDB_USERNAME", "root",
      "INFLUXDB_PASSWORD", idb_password,
      "MONGODB_ROOT_USER", "qiotroot",
      "MONGODB_ROOT_PASSWORD", mdb_password,
      "BINDING_SECRET_NAME", metadata.getName()
    ));

    Log.info("Name of the secret: "+metadata.getName());
    var sec = new SecretBuilder()
    .withMetadata(metadata)
    .addToStringData(commonProperties)
    .build();

    client.secrets().create(sec);

    return commonProperties;

  }

  private Map<String, String> reconcilePostgresDatastore(Factory resource, Map<String, String> commonProperties) {

    final var ds = new Datastore("postgres", "14");
    final var labels = createLabels(resource, ds);
    final var name = resource.getMetadata().getName();

    var serviceName = resource.getMetadata().getName()+"-"+ds.getImageName();
    if(serviceName.length() > 63) {
      serviceName = serviceName.substring(0,62);
    }

    var dataVolMount = new VolumeMount();
    dataVolMount.setMountPath("/var/lib/postgresql/data/pgdata");
    dataVolMount.setName("postgresdb");

    var volClaimTemplate = new PersistentVolumeClaim();
    var volMetadata = createMetadata(resource, ds, labels);
    volMetadata.setName("postgresdb");
    volClaimTemplate.setMetadata(volMetadata);
    var volClaimSpec = new PersistentVolumeClaimSpec();
    volClaimSpec.setAccessModes(List.of("ReadWriteOnce"));
    volClaimSpec.setResources(new ResourceRequirements(Map.of("storage", new Quantity("1Gi")), 
      Map.of("storage", new Quantity("1Gi"))));
    volClaimTemplate.setSpec(volClaimSpec);

    final var metadata = createMetadata(resource, ds, labels);

    var ss = new StatefulSetBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .withNewSelector().withMatchLabels(labels).endSelector()
        .withReplicas(1).withServiceName(serviceName)
        .withNewTemplate()
          .withNewMetadata().withLabels(labels).endMetadata()
          .withNewSpec()
            .addNewContainer()
              .withName(name).withImage(ds.getImageName()+":"+ds.getVersion())
              // .withNewSecurityContext().withReadOnlyRootFilesystem(true).endSecurityContext()
              .addNewPort()
                .withName("tcp").withProtocol("TCP").withContainerPort(5432)
              .endPort()
              .withEnv(KubernetesResourceUtil.convertMapToEnvVarList(Map.of(
                "PGDATA", "/var/lib/postgresql/data/pgdata",
                "POSTGRES_DB", "qiot_manufacturing"
              )))
              .addNewEnv()
                .withName("POSTGRES_USER")
                .withNewValueFrom()
                  .withNewSecretKeyRef("PG_USER", commonProperties.get("BINDING_SECRET_NAME"), false)
                .endValueFrom()
              .endEnv()
              .addNewEnv()
                .withName("POSTGRES_PASSWORD")
                .withNewValueFrom()
                  .withNewSecretKeyRef("PG_PASSWORD", commonProperties.get("BINDING_SECRET_NAME"), false)
                .endValueFrom()
              .endEnv()
              .withVolumeMounts(List.of(dataVolMount))
            .endContainer()
          .endSpec()
        .endTemplate()
        .withVolumeClaimTemplates(List.of(volClaimTemplate))
      .endSpec()
    .build();

    var ss_service = new ServiceBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .addNewPort()
          .withName("tcp")
          .withPort(5432)
          .withNewTargetPort().withIntVal(5432).endTargetPort()
        .endPort()
        .withSelector(labels)
        .withType("ClusterIP")
      .endSpec()
    .build();

    client.apps().statefulSets().createOrReplace(ss);
    client.services().createOrReplace(ss_service);

    commonProperties.put("PG_SERVICE_NAME", metadata.getName());
    commonProperties.put("PG_URL", metadata.getName()+":5432");
    commonProperties.put("PG_DATABASE", "qiot_manufacturing");
    return commonProperties;

  }

  private Map<String, String> reconcileMongoDatastore(Factory resource, Map<String, String> commonProperties) {

    final var ds = new Datastore("mongo", "4.4.3");
    final var labels = createLabels(resource, ds);
    final var name = resource.getMetadata().getName();

    var serviceName = resource.getMetadata().getName()+"-"+ds.getImageName();
    if(serviceName.length() > 63) {
      serviceName = serviceName.substring(0,62);
    }

    var dataVolMount = new VolumeMount();
    dataVolMount.setMountPath("/data/db");
    dataVolMount.setName("mongodb");

    var volClaimTemplate = new PersistentVolumeClaim();
    var volMetadata = createMetadata(resource, ds, labels);
    volMetadata.setName("mongodb");
    volClaimTemplate.setMetadata(volMetadata);
    var volClaimSpec = new PersistentVolumeClaimSpec();
    volClaimSpec.setAccessModes(List.of("ReadWriteOnce"));
    volClaimSpec.setResources(new ResourceRequirements(Map.of("storage", new Quantity("1Gi")), 
      Map.of("storage", new Quantity("1Gi"))));
    volClaimTemplate.setSpec(volClaimSpec);

    final var metadata = createMetadata(resource, ds, labels);

    var ss = new StatefulSetBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .withNewSelector().withMatchLabels(labels).endSelector()
        .withReplicas(1).withServiceName(serviceName)
        .withNewTemplate()
          .withNewMetadata().withLabels(labels).endMetadata()
          .withNewSpec()
            .addNewContainer()
              .withName(name).withImage(ds.getImageName()+":"+ds.getVersion())
              .addNewPort()
                .withName("tcp").withProtocol("TCP").withContainerPort(27017)
              .endPort()
              .withEnv(KubernetesResourceUtil.convertMapToEnvVarList(Map.of(
                "MONGODB_PORT_NUMBER", "27017",
                "MONGO_INITDB_DATABASE", "qiot_manufacturing"
              )))
              .addNewEnv()
                .withName("MONGO_INITDB_ROOT_USERNAME")
                .withNewValueFrom()
                  .withNewSecretKeyRef("MONGODB_ROOT_USER", commonProperties.get("BINDING_SECRET_NAME"), false)
                .endValueFrom()
              .endEnv()
              .addNewEnv()
                .withName("MONGO_INITDB_ROOT_PASSWORD")
                .withNewValueFrom()
                  .withNewSecretKeyRef("MONGODB_ROOT_PASSWORD", commonProperties.get("BINDING_SECRET_NAME"), false)
                .endValueFrom()
              .endEnv()
              .withVolumeMounts(List.of(dataVolMount))
            .endContainer()
          .endSpec()
        .endTemplate()
        .withVolumeClaimTemplates(List.of(volClaimTemplate))
      .endSpec()
    .build();

    var ss_service = new ServiceBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .addNewPort()
          .withName("tcp")
          .withPort(27017)
          .withNewTargetPort().withIntVal(27017).endTargetPort()
        .endPort()
        .withSelector(labels)
        .withType("ClusterIP")
      .endSpec()
    .build();

    client.apps().statefulSets().createOrReplace(ss);
    client.services().createOrReplace(ss_service);

    commonProperties.put("MONGODB_SERVICE_NAME", metadata.getName());
    commonProperties.put("MONGODB_URL", 
      "mongodb://"+commonProperties.get("MONGODB_ROOT_USER")+":"+commonProperties.get("MONGODB_ROOT_PASSWORD")+"@"+metadata.getName()+":27017");
    Log.info("Mongo connectivity URL: "+commonProperties.get("MONGODB_URL"));
    commonProperties.put("MONGODB_DATABASE", "admin");
    
    return commonProperties;

  }

  private Map<String, String> reconcileFactoryFacilityManagerService(Factory resource, Map<String, String> commonProperties) {

    final var labels = createLabels(resource, resource.getSpec().factoryFacilityManagerService);
    final var name = resource.getMetadata().getName();
    final var metadata = createMetadata(resource, resource.getSpec().factoryFacilityManagerService, labels);
    var rs_deployment = new DeploymentBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .withNewSelector().withMatchLabels(labels).endSelector()
        .withNewTemplate()
          .withNewMetadata().withLabels(labels).endMetadata()
          .withNewSpec()
            .addNewContainer()
              .withName(name).withImage(resource.getSpec().getFactoryFacilityManagerImageRef())
              .addNewPort()
                .withName("http").withProtocol("TCP").withContainerPort(8080)
              .endPort()
              .withEnv(KubernetesResourceUtil.convertMapToEnvVarList(Map.of(
                "LOG_LEVEL", resource.getSpec().factoryFacilityManagerService.getLogLevel(),
                "QIOT_LOG_LEVEL", resource.getSpec().factoryFacilityManagerService.getLogLevel()
              )))
            .endContainer()
          .endSpec()
        .endTemplate()
      .endSpec()
    .build();

    var rs_service = new ServiceBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .addNewPort()
          .withName("http")
          .withPort(5100)
          .withNewTargetPort().withIntVal(5100).endTargetPort()
        .endPort()
        .withSelector(labels)
        .withType("ClusterIP")
      .endSpec()
    .build();

    client.apps().deployments().createOrReplace(rs_deployment);
    client.services().createOrReplace(rs_service);

    commonProperties.put("FACTORY_FACILITY_MANAGER_SERVICE_URL", metadata.getName()+":5100");

    return commonProperties;

  }

  private Map<String, String> reconcileFactoryProductLineService(Factory resource, Map<String, String> commonProperties) {

    final var labels = createLabels(resource, resource.getSpec().factoryProductLineService);
    final var name = resource.getMetadata().getName();
    final var metadata = createMetadata(resource, resource.getSpec().factoryProductLineService, labels);
    var rs_deployment = new DeploymentBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .withNewSelector().withMatchLabels(labels).endSelector()
        .withNewTemplate()
          .withNewMetadata().withLabels(labels).endMetadata()
          .withNewSpec()
            .addNewContainer()
              .withName(name).withImage(resource.getSpec().getFactoryProductLineImageRef())
              .addNewPort()
                .withName("http").withProtocol("TCP").withContainerPort(8080)
              .endPort()
              .withEnv(KubernetesResourceUtil.convertMapToEnvVarList(Map.of(
                "LOG_LEVEL", resource.getSpec().factoryProductLineService.getLogLevel(),
                "QIOT_LOG_LEVEL", resource.getSpec().factoryProductLineService.getLogLevel()
              )))
            .endContainer()
          .endSpec()
        .endTemplate()
      .endSpec()
    .build();

    var rs_service = new ServiceBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .addNewPort()
          .withName("http")
          .withPort(5101)
          .withNewTargetPort().withIntVal(5101).endTargetPort()
        .endPort()
        .withSelector(labels)
        .withType("ClusterIP")
      .endSpec()
    .build();

    client.apps().deployments().createOrReplace(rs_deployment);
    client.services().createOrReplace(rs_service);

    commonProperties.put("FACTORY_PRODUCT_LINE_SERVICE_URL", metadata.getName()+":5101");

    return commonProperties;

  }

  private Map<String, String> reconcileFactoryProductionValidatorService(Factory resource, Map<String, String> commonProperties) {

    final var labels = createLabels(resource, resource.getSpec().factoryProductionValidatorService);
    final var name = resource.getMetadata().getName();
    final var metadata = createMetadata(resource, resource.getSpec().factoryProductionValidatorService, labels);
    var rs_deployment = new DeploymentBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .withNewSelector().withMatchLabels(labels).endSelector()
        .withNewTemplate()
          .withNewMetadata().withLabels(labels).endMetadata()
          .withNewSpec()
            .addNewContainer()
              .withName(name).withImage(resource.getSpec().getFactoryProductionValidatorImageRef())
              .addNewPort()
                .withName("http").withProtocol("TCP").withContainerPort(8080)
              .endPort()
              .withEnv(KubernetesResourceUtil.convertMapToEnvVarList(Map.of(
                "LOG_LEVEL", resource.getSpec().factoryProductionValidatorService.getLogLevel(),
                "QIOT_LOG_LEVEL", resource.getSpec().factoryProductionValidatorService.getLogLevel()
              )))
            .endContainer()
          .endSpec()
        .endTemplate()
      .endSpec()
    .build();

    var rs_service = new ServiceBuilder()
      .withMetadata(metadata)
      .withNewSpec()
        .addNewPort()
          .withName("http")
          .withPort(8080)
          .withNewTargetPort().withIntVal(8080).endTargetPort()
        .endPort()
        .withSelector(labels)
        .withType("ClusterIP")
      .endSpec()
    .build();

    client.apps().deployments().createOrReplace(rs_deployment);
    client.services().createOrReplace(rs_service);

    commonProperties.put("FACTORY_PRODUCTION_VALIDATOR_SERVICE_URL", metadata.getName()+":8080");

    return commonProperties;

  }

}

