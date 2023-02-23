#!/usr/bin/env bash

concordDevMode=${CONCORD_DEV_MODE:-"false"}

domain="local.localhost"
concordUrl="https://concord.${domain}"

certsPath="${HOME}/.ck8s/"
caKey="selfsigned-ca.key"
caCertificate="selfsigned-ca.crt"
selfsignedCA="selfsigned-ca"

kindImageVersion="1.23.10"
kindImage="kindest/node:v${kindImageVersion}"
kindName="ck8s-local"
kindKubeconfig="${HOME}/.kube/ck8s-config-local"

registryName="kind-registry"
registryPort="5000"
registryImage="registry:2.7"

awsHostPath="${HOME}/.aws"
awsContainerPath="/tmp/aws"
kubeHostPath="${HOME}/.kube"
kubeContainerPath="/tmp/kube"
m2HostPath="${HOME}/.m2/repository"
m2ContainerPath="/tmp/m2"
concordRunnerHostPath="/tmp/runner"
concordRunnerContainerPath="/tmp/runner"
concordChartVersion="1.0.10"
concordChartRepository="oci://ghcr.io/concord-workflow/concord-charts/concord"

concordAdminApiToken="auBy4eDWrKWsyhiDp3AQiw"

# This needs to be exported to make it available to scripts sourcing this file
export KUBECONFIG="${kindKubeconfig}"

function installCertToTrustStore() {
  if [ "$(uname)" == "Darwin" ]; then
    echo ">>> Installing cert to trust store"
    sudo security add-trusted-cert -d -r trustRoot -k "/Library/Keychains/System.keychain" ${1}
  elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
    sudo trust anchor --store "${1}"
  else
   echo "Error, I don't know how to install certificates on $(uname)"
   exit 1
  fi
}


function localCertificateAuthority() {
  echo ">>> Installing local cert authority"

  if [ -f "${certsPath}"/${caKey} ]; then
    return
  fi

cat > ${certsPath}/${selfsignedCA}.cnf <<-EOF
[ req ]
distinguished_name = req_distinguished_name
attributes = req_attributes

[ req_distinguished_name ]
countryName = Country Name (2 letter code)
countryName_min = 2
countryName_max = 2
stateOrProvinceName = State or Province Name (full name)
localityName = Locality Name (eg, city)
0.organizationName = Organization Name (eg, company)
organizationalUnitName = Organizational Unit Name (eg, section)
commonName = Common Name (eg, fully qualified host name)
commonName_max = 64
emailAddress = Email Address
emailAddress_max = 64

[ req_attributes ]
challengePassword = A challenge password
challengePassword_min = 4
challengePassword_max = 20

[v3_ca]
basicConstraints = critical,CA:TRUE
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always, issuer:always
EOF

  openssl genrsa -out ${certsPath}/${selfsignedCA}.key 2048

  openssl req \
    -new \
    -x509 \
    -nodes \
    -sha256 \
    -days 365 \
    -extensions v3_ca \
    -key ${certsPath}/${selfsignedCA}.key \
    -subj "/CN=The Ministry of Joyful Local Development (MJLD)" \
    -out ${certsPath}/${selfsignedCA}.crt \
    -config ${certsPath}/${selfsignedCA}.cnf

  kubectl create secret tls selfsigned-ca \
    --namespace cert-manager \
    --key=${certsPath}/${selfsignedCA}.key \
    --cert=${certsPath}/${selfsignedCA}.crt \
    --dry-run=client -o yaml > ${certsPath}/${selfsignedCA}.yml

  installCertToTrustStore ${certsPath}/${selfsignedCA}.crt
}

