# TUM 日历控制

Android 本地优先的 TUM 课程同步与个人出席规则控制器。它拟将已确认 Fixplatz 的课程按本地规则同步到应用自建的 Google 次级日历，并由荣耀日历显示。

当前版本：`0.1.6`（构建号 `7`）。完整迭代记录见 [VERSIONING.md](VERSIONING.md)。

当前状态：本地优先同步核心阶段。使用 iCal4j 以合成 RFC 5545 数据验证重复、例外、改期、取消及时区，并以 UID 与原始实例键进行差异规划；可安全保存未来的私有来源／Google 配置，但尚未读取真实 iCal、申请日历权限或连接／写入 Google／荣耀日历。

开始任何工作前后，必须阅读根目录 [AGENTS.md](AGENTS.md)。执行计划、验收结果与可复用规程只保存在本机 `.local-audit/`，不会纳入 Git 或 GitHub。
