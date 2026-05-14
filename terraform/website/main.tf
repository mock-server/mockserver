// Profiles are intentionally not set. Authentication uses the standard AWS
// credential chain (env vars / instance role). Developers running terraform
// manually should `export AWS_PROFILE=mockserver-website` first; the release
// script in scripts/release/components/versioned-site.sh materialises
// website-account creds into env vars via assume_website_role.
provider "aws" {
  region = "us-east-1"
}

provider "aws" {
  alias  = "eu-west-2"
  region = "eu-west-2"
}
