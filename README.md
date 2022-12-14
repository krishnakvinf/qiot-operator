# qiot-operator

operator to install qiot components on Factory and Datacenter clusters.

## Testing using minikube

Install minikube, kubectl and start local cluster with at least 6GB RAM and 2 vCPU using the instructions from the [getting started page](https://minikube.sigs.k8s.io/docs/start/).

The minikube cluster, while starting, will automatically modify kubeconfig to point to the running cluster.

Install cert-manager, Strimzi operator and create a kafka instance using the commands below.

```
kubectl apply -f ./cert-manager.yaml
kubectl create ns kafka
kubectl apply -f ./strimzi_op.yaml -n kafka
kubectl apply -f ./kafka-persistent-single.yaml -n kafka
```

All the install files are available at the root folder in the repo.

The CRD's can be tested by installing using.

```
kubectl apply -f ./testdc.yaml
```

OR

```
kubectl apply -f ./testfactory.yaml
```

# Cleanup

To cleanup, progress in reverse, deleting the CRD instances first, followed by the kafka instance and the operators.