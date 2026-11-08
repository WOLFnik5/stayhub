#Requires -Version 7.4
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$kubeArgs = @('--context', 'kind-stayhub', '--namespace', 'stayhub-lab')
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

function New-LabToken {
    [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
}

function Initialize-LabSecret($name, $values) {
    if (kubectl @kubeArgs get secret $name --ignore-not-found -o name) {
        return
    }

    @{
        apiVersion = 'v1'
        kind = 'Secret'
        metadata = @{ name = $name }
        type = 'Opaque'
        stringData = $values
    } | ConvertTo-Json -Depth 4 | kubectl @kubeArgs create -f -
}

kubectl @kubeArgs get nodes --request-timeout=10s

$localTag = 'stayhub-booking:k8s-lab'
docker build --provenance=false --tag $localTag $repoRoot
$imageId = docker image inspect --format '{{.Id}}' $localTag
$image = "stayhub-booking:lab-$($imageId -replace '^sha256:', '')"
docker tag $imageId $image
kind load docker-image $image --name stayhub

kubectl @kubeArgs apply -f (Join-Path $PSScriptRoot 'namespace.yaml')
Initialize-LabSecret 'postgres-admin' @{
    POSTGRES_PASSWORD = New-LabToken
}
Initialize-LabSecret 'booking-migration' @{
    LIQUIBASE_USERNAME = 'booking_migrator'
    LIQUIBASE_PASSWORD = New-LabToken
}
Initialize-LabSecret 'booking-runtime' @{
    DB_PASSWORD = New-LabToken
    JWT_SECRET = New-LabToken
    STRIPE_SECRET_KEY = 'sk_test_kubernetes_lab'
    STRIPE_WEBHOOK_SECRET = "whsec_$(New-LabToken)"
    TELEGRAM_BOT_TOKEN = 'kubernetes-lab-bot'
    TELEGRAM_CHAT_ID = 'kubernetes-lab-chat'
}

$provisionRoles = Join-Path $repoRoot 'ops/postgres/001-provision-roles.sh'
kubectl @kubeArgs create configmap postgres-init --from-file=$provisionRoles --dry-run=client -o yaml |
    kubectl @kubeArgs apply -f -
kubectl @kubeArgs apply -f (Join-Path $PSScriptRoot 'config.yaml')
kubectl @kubeArgs apply -f (Join-Path $PSScriptRoot 'dependencies.yaml')
kubectl @kubeArgs rollout status statefulset/postgres statefulset/kafka --timeout=300s

$migration = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'migrate.yaml')
$migration = $migration.Replace('  name: booking-migrate', '  generateName: booking-migrate-')
$job = $migration.Replace($localTag, $image) | kubectl @kubeArgs create -f - -o name
try {
    kubectl @kubeArgs wait --for=condition=complete $job --timeout=310s
} catch {
    kubectl @kubeArgs logs $job
    throw
}

$deployment = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'booking.yaml')
$deployment.Replace($localTag, $image) | kubectl @kubeArgs apply -f -
kubectl @kubeArgs rollout status deployment/booking --timeout=310s
kubectl @kubeArgs get pods,services
kubectl @kubeArgs exec deployment/booking -- curl -fsS http://booking:8080/actuator/health/readiness
