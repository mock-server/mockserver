{{/* vim: set filetype=mustache: */}}

{{/* Chart name truncated at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec). */}}
{{- define "chart.name" -}}
    {{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Release name truncated at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec). */}}
{{- define "release.name" -}}
    {{- if .Values.releasenameOverride -}}
        {{- .Values.releasenameOverride | trunc 63 | trimSuffix "-" -}}
    {{- else if .Values.fullnameOverride -}}
        {{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
    {{- else -}}
        {{- .Release.Name | trunc 63 | trimSuffix "-" -}}
    {{- end -}}
{{- end -}}

{{/* Create chart name and version as used by the chart label. */}}
{{- define "chart.name_version" -}}
    {{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}
