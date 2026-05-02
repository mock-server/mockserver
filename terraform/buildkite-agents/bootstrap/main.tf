terraform {
  required_version = ">= 1.15"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

provider "aws" {
  region  = "eu-west-2"
  profile = "mockserver-build"
}

locals {
  project     = "mockserver"
  bucket_name = "${local.project}-terraform-state"
  table_name  = "${local.project}-terraform-locks"
  profile     = "mockserver-build"
  region      = "eu-west-2"
}

data "external" "s3_bucket_exists" {
  program = [
    "bash", "-c",
    "${path.module}/scripts/check_s3_bucket_exists.sh ${local.bucket_name} ${local.profile}"
  ]
}

data "external" "dynamodb_table_exists" {
  program = [
    "bash", "-c",
    "${path.module}/scripts/check_dynamodb_table_exists.sh ${local.table_name} ${local.profile} ${local.region}"
  ]
}

resource "aws_s3_bucket" "terraform_state" {
  bucket = local.bucket_name
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket                  = aws_s3_bucket.terraform_state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "terraform_locks" {
  name         = local.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}

import {
  for_each = data.external.s3_bucket_exists.result.name != "unknown" ? { bucket = local.bucket_name } : {}
  id       = each.value
  to       = aws_s3_bucket.terraform_state
}

import {
  for_each = data.external.s3_bucket_exists.result.name != "unknown" ? { bucket = local.bucket_name } : {}
  id       = each.value
  to       = aws_s3_bucket_versioning.terraform_state
}

import {
  for_each = data.external.s3_bucket_exists.result.name != "unknown" ? { bucket = local.bucket_name } : {}
  id       = each.value
  to       = aws_s3_bucket_server_side_encryption_configuration.terraform_state
}

import {
  for_each = data.external.s3_bucket_exists.result.name != "unknown" ? { bucket = local.bucket_name } : {}
  id       = each.value
  to       = aws_s3_bucket_public_access_block.terraform_state
}

import {
  for_each = data.external.dynamodb_table_exists.result.name != "unknown" ? { table = local.table_name } : {}
  id       = each.value
  to       = aws_dynamodb_table.terraform_locks
}

output "state_bucket" {
  value = aws_s3_bucket.terraform_state.bucket
}

output "lock_table" {
  value = aws_dynamodb_table.terraform_locks.name
}
