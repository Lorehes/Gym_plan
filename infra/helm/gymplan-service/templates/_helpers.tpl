{{- define "gymplan-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "gymplan-service.labels" -}}
app.kubernetes.io/name: {{ include "gymplan-service.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: gymplan
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "gymplan-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "gymplan-service.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
