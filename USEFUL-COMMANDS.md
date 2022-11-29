restart the deployment, after you push a new image:

```shell
./restart-and-pull-new-image.sh
```

apply changes to the deployments:

```shell
kubectl apply -f deployment.yml
```

get logs:

```shell
# find out the NAME of the pod you want to listen to
kubectl get pods 

# replace the name of the pod
kubectl logs --follow test-kotlin-27821730-jj44d
```