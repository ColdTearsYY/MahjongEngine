# 在 Lophine 26.3 启用日麻

本指南用于 `ColdTearsYY/MahjongEngine` 的 `fix/lophine-26.3-compat` 分支。需要 Java 25、Lophine 26.3 build 843、官方 CraftEngine 26.9.2 Paper/Folia 包，以及本分支新增 fork 信任根后的 MahjongPaper 核心。旧核心不认识 fork 签名，不能只替换日麻 JAR。

## 为什么不用已发布的 2.0.1

官方已发布的日麻 2.0.1 在 `NativeScoreCalculator` 中使用 monitor synchronization，核心的规则安全策略会拒绝。上游后来已移除缓存 monitor；提交 `fa2268a0516c60e40d731085227587b7f68de037` 的成功 CI 提供 2.0.2 候选。本 fork 分发该 CI 的原始 JAR 和资源 ZIP，没有修改字节码，也没有关闭安全检查。它仍是测试候选，不是作者正式发布的稳定版本。

日麻候选要求独立资源包，必须与规则成对安装。本 fork 的 registry 使用 format 2，绑定两个文件的 SHA-256 和大小。registry 由 fork 公钥验证，原官方信任根仍保留；官方条目并非被伪装成新的官方签名。来源、许可和固定摘要见 [distribution/riichi](../distribution/riichi/README.md)。

## 安装与激活

先停服并备份自己的数据。在 fork [专用 Actions](https://github.com/ColdTearsYY/MahjongEngine/actions/workflows/lophine-26.3.yml) 中选择包含本次日麻更新的成功运行，下载 `MahjongPaper-lophine-26.3-<os>` artifact。只把无 `-sources` 后缀的核心 JAR 放入 `plugins/`，不要同时保留另一份旧核心。规则 JAR 不是 Bukkit 插件，不能放进顶层 `plugins/`。

修改现有 `plugins/MahjongPaper/config.yml` 的 `rules.registry-url`，保留其余配置：

```yaml
rules:
  registry-url: "https://raw.githubusercontent.com/ColdTearsYY/MahjongEngine/refs/heads/fix/lophine-26.3-compat/distribution/riichi/registry.json"
```

这是 fork registry，不是官方 registry。启动服务器，确认 CraftEngine 与 MahjongPaper 均正常启用。服务器控制台依次执行，并等待每条异步命令返回：

```text
mahjong rules install riichi 2.0.2
mahjong rules verify riichi
mahjong rules activate riichi 2.0.2
stop
```

`activate` 写入待激活版本，必须完整停止并重启 JVM，不使用 `/reload`。重新启动后检查：

```text
mahjong rules list
mahjong rules verify riichi
```

应看到 `riichi:2.0.2[active]` 和 `riichi:2.0.2=valid`。安装器同时下载规则 JAR 和资源 ZIP，核对签名、摘要、大小和资源归属后再发布安装目录；不要手工改 registry-cache 来绕过验证。

游戏内加入或创建等待中的牌桌后，用以下命令切换日麻雀魂预设；也可以在牌桌配置界面选择对应规则：

```text
/mahjong mode riichi mahjong-soul
```

这一步设置牌桌规则，不能替代上面的规则安装与激活。按命令的权限和房主要求操作，使用 Tab 查看已加载的可选预设。玩家需接受 CraftEngine 生成/发送的资源包。已有国标牌桌不会因安装日麻自动改规则。

## 本地构建

在原兼容说明的 [构建命令](lophine-26.3-compatibility.zh-CN.md#获取和构建) 基础上，再设置：

```powershell
$env:MAHJONG_FORK_RULE_PACK_PUBLIC_KEY_BASE64 = 'MCowBQYDK2VwAyEAwQzKwTZ1wK3f/hkA2mH9cWgDSjaPpzSEYQHvQu7w9mU='
$env:RIICHI_RULE_PACK_ARTIFACT = Join-Path (Get-Location) 'distribution\riichi\2.0.2\riichi-rule-pack-2.0.2.jar'
$env:RIICHI_RESOURCE_PACK_ARTIFACT = Join-Path (Get-Location) 'distribution\riichi\2.0.2\riichi-resource-pack-2.0.2.zip'
```

仍需原官方公钥和 MCR 集成测试输入。CI 已固定以上两个公开公钥和候选摘要。签名私钥不参与核心构建，不能写入仓库或交付包。

## 验证记录与边界

2026-10-12 本地完整构建执行 64 个任务，JUnit 304 项，0 failures、0 errors、2 skipped；跳过的两项需要外部 MySQL/MariaDB。日麻两项测试实际执行：成对安装及幂等安装、四人确定性初始化、100 次合法/定时动作推进、公共/私有视图及每步快照恢复；篡改资源会在正式安装前被拒绝。多信任根的六项测试覆盖未知签名者、改动的 payload、坏签名、缺少官方根、错误公钥、私钥和非 Ed25519 输入。

真实隔离服务器使用 Lophine `26.3-843-dev/26.3@f06a4d0`、Java 25.0.4、官方 CE 26.9.2 Paper/Folia，新端口 `25851` 和新世界。通过 fork HTTPS registry 完成规则/资源成对安装，verify 返回 `riichi:2.0.2=valid`；完整停服重启后显示 `riichi:2.0.2[active]`，再次 verify 为 valid。CE 启动流程加载日麻模块、生成并上传资源包，最终 ZIP CRC 校验通过，11 个日麻资源文件与上游 ZIP 逐字节一致。安装与重启轮次均 ready、未超时、exit 0，结束后端口无监听。

Windows/Linux [CI 运行 38251069782](https://github.com/ColdTearsYY/MahjongEngine/actions/runs/38251069782) 均通过，两个日麻测试各自实际执行、未跳过。本地、Windows 和 Linux 核心 JAR 摘要一致：`5a94fe222f53beb690ec20d8522828ed5d0bb1247d49ab29ac3a63b56eb146bf`。这是新增 fork 信任根后的核心，不是原兼容说明中的历史 MCR 测试 JAR。

额外尝试的 `mahjong bundle install` 不是支持的命令，没有计入通过项；资源生成验证依据正常的激活后启动流程和生成文件核验。本地规则及服务端检查不等同于客户端视觉或真人完整对局验收。

本轮不修改生产服或旧测试实例。椅子模型在 26.1+ 上的隐式 UV 告警仍保留，未重做模型；客户端表现、真人多人完整对局和生产部署尚未验证。原作者署名与许可保留，不创建上游 PR 或正式 Release。
