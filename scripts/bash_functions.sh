#!/usr/bin/env bash

# Common Bash Functions
abort() {
  echo >&2 '
***************
*** ABORTED ***
***************
'
  echo "An error occurred. Exiting..." >&2
  exit 1
}

warning () {
  echo >&2 "WARNING:" "$@"
}

die () {
  echo >&2 "$@"
  abort
}

echo_array() {
  for value in "$@"; do
    echo "${value}"
  done
}

what_os() {
  uname_out="$(uname -s)"
  case "${uname_out}" in
      Linux*)     machine=Linux;;
      Darwin*)    machine=MacOS;;
      CYGWIN*)    machine=Cygwin;;
      MINGW*)     machine=MinGw;;
      *)          machine="UNKNOWN:${uname_out}"
  esac
  echo "${machine}"
}

contains_files() {
  local file_list=($@)
  for file in "$@"; do
    if ! [ -f "${file}" ]
    then
      return 1
    fi
  done
  return 0
}

check_not_empty () {
  local suggestion=${2:-}
  if [[ -z "${1// }" ]]; then
    die "Argument is empty. ${suggestion}"
  fi
}

check_not_empty_variable () {
  local var=$1
  local suggestion=${2:-}
  local empty_or_x
  local value

  # http://stackoverflow.com/questions/3601515/how-to-check-if-a-variable-is-set-in-bash
  empty_or_x=$(eval echo "\${${var}+x}")
  value=$(eval echo "\${${var}}")

  if [ -z "${empty_or_x}" ]; then
    die "Variable '${var}' is unset. ${suggestion}"
  elif [ -z "${value}" ]; then
    die "Variable '${var}' is empty. ${suggestion}"
  fi
}

check_file_exists () {
  local file=$1
  local suggestion=${2:-}
  if [ ! -f "${file}" ]; then
    die "File not found: '${file}' (PWD='${PWD}'). ${suggestion}"
  fi
}

check_file_not_exists () {
  local file=$1
  local suggestion=${2:-}
  if [ -f "${file}" ]; then
    die "File found: '${file}' (PWD='${PWD}'). ${suggestion}"
  fi
}

check_file_not_empty () {
  local file=$1
  local suggestion=${2:-}
  check_file_exists "${file}" "${suggestion}"
  if [ ! -s "${file}" ]; then
    die "File is empty: '${file}' (PWD='${PWD}'). ${suggestion}"
  fi
}

check_dir_exists () {
  local directory=$1
  local suggestion=${2:-}
  if [ ! -d "${directory}" ]; then
    die "Directory not found: '${directory}' (PWD='${PWD}'). ${suggestion}"
  fi
}

check_command_exists () {
  local command=$1
  local suggestion=${2:-}
  hash "${command}" 2>/dev/null || die "Command '${command}' is required but it's not installed. ${suggestion}"
}

delete_file_if_exists() {
  local file=$1
  if [ -f "${file}" ]; then
    rm "${file}"
  fi
}

copy_files() {
  local target_path=$1
  local file_list=(${@:2})

  mkdir -p "${target_path}"

  echo
  for file in "${file_list[@]}"; do
    echo "Copying ${file} ${target_path}"
    cp -r "${file}" "${target_path}"
  done
}

strip_prefix_and_file_from_path() {
  local input=$1
  local prefix_pattern=$2
  local prefixless

  prefixless="${input##${prefix_pattern}}"

  dirname "${prefixless}"
}

strip_suffix() {
  local input=$1
  local suffix_pattern=$2

  echo "${input%${suffix_pattern}}"
}

print_function_arguments() {
  local func=$1
  declare -f "$func" | grep -E "local.+\\\$[\{\(]*[0-9@]+"
}

retry() {
  local command=$@
  delay=10
  max_attempt=20
  attempt=0
  until [ ${attempt} -ge ${max_attempt} ]
  do
    if ${command}; then
      break
    fi;

    attempt=$[$attempt+1]
    echo "Retry #${attempt}/${max_attempt}, wait ${delay} seconds"
    sleep ${delay}
  done
}

mac_compatibility() {
  case "$(what_os)" in
    MacOS)
      echo "Checking MacOS compatibility"
      check_command_exists greadlink "Read bin/README.md"
      # shellcheck disable=SC2034
      readlink="greadlink"
      ;;
    Linux)
      # shellcheck disable=SC2034
      readlink="readlink"
      ;;
    *)
      die "Sorry, but this script doesn't support your system :("
      ;;
  esac
}

mac_compatibility