# Normally inside the cluster we'd let cert-manager generate the application's TLS certificate but
# for bootstrapping Concord and making it available at https://concord.local.dev with a valid TLS
# certificate we need to create it outside the cluster and pass it in.
function localServerCertificate() {
  cd ${certsPath}
  app="${1:-dummy}"

  if [ ! -f ${caCertificate} ]; then
    # Local CA needs to be created
    localCertificateAuthority
  fi

  serverDomain="${app}.${domain}"
  serverResource="${serverDomain}"
  namespace="${app}"
  secret="${app}-tls"
  secretYaml="${app}-tls.yaml"

  if [ ! -f ${serverResource}.key ]; then
    echo ">>> Generating the server private key ..."
    openssl genrsa -out ${serverResource}.key 2048
  fi

  if [ ! -f ${serverResource}.csr ]; then
    echo ">>> Generating the certificate CSR ..."
    openssl req -new \
      -key ${serverResource}.key \
      -out ${serverResource}.csr \
      -subj "/C=CA/ST=Joytario/L=Your Place!/O=No Meetings!/OU=JFDI/CN=${serverDomain}"
  fi

if [ ! -f ${serverResource}.ext ]; then
echo ">>> Generating the configuration for the certificate ..."
cat > ${serverResource}.ext << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names
[alt_names]
DNS.1 = ${serverDomain}
EOF
fi

  if [ ! -f ${serverResource}.crt ]; then
    echo ">>> Generating the server certificate ..."
    openssl x509 -req -in ${serverResource}.csr -CA ${caCertificate} -CAkey ${caKey} -CAcreateserial \
      -out ${serverResource}.crt -days 825 -sha256 -extfile ${serverResource}.ext
  fi

  echo ">>> Creating namespace ${app} for application ${app} ..."
  kubectl create namespace ${app} --dry-run=client -o yaml | kubectl apply -f - > /dev/null 2>&1

  echo ">>> Generating the TLS secret ${app} in namespace ${app} ..."
  kubectl create secret tls ${secret} -n ${namespace} \
    --key=${serverResource}.key \
    --cert=${serverResource}.crt \
    --dry-run=client -o yaml > ${secretYaml}

  kubectl apply -f ${secretYaml} --dry-run=client -o yaml | kubectl apply -f - > /dev/null 2>&1

  cd ..
}

function dockerRegistry() {
  echo ">>> Installing docker registry"

  # Start a local Docker registry (unless it already exists)
  running="$(docker inspect -f '{{.State.Running}}' "${registryName}" 2>/dev/null || true)"
  if [ "${running}" != 'true' ]; then
    echo ">>> Starting local Docker registry ..."
    docker run -d --restart=always -p "127.0.0.1:${registryPort}:5000" --name "${registryName}" ${registryImage}
  fi
}

function kindRegistry() {
  echo "installing kind registry"

# https://github.com/kubernetes/enhancements/tree/master/keps/sig-cluster-lifecycle/generic/1755-communicating-a-local-registry
cat <<EOF | kubectl apply -f - > /dev/null 2>&1
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${registryPort}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF
}

function connectRegistryToKindNetwork() {
  # connect the registry to the cluster network if not already connected
  if [ "$(docker inspect -f='{{json .NetworkSettings.Networks.kind}}' "${registryName}")" = 'null' ]; then
    echo ">>> Connecting Docker registry to ${kindName} cluster network ..."
    docker network connect "kind" "${registryName}"
  fi
}

function kindCluster() {
  echo ">>> Installing kind cluster"

# Create a kind cluster
# - Configures containerd to use the local Docker registry
# - Enables Ingress on ports 80 and 443
cat <<EOF | kind create cluster --kubeconfig ${kindKubeconfig} --name ${kindName} --image ${kindImage} --wait 5m --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${registryPort}"]
    endpoint = ["http://${registryName}:${registryPort}"]
nodes:
- role: control-plane
  extraMounts:
    - hostPath: "${awsHostPath}"
      containerPath: "${awsContainerPath}"
    # - hostPath: "${kubeHostPath}"
    #   containerPath: "${kubeContainerPath}"
    # - hostPath: "${m2HostPath}"
    #   containerPath: "${m2ContainerPath}"
    # - hostPath: "${concordRunnerHostPath}"
    #   containerPath: "${concordRunnerContainerPath}"
  kubeadmConfigPatches:
  - |
    kind: InitConfiguration
    nodeRegistration:
      kubeletExtraArgs:
        node-labels: "ingress-ready=true"
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
    protocol: TCP
  - containerPort: 443
    hostPort: 443
    protocol: TCP
EOF

  kindRegistry
  connectRegistryToKindNetwork
}

