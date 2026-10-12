# Lophine 26.3 兼容验证说明

本 fork 的 `fix/lophine-26.3-compat` 分支基于上游 `chore/minecraft-26.3` 提交 `758a3914b98ebe26f4e6815b5061f3b6c1a6d23f`，适配 CraftEngine 26.9 API 线。插件版本仍为 `2.0.0-SNAPSHOT`，用于测试服验证。

## 运行环境

| 组件 | 本次实测版本 |
| --- | --- |
| 服务端 | Lophine `26.3-843-dev/26.3@f06a4d0`，API `26.3.build.843-beta` |
| Java | 25.0.4 |
| CraftEngine | 官方 26.9.2 **Paper/Folia** 包 |
| MahjongPaper | 本分支构建的 2.0.0-SNAPSHOT thin JAR |
| 规则 | 官方 MCR 2.0.1，SPI 1.6.0 |
| 数据库 | 默认 H2 |

测试使用独立端口和新世界，没有启动或修改已有服务器。CraftEngine 的 `misc.delay-configuration-load` 保持 `true`。运行时门禁仅接受 26.9 API 线；其他 CE 版本需要重新适配。CraftEngine 26.8.2 在此 Lophine 版本上发生 bootstrap/proxy 接口错误，因此不在本分支支持范围内。

## 改动

CraftEngine 编译和测试依赖升至 26.9。资源生成改为调用管理员配置的 `reload_pack` workflow，保留其校验、打包、上传和发送设置。启动时通过 CE 平台调度器等待初始资源加载完成，合并等待请求，最多等待约 60 秒，插件停用后退出等待。

官方规则 JAR 中的 `assets/<rule-id>/tile-visuals.properties` 仅在 manifest 声明且大小不超过 64 KiB 时允许加载，其空父目录也允许存在。纹理、模型、音频、其他资源路径及跨规则路径仍被拒绝。签名、SHA-256、SPI 和字节码策略保持启用。

规则类加载器读取字节码时关闭该 URL 连接的 JAR 缓存，让探测加载器关闭后 Windows 能原子移动已验证的 staging 目录。回归测试直接使用未修改的官方 MCR JAR，验证加载、关闭、移动及重新加载。

## 本地验证结果

2026-10-12 完整执行 `build --rerun-tasks` 成功，64 个任务全部执行。JUnit 共 296 项，0 failures、0 errors、2 skipped；跳过项是需要外部 MySQL/MariaDB 的排行榜查询计划测试。官方包加载和 Windows 原子移动两项集成测试实际执行通过，metadata 接受与拒绝边界测试通过。构建包含 thin JAR 和资源模型一致性检查。

| 真实服务端验证 | 结果 |
| --- | --- |
| CE 26.9.2 与 MahjongPaper 启用 | 通过 |
| 官方 MCR 2.0.1 下载及安装 | 通过；安装后的摘要与官方固定摘要一致 |
| 规则 verify / activate | 通过；`mcr:2.0.1=valid`，激活请求写入 pending |
| 完整停服、重启及激活持久化 | 通过；重启后为 `mcr:2.0.1[active]`，再次校验 valid，正常停服退出 |
| bundle 重新安装及 CE reload workflow | 通过；重新安装后触发 reload，生成 ZIP 含 753 个条目，其中 Mahjong 资源 206 项，ZIP 完整性检查通过，无 MahjongPaper ERROR |

首次安装复现了 Windows 文件锁，修复后回归测试通过。另一次安装因直连 GitHub 超时失败，隔离测试 JVM 设置本机代理后成功；代理不是插件默认配置。官方 registry 中该 MCR 条目没有独立资源 ZIP，本次安装结果不包含规则资源 ZIP 验证。

本次本地运行的核心 JAR SHA-256：

```text
A54A481ADEF93A5F8734D43C8E8BF4240713CBBB96DD6EDF8477BC21608FC714
```

该摘要标识本次本地测试制品。其他平台或后续提交的构建应重新计算摘要。

## 获取和构建

