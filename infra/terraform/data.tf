# データの置き場所：RDS・S3・ECR・パラメータストア（デプロイ設計書 2・4 章）
# 3 日で消すので、S3・ECR は中身があっても消せるようにし、RDS の最終のスナップショットは取らない

resource "aws_db_subnet_group" "main" {
  name       = var.project_name
  subnet_ids = aws_subnet.private[*].id
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_instance" "main" {
  identifier              = var.project_name
  engine                  = "postgres"
  engine_version          = "18.6"
  instance_class          = var.db_instance_class
  storage_type            = "gp3"
  allocated_storage       = 20
  db_name                 = "raisetimeline"
  username                = "raisetimeline"
  password                = random_password.db.result
  db_subnet_group_name    = aws_db_subnet_group.main.name
  vpc_security_group_ids  = [aws_security_group.db.id]
  backup_retention_period = 1
  skip_final_snapshot     = true
}

resource "random_bytes" "jwt_secret" {
  length = 32
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project_name}/db-password"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${var.project_name}/jwt-secret"
  type  = "SecureString"
  value = random_bytes.jwt_secret.base64
}

resource "aws_ecr_repository" "api" {
  name         = "${var.project_name}-api"
  force_delete = true
}

resource "aws_s3_bucket" "web" {
  bucket        = "${var.project_name}-web-${local.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket" "images" {
  bucket        = "${var.project_name}-images-${local.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = aws_s3_bucket.web.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 画面用のバケットは CloudFront からだけ読める
data "aws_iam_policy_document" "web_bucket" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.web.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = data.aws_iam_policy_document.web_bucket.json
}