function certManager() {
  echo ">>> Installing cert manager"

  # Install the selfsigned CA we created into the cert-manager namespace so that
  # it can be used by the cert-manager CA cluster issuer
  kubectl create namespace cert-manager -o yaml --dry-run=client | kubectl apply -f -
  kubectl apply -f ${certsPath}/${selfsignedCA}.yml
}

function ingressNginx() {
  if [ -z "${CK8S_COMPONENTS}" ]; then
      >&2 echo "No CK8S_COMPONENTS provided"
      exit 1
  fi

  echo ">>> Installing NGinx Ingress controller ..."
  chartValues="${CK8S_COMPONENTS}/ingress-nginx/values-local.yaml"
  if [ ! -f ${chartValues} ]; then
    >&2 echo "Chart values ${chartValues} is not accessible"
    exit 1
  fi;

  helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
  helm repo update ingress-nginx
  helm upgrade ingress-nginx \
    --install \
    --values ${chartValues} \
    --namespace ingress-nginx \
    --create-namespace \
    --version 4.2.3 \
    ingress-nginx/ingress-nginx \
    --wait

  # https://stackoverflow.com/questions/61616203/nginx-ingress-controller-failed-calling-webhook
  #
  # Maybe customizing the values and disabling admission webhooks is better:
  # admissionWebhooks:
  #   enabled: false
  #
  nginxDeleteWebHook="true"
  if [ "${nginxDeleteWebHook}" = "true" ]; then
    kubectl delete -A ValidatingWebhookConfiguration ingress-nginx-admission > /dev/null
  fi
}

function installComponents() {
  echo ">>> Installing components"
  certManager
  ingressNginx
}

function installConcordAgentPool() {
  echo ">>> Installing concord agent pool"

  kubectl apply -f ${CK8S_COMPONENTS}/concord/agentpool.yaml -n concord > /dev/null 2>&1

  # The metrics endpoint has a metric for the workers available to do work.
  #
  # Calling https://concord.local.dev/metrics yields a page of information we
  # can sift out the 'agent_workers_available' metric and only continue when
  # there are more than 0.0 agents available.

  function agentsAvailable() {
    curl -s ${concordUrl}/metrics | grep ^agent_workers_available | sed 's/^agent_workers_available //' | tr -d '\n' | tr -d '\r'
  }

  echo -n ">>> Waiting for Concord agents to be available "
  while [ "$(agentsAvailable)" = "0.0" ]; do
    stdbuf -o0 printf '.'
    sleep 5
  done
  echo " READY!"
}

function uninstallConcordAgentPool() {
  kubectl delete -f ${CK8S_COMPONENTS}/concord/agentpool.yaml -n concord
}

function reinstallConcordAgentPool {
  uninstallConcordAgentPool
  sleep 10
  installConcordAgentPool
}

function installConcord() {
  echo ">>> Installing Concord"

  localServerCertificate concord

  chartValues="${CK8S_COMPONENTS}/concord/values-local.yaml"
  if [ "${concordDevMode}" = "true" ]; then
    echo ">>> Using local Concord Helm chart!"
    chart="../concord-charts/concord"
    chartVersion=""
  else
    echo ">>> Using published Concord Helm chart!"
    chart="${concordChartRepository}"
    chartVersion="--version ${concordChartVersion}"
  fi

  helm delete concord --namespace concord > /dev/null 2>&1

  helm upgrade concord \
    --install \
    --values ${chartValues} \
    --namespace concord \
    --create-namespace \
    ${chartVersion} \
    ${chart} > /dev/null 2>&1

  # This will provide the concord-server pod id if we need to kill it
  #kubectl get pods -n concord --template '{{range .items}}{{.metadata.name}}{{end}}' --selector=app=concord-server

  echo -n ">>> Waiting for Concord to start"

  until $(curl --output /dev/null --silent --head --fail "${concordUrl}/api/v1/server/ping"); do
    stdbuf -o0 printf '.'
    sleep 5
  done
  echo " READY!"

  installConcordAgentPool
}

