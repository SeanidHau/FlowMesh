{{/* 作用：统一生成 Chart 名称和资源标签。 */}}
{{- define "flowmesh.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "flowmesh.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name (include "flowmesh.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "flowmesh.labels" -}}
app.kubernetes.io/name: {{ include "flowmesh.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{/* 作用：允许部署复用外部 Secret，也兼容由 Chart 创建的本地 Secret。 */}}
{{- define "flowmesh.configSecret" -}}
{{- default (printf "%s-config" (include "flowmesh.fullname" .)) .Values.global.existingSecret -}}
{{- end }}

{{/* 作用：为应用和维护 CronJob 统一注入可选的私有镜像仓库凭据。 */}}
{{- define "flowmesh.imagePullSecrets" -}}
{{- with .Values.global.imagePullSecrets }}
imagePullSecrets:
{{- toYaml . | nindent 2 }}
{{- end }}
{{- end }}

{{/* 作用：统一生成镜像引用；生产模式必须绑定不可变的提交 SHA 标签。 */}}
{{- define "flowmesh.imageReference" -}}
{{- $root := .root -}}
{{- $service := .service -}}
{{- $registry := trimSuffix "/" (default "" $root.Values.global.imageRegistry) -}}
{{- $tag := default $service.tag $root.Values.global.imageTag -}}
{{- if $root.Values.global.production }}
{{- $tag = required "global.imageTag is required in production mode" $root.Values.global.imageTag -}}
{{- end }}
{{- if $registry }}{{ printf "%s/%s" $registry $service.image }}{{ else }}{{ $service.image }}{{ end }}:{{ $tag }}
{{- end }}

{{/* 作用：统一注入可选 OpenTelemetry Trace 配置，并阻止生产导出缺少 Collector 地址。 */}}
{{- define "flowmesh.observabilityEnv" -}}
{{- if and .Values.global.production .Values.observability.tracing.exportEnabled (not .Values.observability.tracing.endpoint) }}
{{- fail "observability.tracing.endpoint is required when OTLP export is enabled in production mode" }}
{{- end }}

- name: FLOWMESH_TRACING_ENABLED
  value: {{ .Values.observability.tracing.enabled | quote }}
- name: FLOWMESH_OTEL_EXPORT_ENABLED
  value: {{ .Values.observability.tracing.exportEnabled | quote }}
- name: OTEL_EXPORTER_OTLP_TRACES_ENDPOINT
  value: {{ .Values.observability.tracing.endpoint | quote }}
- name: FLOWMESH_TRACING_SAMPLING_PROBABILITY
  value: {{ .Values.observability.tracing.samplingProbability | quote }}
- name: FLOWMESH_OTEL_CONNECT_TIMEOUT
  value: {{ .Values.observability.tracing.connectTimeout | quote }}
- name: FLOWMESH_OTEL_EXPORT_TIMEOUT
  value: {{ .Values.observability.tracing.exportTimeout | quote }}
{{- end }}

{{/* 作用：统一注入 RocketMQ 访问通道，所有消息服务都需要该配置。 */}}
{{- define "flowmesh.rocketmqCommonEnv" -}}
- name: ROCKETMQ_ACCESS_CHANNEL
  value: {{ .Values.rocketmq.accessChannel | quote }}
{{- end }}

{{/* 作用：仅向实际发布消息的服务注入 Producer 凭据和 TLS 配置。 */}}
{{- define "flowmesh.rocketmqProducerEnv" -}}
- name: ROCKETMQ_PRODUCER_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: {{ include "flowmesh.configSecret" . }}
      key: ROCKETMQ_PRODUCER_ACCESS_KEY
- name: ROCKETMQ_PRODUCER_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: {{ include "flowmesh.configSecret" . }}
      key: ROCKETMQ_PRODUCER_SECRET_KEY
- name: ROCKETMQ_PRODUCER_TLS_ENABLED
  value: {{ .Values.rocketmq.producer.tlsEnabled | quote }}
{{- end }}

{{/* 作用：仅向实际消费消息的服务注入 Consumer 凭据和 TLS 配置。 */}}
{{- define "flowmesh.rocketmqConsumerEnv" -}}
- name: ROCKETMQ_CONSUMER_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: {{ include "flowmesh.configSecret" . }}
      key: ROCKETMQ_CONSUMER_ACCESS_KEY
- name: ROCKETMQ_CONSUMER_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: {{ include "flowmesh.configSecret" . }}
      key: ROCKETMQ_CONSUMER_SECRET_KEY
- name: ROCKETMQ_CONSUMER_TLS_ENABLED
  value: {{ .Values.rocketmq.consumer.tlsEnabled | quote }}
{{- end }}

{{/* 作用：统一生成备份镜像引用，并在生产模式阻止使用可变默认标签。 */}}
{{- define "flowmesh.backupImageReference" -}}
{{- $registry := trimSuffix "/" (default "" .Values.global.imageRegistry) -}}
{{- $tag := default .Values.backup.tag .Values.global.imageTag -}}
{{- if .Values.global.production }}
{{- $tag = required "global.imageTag is required in production mode" .Values.global.imageTag -}}
{{- end }}
{{- if $registry }}{{ printf "%s/%s" $registry .Values.backup.image }}{{ else }}{{ .Values.backup.image }}{{ end }}:{{ $tag }}
{{- end }}

{{/* 作用：统一生成生命周期维护镜像引用，并在生产模式阻止使用可变默认标签。 */}}
{{- define "flowmesh.retentionImageReference" -}}
{{- $registry := trimSuffix "/" (default "" .Values.global.imageRegistry) -}}
{{- $tag := default .Values.retention.tag .Values.global.imageTag -}}
{{- if .Values.global.production }}
{{- $tag = required "global.imageTag is required in production mode" .Values.global.imageTag -}}
{{- end }}
{{- if $registry }}{{ printf "%s/%s" $registry .Values.retention.image }}{{ else }}{{ .Values.retention.image }}{{ end }}:{{ $tag }}
{{- end }}

{{/* 作用：生成 Workflow SLA CronJob 的 PostgreSQL 客户端镜像，并在生产模式强制使用 digest。 */}}
{{- define "flowmesh.workflowSlaImageReference" -}}
{{- if .Values.global.production -}}
{{- $digest := required "workflowSla.imageDigest is required in production mode" .Values.workflowSla.imageDigest -}}
{{- if not (regexMatch "^sha256:[a-f0-9]{64}$" $digest) -}}
{{- fail "workflowSla.imageDigest must be a sha256 digest" -}}
{{- end -}}
{{ printf "%s@%s" .Values.workflowSla.image $digest }}
{{- else -}}
{{ printf "%s:%s" .Values.workflowSla.image .Values.workflowSla.tag }}
{{- end -}}
{{- end }}

{{/* 作用：生产使用 PostgreSQL 服务端身份校验时，强制要求 CA Secret，避免 verify 模式退化为未验证的连接。 */}}
{{- define "flowmesh.postgresqlValidation" -}}
{{- if and .Values.global.production (has .Values.postgresql.sslMode (list "verify-ca" "verify-full")) (not .Values.postgresql.caSecretName) }}
{{- fail "postgresql.caSecretName is required when PostgreSQL sslMode is verify-ca or verify-full in production mode" }}
{{- end }}
{{- end }}

{{/* 作用：统一生成业务服务使用的 PostgreSQL JDBC URL，并在提供 CA Secret 时启用证书校验文件。 */}}
{{- define "flowmesh.postgresqlJdbcUrl" -}}
{{- $root := .root -}}
{{- printf "jdbc:postgresql://%s:%v/%s?currentSchema=%s&sslmode=%s%s" $root.Values.postgresql.host $root.Values.postgresql.port $root.Values.postgresql.database .schema $root.Values.postgresql.sslMode (ternary "&sslrootcert=/etc/flowmesh/postgresql/ca.crt" "" (ne (default "" $root.Values.postgresql.caSecretName) "")) -}}
{{- end }}

{{/* 作用：向应用或维护任务挂载外部 PostgreSQL CA Secret，不在镜像中内置环境相关证书。 */}}
{{- define "flowmesh.postgresqlCaVolumeMount" -}}
{{- if .secretName }}
- name: postgresql-ca
  mountPath: /etc/flowmesh/postgresql
  readOnly: true
{{- end }}
{{- end }}

{{/* 作用：将外部 PostgreSQL CA Secret 以只读、固定文件名挂载到 Pod。 */}}
{{- define "flowmesh.postgresqlCaVolume" -}}
{{- if .secretName }}
- name: postgresql-ca
  secret:
    secretName: {{ .secretName | quote }}
    items:
      - key: {{ default "ca.crt" .secretKey | quote }}
        path: ca.crt
        mode: 0444
{{- end }}
{{- end }}
