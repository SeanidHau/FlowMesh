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
