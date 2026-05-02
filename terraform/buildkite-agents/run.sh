#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AWS_PROFILE="mockserver-build"
AWS_REGION="eu-west-2"

usage() {
  cat <<EOF
Usage: $(basename "$0") [command]

Commands:
  plan       Run terraform plan (default)
  apply      Run terraform apply
  destroy    Run terraform destroy
  bootstrap  Initialise the S3 state bucket and DynamoDB lock table
  init       Run terraform init

AWS profile: $AWS_PROFILE
Region:      $AWS_REGION
EOF
}

check_aws_auth() {
  echo "Checking AWS authentication (profile: $AWS_PROFILE)..."
  if ! aws sts get-caller-identity --profile "$AWS_PROFILE" --region "$AWS_REGION" > /dev/null 2>&1; then
    echo ""
    echo "Not authenticated. Run the following command to log in:"
    echo ""
    echo "  aws sso login --profile $AWS_PROFILE"
    echo ""
    read -rp "Would you like to run it now? [Y/n] " answer
    answer="${answer:-Y}"
    if [[ "$answer" =~ ^[Yy] ]]; then
      aws sso login --profile "$AWS_PROFILE"
      if ! aws sts get-caller-identity --profile "$AWS_PROFILE" --region "$AWS_REGION" > /dev/null 2>&1; then
        echo "Authentication failed. Exiting."
        exit 1
      fi
    else
      echo "Exiting."
      exit 1
    fi
  fi
  echo "Authenticated."
  echo ""
}

run_bootstrap() {
  echo "Bootstrapping state backend..."
  cd "$SCRIPT_DIR/bootstrap"
  terraform init -upgrade
  terraform apply -auto-approve
  echo ""
  echo "Bootstrap complete. State bucket and lock table are ready."
}

run_terraform() {
  local cmd="${1:-plan}"
  cd "$SCRIPT_DIR"
  terraform init -input=false

  case "$cmd" in
    plan)
      terraform plan
      ;;
    apply)
      terraform apply
      ;;
    destroy)
      terraform destroy
      ;;
    *)
      echo "Unknown command: $cmd"
      usage
      exit 1
      ;;
  esac
}

if [[ -n "${NODE_EXTRA_CA_CERTS:-}" && -z "${AWS_CA_BUNDLE:-}" ]]; then
  export AWS_CA_BUNDLE="$NODE_EXTRA_CA_CERTS"
fi

if [[ "$(uname)" == "Darwin" && -d "/opt/homebrew/opt/expat/lib" ]]; then
  export DYLD_LIBRARY_PATH="/opt/homebrew/opt/expat/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
fi

COMMAND="${1:-plan}"

if [[ "$COMMAND" == "-h" || "$COMMAND" == "--help" ]]; then
  usage
  exit 0
fi

check_aws_auth

case "$COMMAND" in
  bootstrap)
    run_bootstrap
    ;;
  init|plan|apply|destroy)
    run_terraform "$COMMAND"
    ;;
  *)
    echo "Unknown command: $COMMAND"
    usage
    exit 1
    ;;
esac