在本 fork 的 [Actions](https://github.com/ColdTearsYY/MahjongEngine/actions/workflows/lophine-26.3.yml) 中选择本分支对应的成功运行，下载 `MahjongPaper-lophine-26.3-<os>` artifact。工作流分别在 Windows 和 Linux 构建，核对官方 MCR 摘要后运行完整 `build`，并上传测试报告。安装时使用无分类器的 `mahjong-plugin-2.0.0-SNAPSHOT.jar`，不要把 `-sources.jar` 放入 `plugins/`。远程 CI 的实际状态以 Actions 页面为准。

手工构建需要 Java 25 和 Java 21：Java 25 用于核心及 Gradle，Java 21 用于规则 SPI 模块的兼容编译。以下命令使用 PowerShell 7，在源码根目录执行；把两个 JDK 路径替换为本机路径。

```powershell
$env:JAVA_HOME = 'E:\jdk25'
$java21 = 'E:\jdk21'
$env:MAHJONG_RULE_PACK_PUBLIC_KEY_BASE64 = 'MCowBQYDK2VwAyEABKsqAQxbRheenJb5AsClim4f1uuCCrdByfaM5AOxlro='
$taskTemp = Join-Path (Get-Location) 'Temp\lophine-build'
New-Item -ItemType Directory -Force $taskTemp | Out-Null
$env:TEMP = $taskTemp
$env:TMP = $taskTemp
$env:GRADLE_USER_HOME = Join-Path $taskTemp 'gradle-home'
$env:RULE_PACK_ARTIFACT = Join-Path $taskTemp 'mcr-2.0.1.jar'
Invoke-WebRequest -Uri 'https://github.com/EllanServer/mahjong-mcr-java/releases/download/v2.0.1/mcr-rule-pack-2.0.1.jar' -OutFile $env:RULE_PACK_ARTIFACT
if ((Get-FileHash $env:RULE_PACK_ARTIFACT -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'e07b7e3db3643826fd591fd23a5c985e17b30151efda29b0f41d24085ef20cd1') {
    throw 'Official MCR artifact checksum mismatch'
}
& "$env:JAVA_HOME\bin\java.exe" -version
& .\gradlew.bat build --no-daemon --stacktrace "-Porg.gradle.java.installations.paths=$env:JAVA_HOME,$java21" '-Porg.gradle.java.installations.auto-download=false'
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
```

显式传入 `org.gradle.java.installations.paths` 可覆盖仓库 `gradle.properties` 中的本机路径。Linux 使用 `./gradlew`，环境变量及路径按系统设置。构建产物位于 `modules/mahjong-plugin/build/libs/`。首次构建和首次开服需能访问依赖仓库；下载失败时检查网络并按环境配置代理。

公钥是 Ed25519 X.509 DER 的 Base64，来自[上游构建运行 31343358890](https://github.com/EllanStudio/MahjongEngine/actions/runs/31343358890)中核对过的官方信任根。本次使用该公钥验证了官方 registry 签名，并验证修改后的 registry 会被拒绝。它是公开验证公钥，签名私钥不参与本 fork 构建。

## 安装规则与重启

安装官方 CraftEngine 26.9.2 Paper/Folia 包及本分支核心 JAR，首次启动后确认两者正常启用。检查 `plugins/MahjongPaper/config.yml` 中 `rules.registry-url` 指向官方签名 registry：

```text
https://github.com/EllanServer/MahjongEngine/releases/download/rule-registry-v2026.08.10.1/registry.json
```

在服务器控制台依次执行：

```text
mahjong rules install mcr 2.0.1
mahjong rules verify mcr
mahjong rules activate mcr 2.0.1
stop
```

每条异步命令都要等待结果。重新启动后执行 `mahjong rules list` 和 `mahjong rules verify mcr`，确认规则为 active 且 verified。游戏内命令需加 `/` 并具备管理权限。`verify` 接受规则 ID，不接受版本参数；`activate` 的版本选择在完整 JVM 重启后生效。

## 仍需验证的范围

官方 riichi 2.0.0/2.0.1 包的 `NativeScoreCalculator` 使用 monitor synchronization，当前字节码策略会拒绝；MCR 2.0.0 使用旧 SPI 1.5.0，也会被拒绝。本分支没有放宽这些策略。四川 2.0.1 曾通过独立加载实验，尚未完成本轮真实服务器安装及对局验证。

CraftEngine 对上游椅子模型的隐式 UV 发出警告，提示 26.1+ 客户端可能无法正确渲染；椅子 29 个元素与 Blockbench 源一致，本次未重做模型贴图。尚未进行客户端资源包视觉检查、真人多人完整对局、生产部署或外部 MySQL/MariaDB 验证。服务端启动和规则加载结果不能替代这些检查。

原作者署名、许可证和第三方通知保留。本 fork 的兼容更新与上游稳定 Release 分开维护。