function concordProcess() {
  processId="${1}"
  flowName="${2}"
  if [ -z "${flowName}" ]; then
    flowName="Concord"
  fi

  echo -n ">>> Waiting for the ${flowName} flow ${processId} to finish ..."
  while [ "$(curl --silent -H "Authorization: ${concordAdminApiToken}" ${concordUrl}/api/v1/process/${processId} | jq -r .status)" != "FINISHED" ]; do
    printf '.'
    sleep 5
  done
  echo " FINISHED!"
}

function testConcord() {

  echo -n ">>> Submitting test process ... "

  RESULT=`curl --silent -H "Authorization: ${concordAdminApiToken}" -F concord.yml=@${CK8S_COMPONENTS}/concord/test.yaml ${concordUrl}/api/v1/process`
  ID=`echo ${RESULT} | jq -r .instanceId`
  echo ${ID}

  concordProcess ${ID}

  echo -n ">>> Looking for completion message in process log ... "
  # Inspect logs from executed process
  RESULT=`curl --silent -H "Authorization: ${concordAdminApiToken}" ${concordUrl}/api/v1/process/${ID}/log`
  if echo ${RESULT} | grep -q 'COMPLETED'; then
      echo "SUCCESS!"
  else
      echo "FAILED :("
  fi
}

function ck8sUp() {
  echo "using target: ${certsPath}"
  mkdir -p ${certsPath} > /dev/null 2>&1
  localCertificateAuthority
  dockerRegistry
  kindCluster
  installComponents
  installConcord
  testConcord
}

function ck8sDown() {
  kind delete cluster --name ${kindName}
  rm -f ${HOME}/.kube/${kindKubeconfig}
}

function ck8sDockerRegistry() {
  dockerRegistry
  connectRegistryToKindNetwork
}

function dnsmasqSetup() {
  # https://eengstrom.github.io/musings/local-dns-resolvers-on-mac-os-x
  brew install dnsmasq
  etc="$(brew --prefix)/etc"
  mkdir -pv ${etc} > /dev/null 2>&1
  echo 'address=/.localhost/127.0.0.1' >> ${etc}/dnsmasq.conf
  sudo mkdir -v /etc/resolver > /dev/null 2>&1
  sudo bash -c 'echo "nameserver 127.0.0.1" > /etc/resolver/localhost'
  sudo brew services start dnsmasq
}

function dnsmasqRestart() {
  brew services stop dnsmasq
  brew services start dnsmasq
}

function assertLocalCluster() {
  # Wait for polaris certificaterequest to be ready
  echo -n ">>> Waiting for Polaris certificate request to be ready ... "
  certRequestName=$(kubectl get certificaterequests -n polaris -o jsonpath={.items[0].metadata.name})
  kubectl wait certificaterequest/${certRequestName} -n polaris --for=condition=ready=true > /dev/null 2>&1
  echo "READY!"

  # kubectl wait doesn't support jsonpath yet
  echo -n ">>> Waiting for Polaris ingress to be available ..."
  while [ "$(kubectl get ingress/polaris -n polaris -o jsonpath={.status.loadBalancer.ingress[0].hostname})" != "localhost" ]; do
    printf '.'
    sleep 5
  done
  echo " READY!"

  # Check that Polaris deployed correctly and we can access it's health endpoint
  echo -n ">>> Testing deployed application is healthy ..."
  response=`curl -s https://polaris.${domain}/health`
  if [ "${response}" = "OK" ]; then
    echo " SUCCESS!"
  else
    echo " FAILURE :("
  fi
}