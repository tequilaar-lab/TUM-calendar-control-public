# TUM 日历控制

Android 本地优先的 TUM 课程同步与个人出席规则控制器。它拟将已确认 Fixplatz 的课程按本地规则同步到应用自建的 Google 次级日历，并由荣耀日历显示。

当前版本：`0.1.4`（构建号 `5`）。完整迭代记录见 [VERSIONING.md](VERSIONING.md)。

当前状态：MIUIX 离线规则原型阶段。工程只含合成课程，用于验证 Fixplatz 准入、绿／蓝／灰三类显示和“从某日以后不去”的历史保护；可独立保存“本机荣耀日历输出”与“Google 日历镜像输出”的未来许可，但尚未配置 OAuth、读取真实 iCal、申请日历权限或连接／写入 Google／荣耀日历。

开始任何工作前后，必须阅读根目录 [AGENTS.md](AGENTS.md)。执行计划、验收结果与可复用规程只保存在本机 `.local-audit/`，不会纳入 Git 或 GitHub。
