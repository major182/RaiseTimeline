# 外から渡す値。実際の値は terraform.tfvars に書く（リポジトリに入れない）

variable "project_name" {
  description = "資源の名前の先頭に付ける名前"
  type        = string
  default     = "raisetimeline"
}

variable "aws_region" {
  description = "資源を作るリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "allowed_ip" {
  description = "画面と API に接続できる開発者の IP（1 つ）。curl https://checkip.amazonaws.com で確かめる"
  type        = string

  validation {
    condition     = can(regex("^[0-9]{1,3}([.][0-9]{1,3}){3}$", var.allowed_ip))
    error_message = "allowed_ip は 203.0.113.10 のような IPv4 アドレスで書いてください。"
  }
}

variable "desired_count" {
  description = "Fargate のタスクの数。普段は 1、NF-AV-02 の確認のときだけ 2（Q-06）"
  type        = number
  default     = 1
}

variable "task_cpu" {
  description = "タスクの CPU（1024 = 1 vCPU）"
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "タスクのメモリ（MB）"
  type        = number
  default     = 1024
}

variable "db_instance_class" {
  description = "RDS の大きさ"
  type        = string
  default     = "db.t4g.micro"
}

variable "github_repository" {
  description = "自動デプロイを許すリポジトリ（owner/name）"
  type        = string
  default     = "major182/RaiseTimeline"
}
