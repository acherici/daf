#!/usr/bin/env bash
kubectl delete configmap storage-manager-conf
kubectl create configmap storage-manager-conf --from-file=../playApi/conf/prodBase.conf