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
