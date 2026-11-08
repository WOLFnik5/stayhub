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

## Етап 3. Перевірка оновлення під навантаженням

Спочатку опублікуй образ із міграцією `019` та налаштуванням graceful shutdown. Зміну `ops/k8s/kustomization.yaml` збережи для окремого коміту разом із новим digest. Так Argo CD застосує зміну Pod template під час вимірюваного оновлення.

На своєму ПК запусти запити до каталогу через LAN. Скрипт створює нове HTTP-з'єднання для кожного запиту, рахує HTTP-статуси й транспортні помилки та показує p95 успішних запитів.

```powershell
.\ops\k8s\Measure-Rollout.ps1 -BaseUrl 'http://192.168.0.99:18081' -DurationSeconds 480
```

Поки скрипт працює, в іншому терміналі онови `images[0].digest` у `ops/k8s/kustomization.yaml` на digest нового образу, закоміть цей файл і зроби push. На ПК із кластером стеж за синхронізацією:

```powershell
kubectl --context kind-stayhub -n argocd get application stayhub-lab -w
```

Дочекайся `Synced` і `Healthy` для нового Git-коміту в межах заміру. `PreSync` додає частковий індекс каталогу через `CREATE INDEX CONCURRENTLY`, поки стара версія обслуговує запити. Потім Kubernetes оновлює репліки. `preStop` дає 5 секунд на оновлення маршрутизації перед `SIGTERM`, Spring має 20 секунд на завершення фази, а Pod отримує 60 секунд загалом.

У звіті очікуємо лише `200`. Звір Git-коміт, digest Deployment та наявність валідного індексу:

```powershell
kubectl --context kind-stayhub -n argocd get application stayhub-lab -o 'jsonpath={.status.sync.revision}'
kubectl --context kind-stayhub -n stayhub-lab get deployment booking -o 'jsonpath={.spec.template.spec.containers[0].image}'
kubectl --context kind-stayhub -n stayhub-lab exec statefulset/postgres -- psql -U booking_admin -d booking_app -tAc "SELECT indisvalid FROM pg_index WHERE indexrelid = to_regclass('public.idx_accommodations_available_id')"
```

Очікуване значення PostgreSQL — `t`. Для окремої перевірки зворотної сумісності повтори замір, повернувши старий digest у Git. Перевір роботу старого API на вже мігрованій схемі та знову застосуй новий digest. Якщо перерваний `CREATE INDEX CONCURRENTLY` залишив невалідний індекс, Liquibase зупинить повторну міграцію. Власник схеми має виконати `DROP INDEX CONCURRENTLY public.idx_accommodations_available_id` перед повторним sync.

## Етап 4. Observability

Argo CD керує стеком разом із Booking Service. Prometheus відкриває окремий target для кожного Pod, Alloy читає JSON-логи Booking Service й надсилає їх до Loki, а Spring Boot експортує OTLP-траси в Jaeger. Grafana завантажує дашборд StayHub і підключає всі три джерела даних. Alertmanager має локальний приймач для перегляду стану алертів. Prometheus і Loki використовують окремі PVC по 2 ГіБ і зберігають дані 24 години. Jaeger тримає траси в пам’яті.

### Перший запуск

Перед push маніфестів створи Secret у кластері. На ПК із кластером або в SSH-сесії виконай у PowerShell:

```powershell
$kube = @('--context', 'kind-stayhub', '-n', 'stayhub-lab')
$existing = kubectl @kube get secret monitoring-credentials --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw 'Не вдалося перевірити Secret monitoring-credentials.' }
if (-not $existing) {
    $metricsBytes = [byte[]]::new(32)
    $grafanaBytes = [byte[]]::new(32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($metricsBytes)
        $rng.GetBytes($grafanaBytes)
    } finally {
        $rng.Dispose()
    }
    @{
        apiVersion = 'v1'
        kind = 'Secret'
        metadata = @{ name = 'monitoring-credentials'; namespace = 'stayhub-lab' }
        type = 'Opaque'
        stringData = @{
            'metrics-password' = [Convert]::ToBase64String($metricsBytes)
            'grafana-password' = [Convert]::ToBase64String($grafanaBytes)
        }
    } | ConvertTo-Json -Depth 4 | kubectl @kube create -f -
    if ($LASTEXITCODE -ne 0) { throw 'Не вдалося створити Secret monitoring-credentials.' }
}
kubectl @kube get secret monitoring-credentials -o name
```

