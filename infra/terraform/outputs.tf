# 手順と自動デプロイで使う値。公開の文書には書かず、terraform output で確かめる

output "url" {
  value = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "web_bucket" {
  value = aws_s3_bucket.web.bucket
}

output "distribution_id" {
  value = aws_cloudfront_distribution.main.id
}

output "github_actions_role_arn" {
  value = aws_iam_role.github_actions.arn
}
