#Requires -Version 7.4
param(
    [System.Net.IPAddress]$LanAddress
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$nodeImage = 'kindest/node:v1.34.11@sha256:44e222ee2132dab25ff87301682f89eb82c7880ea3a1bf543bfe9708fd08d67d'
$clusterArgs = @('create', 'cluster', '--name', 'stayhub', '--image', $nodeImage, '--wait', '180s')
if (-not $LanAddress) {
    kind @clusterArgs
    return
}

if ($LanAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    $LanAddress.Equals([System.Net.IPAddress]::Any) -or [System.Net.IPAddress]::IsLoopback($LanAddress)) {
    throw 'LanAddress must be the IPv4 address of the host LAN adapter.'
}

$kindConfig = [System.IO.Path]::GetTempFileName()
try {
    @"
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  apiServerAddress: "127.0.0.1"
  apiServerPort: 16443
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30081
        hostPort: 18081
        listenAddress: "$LanAddress"
        protocol: TCP
"@ | Set-Content -LiteralPath $kindConfig -Encoding utf8
    kind @clusterArgs --config $kindConfig
} finally {
    Remove-Item -LiteralPath $kindConfig
}
