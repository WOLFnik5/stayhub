# Етап 1. Booking Service у Kubernetes

Лабораторія працює в окремому кластері `kind-stayhub` і namespace `stayhub-lab`.
Booking Service стартує з двома репліками за `ClusterIP` Service `booking`.
PostgreSQL і Kafka працюють в одному екземплярі кожен, із власними PVC.

## Запуск

Потрібні Docker Desktop з Linux engine, `kubectl` 1.34 або 1.35, [kind](https://kind.sigs.k8s.io/docs/user/quick-start/) 0.33 і PowerShell 7.4 або новіший. Образ вузла Kubernetes 1.34 підтримує Docker Engine з cgroup v1. Виконай з кореня репозиторію:

```powershell
.\ops\k8s\New-LabCluster.ps1
.\ops\k8s\Start-Lab.ps1
```

Скрипт збирає Docker image, позначає його тегом на основі image ID і завантажує його в kind. Потім створює ConfigMap і три Secrets, чекає на PostgreSQL та Kafka, запускає Liquibase окремим Job і тільки після успішної міграції оновлює API. Повторний запуск зберігає паролі, ключ JWT та дані на PVC. Облікові дані беруться з лабораторних Secrets. Для Stripe використовуються синтетичні значення, а Telegram consumer вимкнений. Для вправи використовуй health, авторизацію та Booking API без платіжних запитів.

## Перевірка

У PowerShell створи скорочення для команд лабораторії:

```powershell
function kl { kubectl --context kind-stayhub -n stayhub-lab @args }
kl get deployment booking
kl get pods -l app=booking
kl get endpointslice -l kubernetes.io/service-name=booking
kl get jobs
kl port-forward service/booking 18081:8080
```

З іншого термінала перевір `Invoke-RestMethod http://localhost:18081/actuator/health/readiness` або відкрий [Swagger UI](http://localhost:18081/swagger-ui.html). Після заміни Pod запусти `port-forward` знову, оскільки тунель прив’язаний до конкретного Pod.

## Запуск на іншому ПК з Windows 11 Pro

На цьому ПК запусти Docker Desktop з Linux engine і відкрий PowerShell 7 у корені репозиторію. Знайди IPv4 активного адаптера Ethernet або Wi-Fi:

```powershell
Get-NetIPConfiguration |
    Where-Object IPv4DefaultGateway |
    Select-Object InterfaceAlias, @{Name='IPv4';Expression={$_.IPv4Address.IPAddress}}
```

Підстав адресу цього адаптера замість `192.168.1.50`. Застосуй `-LanAddress` під час створення кластера, оскільки Docker налаштовує прив’язку портів під час створення kind-вузла.

```powershell
$lanIp = '192.168.1.50'
.\ops\k8s\New-LabCluster.ps1 -LanAddress $lanIp
.\ops\k8s\Start-Lab.ps1
kubectl --context kind-stayhub -n stayhub-lab apply -f .\ops\k8s\booking-lan.yaml
```

На ПК із кластером у PowerShell від імені адміністратора дозволь вхідний TCP `18081` з локальної підмережі для профілю `Private`. Вкажи ту саму LAN-адресу.

```powershell
New-NetFirewallRule -Name 'StayHub-Booking-LAN' -DisplayName 'StayHub Booking LAN' `
    -Direction Inbound -Action Allow -Protocol TCP -LocalAddress '192.168.1.50' `
    -LocalPort 18081 -RemoteAddress LocalSubnet -Profile Private
```

З робочого ПК перевір `Invoke-RestMethod http://192.168.1.50:18081/actuator/health/readiness`. Swagger доступний на `http://192.168.1.50:18081/swagger-ui.html`. Docker направляє `18081` на NodePort `30081`, а Service розподіляє запити між готовими Pod. [Налаштування портів kind](https://kind.sigs.k8s.io/docs/user/configuration/#nodeport-with-port-mappings)

### Доступ до Kubernetes API з робочого ПК

Kubernetes API слухає `127.0.0.1:16443` на ПК із кластером. Для віддаленого `kubectl` налаштуй [OpenSSH Server у Windows](https://learn.microsoft.com/en-us/windows-server/administration/openssh/openssh_install_firstuse). Для вхідного SSH використовуй приватний профіль і дозволь доступ із робочого ПК. Підключайся під обліковим записом, який створив кластер і має `kubectl` у `PATH`.

На робочому ПК заміни ім’я та IP у `$labSsh` і збережи kubeconfig цього кластера. Файл містить облікові дані адміністратора, тому зберігай його в `.kube` поза репозиторієм.

```powershell
$labSsh = 'user@192.168.1.50'
$labKubeconfig = Join-Path $env:USERPROFILE '.kube/stayhub-lan.yaml'
New-Item -ItemType Directory -Force (Split-Path $labKubeconfig) | Out-Null
$labConfig = ssh $labSsh 'kubectl config view --context kind-stayhub --minify --flatten --raw'
if ($LASTEXITCODE -ne 0 -or -not $labConfig) { throw 'Не вдалося отримати kubeconfig лабораторії.' }
$labConfig | Set-Content -LiteralPath $labKubeconfig -Encoding utf8
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:16443:127.0.0.1:16443 $labSsh
```

Залиш SSH-тунель відкритим і в іншому терміналі виконай:

```powershell
kubectl --kubeconfig "$env:USERPROFILE/.kube/stayhub-lan.yaml" get nodes
kubectl --kubeconfig "$env:USERPROFILE/.kube/stayhub-lan.yaml" -n stayhub-lab get pods
```

## Вправа з масштабування й Rolling Update

```powershell
kl scale deployment/booking --replicas=3
kl rollout status deployment/booking --timeout=310s
kl get pods -l app=booking
kl rollout restart deployment/booking
kl rollout status deployment/booking --timeout=310s
kl get replicasets -l app=booking
```

Оголоси `kl` у другому терміналі та виконай `kl get pods -l app=booking -w`, щоб стежити за Pod під час перезапуску. Deployment піднімає один додатковий Pod за раз і чекає, доки він стане доступним, перш ніж прибрати старий. Для перевірки запитів через внутрішній Service виконай:

```powershell
kl exec deployment/booking -- curl -fsS http://booking:8080/actuator/health/readiness
kl exec deployment/booking -- curl -fsS 'http://booking:8080/accommodations?page=0&size=1'
```

Видалення всієї лабораторії разом із її даними та Secrets виконується командою `kind delete cluster --name stayhub`.

## Етап 2. Публікація образу в GHCR

Після push змін workflow відкрий **Actions → Java CI → Run workflow**, вибери `dev2/kubernetes-lab` і запусти workflow. Після успішних тестів завдання `publish` збирає образ для `linux/amd64` та публікує його в GHCR. Зі сторінки **Summary** скопіюй повне посилання `ghcr.io/<owner>/<repo>@sha256:...`. Цей digest потрібен для GitOps-маніфесту.

### Перехід на Argo CD

Почни з працюючої лабораторії етапу 1. Argo CD керуватиме Deployment, ConfigMap, обома Service та запуском Liquibase. PostgreSQL, Kafka та Secrets підготуй скриптом `Start-Lab.ps1` на етапі 1. Після переходу змінюй образ і кількість реплік у Git.

Після push GitOps-маніфестів виконай на ПК із кластером:

```powershell
$labContext = 'kind-stayhub'
$argoInstall = 'https://raw.githubusercontent.com/argoproj/argo-cd/v3.5.7/manifests/install.yaml'
$labApplication = 'https://raw.githubusercontent.com/WOLFnik5/stayhub/refs/heads/dev2/kubernetes-lab/ops/k8s/gitops/application.yaml'

kubectl --context $labContext create namespace argocd --dry-run=client -o yaml |
    kubectl --context $labContext apply -f -
kubectl --context $labContext -n argocd apply --server-side --force-conflicts -f $argoInstall
kubectl --context $labContext wait --for=condition=Established crd/applications.argoproj.io --timeout=120s
kubectl --context $labContext apply -f $labApplication
kubectl --context $labContext -n argocd get application stayhub-lab -w
```

Argo CD читає `ops/k8s/kustomization.yaml` із `dev2/kubernetes-lab`. Kustomize підставляє той самий digest у Job міграції та Deployment. `PreSync` запускає Liquibase перед оновленням API. Після успіху очікуй `Synced` і `Healthy`, потім перевір образ у Deployment і readiness через LAN:

```powershell
kubectl --context kind-stayhub -n stayhub-lab get deployment booking -o 'jsonpath={.spec.template.spec.containers[0].image}'
Invoke-RestMethod http://192.168.0.99:18081/actuator/health/readiness
```

Для наступного релізу опублікуй новий образ ручним запуском **Java CI**, зміни `images[0].digest` у `ops/k8s/kustomization.yaml` і запуш коміт. Для перевірки рендерингу перед push виконай `kubectl kustomize ops/k8s`.
