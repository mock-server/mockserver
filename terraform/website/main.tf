provider "aws" {
  region  = "us-east-1"
  profile = "mockserver-website"
}

provider "aws" {
  alias   = "eu-west-2"
  region  = "eu-west-2"
  profile = "mockserver-website"
}
