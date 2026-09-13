# TUM 日历控制

Android 本地优先的 TUM 课程同步与个人出席规则控制器。它拟将已确认 Fixplatz 的课程按本地规则同步到应用自建的 Google 次级日历，并由荣耀日历显示。

当前版本：`0.1.10`（构建号 `11`）。完整迭代记录见 [VERSIONING.md](VERSIONING.md)。

当前状态：本地优先同步核心阶段。课程名称、地点和结构化星期／时分可保存为本机覆盖，并在列表与详情统一显示；来源状态可筛选显示。当前仍没有 Calendar Provider、真实 iCal、Google 或荣耀日历读写实现。

开始任何工作前后，必须阅读根目录 [AGENTS.md](AGENTS.md)。执行计划、验收结果与可复用规程只保存在本机 `.local-audit/`，不会纳入 Git 或 GitHub。
