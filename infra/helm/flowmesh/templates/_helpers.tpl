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