Переконайся, що остання команда повернула `secret/monitoring-credentials`. Після push дочекайся `Synced` і `Healthy` для нового коміту. На ПК із кластером перевір стан Pod і Prometheus targets:

```powershell
$kube = @('--context', 'kind-stayhub', '-n', 'stayhub-lab')
kubectl @kube get pods,pvc
$targets = kubectl @kube exec deployment/prometheus -- wget -qO- http://127.0.0.1:9090/api/v1/targets | ConvertFrom-Json
$targets.data.activeTargets | Select-Object @{Name='Pod'; Expression={$_.labels.pod}}, health, lastError
```

Для `booking` очікуємо два targets із `health = up`. Кількість targets змінюється разом із кількістю реплік. Для перевірки правил із синтетичними 503 і повільними запитами виконай `promtool test rules ops/monitoring/prometheus/rules-test.yml`.

### Grafana, логи й трасування

У робочому PowerShell відкрий SSH-з’єднання з локальним переадресуванням порту. Підстав Windows-користувача ПК із кластером:

```powershell
ssh -L 127.0.0.1:3000:127.0.0.1:3000 -l 'USER' 192.168.0.99
```

У цій SSH-сесії запусти `kubectl --context kind-stayhub -n stayhub-lab port-forward svc/grafana 3000:3000`. Залиш сесію відкритою і відкрий [Grafana](http://localhost:3000) на своєму ПК. Ім’я користувача `admin`. Пароль можна переглянути у своїй PowerShell-сесії на ПК із кластером:

```powershell
$encoded = kubectl --context kind-stayhub -n stayhub-lab get secret monitoring-credentials -o 'jsonpath={.data.grafana-password}'
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encoded))
```

- У дашборді **StayHub** перевір HTTP request rate, 5xx і p95.
- В **Explore → Loki** виконай `{app="booking"} | json`. Для записів із `traceId` доступне посилання в Jaeger.
- Виконай кілька запитів до [каталогу](http://192.168.0.99:18081/accommodations?page=0&size=1), потім в **Explore → Jaeger** знайди траси сервісу `booking-app`.

## Етап 5. Argo Rollouts

Після встановлення контролера Argo Rollouts запуш маніфести першої фази. Rollout `booking` використовує Pod template існуючого Deployment `booking` через `workloadRef`. Спочатку обидва контролери тримають по дві репліки того самого образу, а `booking-lan` спрямовує запити на готові Pod.

На ПК із кластером дочекайся готовності Rollout і переглянь обидва workloads:

```powershell
$kube = @('--context', 'kind-stayhub', '-n', 'stayhub-lab')
kubectl @kube wait '--for=jsonpath={.status.phase}=Healthy' rollout/booking --timeout=300s
kubectl @kube get deployment/booking rollout/booking
```

Після `Healthy` і двох готових реплік Rollout зафіксуй `replicas: 0` для Deployment в окремому коміті Kustomize. Deployment залишається джерелом Pod template. Наступна зміна digest у Git запускає новий Rollout.

Перший крок оновлення виставляє вагу Canary на 50%. Звичайний Kubernetes Service розподіляє трафік за співвідношенням готових Pod. Після паузи у 60 секунд `AnalysisTemplate` бере три заміри з Prometheus з інтервалом 20 секунд. Він фільтрує лише Pod нової версії за `rollout_hash`. Перевищення 5% HTTP 5xx або p95 у 1 секунду зупиняє реліз. Для кожного заміру потрібно щонайменше 10 запитів за хвилину до Canary. За недостатньої кількості запитів аналіз повертає `Inconclusive`, а Rollout призупиняється.

### Вправа з автоматичного abort

Почни з двох готових реплік Rollout і Deployment з `replicas: 0`. Профіль `rollout-fault` для цієї вправи повертає HTTP `503` на `GET /accommodations`. Readiness залишається `UP`, тому новий Pod бере участь у Canary й Prometheus бачить його HTTP-помилки.

1. Опублікуй образ із `RolloutFaultConfiguration` через ручний запуск **Java CI**. На ПК із кластером запиши хеш стабільного ReplicaSet командою `kubectl --context kind-stayhub -n stayhub-lab get rollout booking -o 'jsonpath={.status.stableRS}'`.
2. Запусти `Measure-Rollout.ps1` на 480 секунд. Поки він працює, зміни digest у `ops/k8s/kustomization.yaml` і додай `rollout-fault` до `SPRING_PROFILES_ACTIVE`. Запуш обидві зміни одним комітом.
3. На ПК із кластером перевір, що новий AnalysisRun отримав `Failed`, а `ABORT` у Rollout став `true`. Зістав `STABLE` з хешем, записаним до вправи.

   ```powershell
   $kube = @('--context', 'kind-stayhub', '-n', 'stayhub-lab')
   kubectl @kube get analysisruns
   kubectl @kube get rollout booking -o 'custom-columns=PHASE:.status.phase,ABORT:.status.abort,STABLE:.status.stableRS,NEW:.status.currentPodHash'
   ```

4. Після масштабування невдалого ReplicaSet до нуля повтори короткий замір. У першому замірі очікуємо `503` від Canary. У повторному всі запити мають повернути `200`. Потім віднови бажаний стан у Git, зробивши `git revert` коміту, що змінив digest і активував профіль.

### Перехід на Blue-Green

Почни зі стабільного `Synced / Healthy` після вправи Canary. `booking-lan` залишає адресу LAN і спрямовує трафік на активний ReplicaSet. Внутрішнє ім’я `booking:8080` веде до того самого Service через DNS. `booking-preview` дає окремий доступ до кандидата. Argo Rollouts змінює хеш ReplicaSet у селекторах двох Service, а Argo CD ігнорує саме це поле.

1. Опублікуй зміну `ops/k8s/gitops/application.yaml` окремим комітом. На ПК із кластером онови Application до публікації зміни стратегії.

   ```powershell
   $ctx = 'kind-stayhub'
   $appManifest = 'https://raw.githubusercontent.com/WOLFnik5/stayhub/refs/heads/dev2/kubernetes-lab/ops/k8s/gitops/application.yaml'
   kubectl --context $ctx apply -f $appManifest
   kubectl --context $ctx -n argocd get application stayhub-lab -o 'jsonpath={.spec.syncPolicy.syncOptions}'
   ```

2. Опублікуй решту маніфестів Blue-Green і дочекайся `Synced / Healthy`. Перед тестом порівняй `ACTIVE`, `PREVIEW` і `STABLE`. У стабільному стані вони мають збігатися.

   ```powershell
   $kube = @('--context', 'kind-stayhub', '-n', 'stayhub-lab')
   kubectl @kube get rollout booking -o 'custom-columns=PHASE:.status.phase,ACTIVE:.status.blueGreen.activeSelector,PREVIEW:.status.blueGreen.previewSelector,STABLE:.status.stableRS'
   ```

3. Для успішного тесту запуш новий здоровий digest або зміну анотації в `.spec.template.metadata.annotations` Deployment `booking`. Argo Rollouts створить новий ReplicaSet і зупиниться на `Paused`. Перевір, що `PREVIEW` змінився, а `ACTIVE` досі збігається з попереднім `STABLE`. На ПК із кластером відкрий тунель до кандидата в окремому вікні PowerShell.

   ```powershell
   kubectl --context kind-stayhub -n stayhub-lab port-forward svc/booking-preview 18082:8080
   ```

   В іншому вікні виконай `Invoke-RestMethod 'http://127.0.0.1:18082/accommodations?page=0&size=1'`. На своєму ПК запусти `Measure-Rollout.ps1` з адресою `http://192.168.0.99:18081` щонайменше на 180 секунд. Поки йдуть запити до LAN, вручну переведи кандидата в активну версію.

   ```powershell
   kubectl argo rollouts promote booking @kube
   kubectl @kube get analysisruns -w
   ```

   Для `promote` потрібен плагін kubectl Argo Rollouts. Очікуй `Successful` для нового AnalysisRun, `Healthy` для Rollout та HTTP `200` у LAN. Аналіз починає заміри через 45 секунд після перемикання і перевіряє 5xx та p95 тільки для нового ReplicaSet. [Встановлення плагіна](https://argo-rollouts.readthedocs.io/en/stable/installation/#kubectl-plugin-installation)

4. Для тесту повернення трафіку повтори крок 3 із образом та профілем `rollout-fault` з вправи Canary. У preview очікуй HTTP `503`, у LAN до promotion — `200`. Після навмисного promotion частина LAN-запитів має отримати `503`, доки аналіз не позначить оновлення як `Failed` і Argo Rollouts не поверне `ACTIVE` на попередній `STABLE`. Перевір відновлення окремим коротким заміром у LAN і зроби `git revert` коміту з несправним кандидатом.
