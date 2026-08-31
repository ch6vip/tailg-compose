# CI 审查报告 — `.github/workflows/build.yml`

审查日期：2026-09-01
审查对象：`.github/workflows/build.yml`（+ `app/build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`）

## 处置状态（2026-09-01 更新）

| 优先级 | 项 | 状态 |
| --- | --- | --- |
| P0 | 签名密钥/明文密码泄露 + release APK 外泄 | 🟡 **代码侧已完成，历史清理待你执行**。已完成：4 个 Secret 已上传、`build.gradle.kts` 去掉明文兜底并可在无密钥时降级为未签名、`build.yml` 从 Secret 还原 keystore 且 PR 拿不到密钥、`.gitignore` 屏蔽 keystore、工作区与 git 索引已移除 `app/release.keystore`、已开启 secret scanning + push protection。**待执行**：`git filter-repo` 清历史 + force push（见第 8 节） |
| P1 | `actions: write` + 自删 artifact | ✅ 已删除 Prune 步骤，权限降为 `contents: read` |
| P1 | `cache-read-only` 全局只读 | ✅ 改为仅 master 可写 |
| P1 | 两次 `./gradlew` + 无 `--continue` | ✅ 合并为单次调用 + `--continue` |
| P2 | `compression-level: 9` | ✅ 移除，用默认（6） |
| P2 | `chmod +x gradlew` 补丁 | ✅ `git update-index --chmod=+x gradlew`（索引已改，待提交）；step 保留作保险 |
| P2 | 缺 wrapper 校验 | ✅ 加 `gradle/actions/wrapper-validation@v4` |
| P2 | checkout 凭据持久化 | ✅ 加 `persist-credentials: false` |
| P2 | `${{ }}` 插值进 `run:` | ✅ 随 Prune 步骤一并删除 |
| P2 | 测试报告只在 artifact | ✅ 补传 `app/build/test-results/` |
| P2 | `if: success()` 冗余 / `continue-on-error` 过宽 | ✅ 全部移除，仅保留在 reports 步 |
| P2 | compose_compiler 未上传 | ✅ 补传 `app/build/compose_compiler/` |
| P2 | `paths-ignore` | ⚠️ 只加在 `push` 上——PR 上加会让 required check 永远 pending（详见 workflow 内注释） |
| P2 | `timeout-minutes` 60→90 | ✅ |
| P2 | lint baseline | ❌ 未做（生成 baseline 需本机跑一次 `lintDebug`，要 Android SDK） |
| P2 | push + PR 双跑 | ❌ 未做（`push` 已限定 master/develop，双跑极罕见；属团队流程决策） |
| P2 | actions pin 到 SHA | ❌ 未做（需配 Dependabot 维护，成本 > 收益） |
| P2 | `dependency-graph: generate-and-submit` | ❌ 未做（需要 `contents: write` 权限，与最小权限目标冲突） |

## 0. 核查到的事实（结论依据）

| 项 | 实测值 |
| --- | --- |
| 仓库可见性 | **PUBLIC**（`gh repo view` 确认：`ch6vip/tailg-compose`） |
| `app/release.keystore` | **已被 git 跟踪**（2026-08 的签名提交引入，历史已于 2026-09-01 清理，见第 8 节） |
| 签名密码 | 硬编码兜底值 `Q********.` / alias `tailg-r******e`（`app/build.gradle.kts:30-32`） |
| workflow 里是否注入 Secrets | **没有** `env:` 块 → 走的是兜底明文密码分支 |
| AGP / Kotlin / Gradle | 8.9.1 / 2.1.10 / 8.12-bin |
| 单测规模 | 35 个测试文件 |
| `gradle.properties` | `configuration-cache=true`、`caching=true`、`parallel=true`、`jvmargs=-Xmx4096m` |
| lint baseline | 无 `app/lint-baseline.xml`，且 `abortOnError=true` |
| `gradlew` 文件模式 | `100644`（无执行位）→ 所以才有 `chmod +x` 那一步 |

---

## 1. P0 — 签名密钥与明文密码已泄露（不在 workflow 本身，但它让后果扩大）

**问题**：`app/release.keystore` 在公开仓库里，密码以源码形式写在 `app/build.gradle.kts:30-32`。任何人 clone 后即可用你的签名打出任意 APK——已安装你 App 的用户会被恶意更新静默覆盖（Android 只校验签名一致性）。

**与 workflow 的耦合**：`build.yml:62` 的注释写着 "Catch R8/ProGuard keep-rule regressions **without requiring a release keystore**" —— 这条注释**已经与事实不符**。自从 2026-08 的签名提交把 keystore 签进仓库后，`assembleRelease` 是真签名，而且 `build.yml:76-84` 每次 push **和每个 PR** 都把**已签名的 release APK** 作为 artifact 上传。公开仓库的 artifact 对任何有读权限的人可下载。

**处置顺序（缺一不可）**：
1. **轮换密钥**，旧密钥视为已泄露（Play/第三方渠道若已上传过，走签名密钥升级流程）。
2. Secrets 存 `ANDROID_KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，CI 里解码到 `$RUNNER_TEMP`，**不要**落回仓库路径。
3. `build.gradle.kts` 去掉明文兜底：缺 env 就直接 `error(...)` 或跳过签名，**绝不能静默用默认密码**。
4. 清理 git 历史（`git filter-repo --path app/release.keystore --invert-paths`）+ force push；同时确认 GitHub 上没有 fork 保留该对象。
5. workflow 里：**PR 不构建 release、不上传 release APK**。

## 2. P1 — `actions: write` 权限 + 自删 artifact（`build.yml:22-24, 107-119`）

为了省配额，整个 job 的 `GITHUB_TOKEN` 被提升到 `actions: write`，只为跑一个"保留最新 3 个 artifact"的删除脚本。

- 配额焦虑没必要：`upload-artifact` 的 `retention-days` 已经在自动过期；GitHub 自身也会对超限缓存做 LRU 回收。
- 该脚本是**跨 workflow** 删除的（按 `created_at` 全局排序，只留最新 3 个），一旦以后加第二个 workflow 就会互相误删。
- 少一个写权限，供应链风险就少一档。

**建议**：删掉 Prune 步骤，`permissions` 降回 `contents: read`。

## 3. P1 — `cache-read-only: true` 全局生效，缓存再也不会被写入（`build.yml:49`）

这是本文件里**最大的性能隐患**，而且注释里的推理有一个关键盲点：

GitHub Actions 的缓存作用域规则是「分支可以读本分支 + 默认分支的缓存」。现在 `master` 也不写，后果是：

- 缓存**永远不会新增条目**。一旦依赖升级导致 cache key 变化 → **永久冷启动**，且永远无法自愈。
- 已有缓存 7 天未被访问会被回收；只读访问是否刷新 LRU 也没有保证。
- 注释里担心的"500MB 配额会被撑爆"已经不成立：GitHub 现在对超限缓存做 LRU 自动回收。
- `org.gradle.caching=true`（`gradle.properties:2`）的本地构建缓存放在 `~/.gradle/caches/build-cache-1`，也在只读范围内 → **跨 run 的 build cache 命中同样失效**。

**建议**：只在默认分支写，其余只读：

```yaml
cache-read-only: ${{ github.event_name == 'pull_request' || github.ref != 'refs/heads/master' }}
```

`gradle-home-cache-cleanup`（默认开启）会在打包前清理 gradle home，已经能控制体积。

## 4. P1 — 两次 `./gradlew` 调用 + 没有 `--continue`（`build.yml:59-64`）

- 两次调用 = 两次 daemon 启动/挂载 + 两次配置阶段。合并成一次能省 1-2 分钟。
- 没有 `--continue`：debug 阶段一红，release 的 R8/签名问题要等下一轮才暴露。

## 5. P2 — 其他可改项

| # | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| 6 | `build.yml:74, 84, 93` | `compression-level: 9` | APK 内部 dex/resources 多为已压缩，9 级相对默认 6 级收益极小、CPU 时间明显变长。用默认即可 |
| 7 | `build.yml:37-38` | `chmod +x gradlew` 是补丁不是修复 | 本地 `git update-index --chmod=+x gradlew` 提交一次，之后这步可删（留着当保险也无害） |
| 8 | `build.yml:26` | 缺 Gradle Wrapper 校验 | 加 `gradle/actions/wrapper-validation@v4`，防 wrapper jar 被投毒，零成本 |
| 9 | `build.yml:26` | `checkout` 未关凭据持久化 | 加 `persist-credentials: false`，避免 token 落到 `.git/config` 被后续脚本读取 |
| 10 | `build.yml:114` | `${{ github.repository }}` 直接插值进 `run:` | 该变量可信，不算注入漏洞，但属于 actionlint/zizmor 告警项；改成 `env:` 传参更规范（删掉 Prune 后自动消失） |
| 11 | `build.yml:88-100` | 测试报告只在 artifact 里 | 额外上传 `app/build/test-results/**/*.xml`，或接 `dorny/test-reporter`，PR 里直接看到红掉的 case |
| 12 | `build.yml:66-84` | `if: success()` 冗余 + `continue-on-error` 全覆盖 | `if: success()` 是默认值可删；`continue-on-error` 建议只保留在 reports 步，release APK 上传失败不该静默 |
| 13 | `app/build.gradle.kts:107-110` | 生成了 `build/compose_compiler` 但没上传 | 报告 artifact 里补上 `app/build/compose_compiler/`，否则白跑 |
| 14 | `app/build.gradle.kts:95-100` | `abortOnError=true` 且无 baseline | 建议 `baseline = file("lint-baseline.xml")` 锁定存量告警，只让**新增**问题失败，避免长期噪音 |
| 15 | `build.yml:5-9` | 无 `paths-ignore` | 只改 `docs/**` 或 `*.md` 也要跑 30+ 分钟全量构建，加上 `paths-ignore: ['**.md', 'docs/**']` |
| 16 | `build.yml:6-8` | 同分支 push + 开 PR 会双跑 | 视团队习惯：可让 `push` 只覆盖 `master`/`develop`，PR 场景交给 `pull_request` |
| 17 | `build.yml:21` | `timeout-minutes: 60` | 冷缓存下 debug+lint+35 个测试文件+R8 release，4 核 runner 上 30-50 分钟很常见，余量偏小。建议 90，或拆分 job |
| 18 | `build.yml:26-41` | actions 未 pin 到 commit SHA | 硬化可选：`@v5` tag 可被上游移动；pin SHA + Dependabot 管理 |
| 19 | `build.yml:40-52` | 未启用依赖图 | `dependency-graph: generate-and-submit` 可让 GitHub 在 PR 里报漏洞依赖（注意需要 `contents: write`） |

### 做得对的地方（别改）

- `setup-java` **没有**开 `cache: gradle` —— 避免和 `gradle/actions/setup-gradle` 重复缓存。
- 不缓存 Android SDK —— runner 镜像自带，缓存它纯属烧配额。
- `'on':` 加引号规避 YAML 1.1 布尔歧义、`concurrency` 按 `event_name` 分组、`always()` 上传报告 —— 都是对的。

---

## 6. P1 + P2 已落地到 `.github/workflows/build.yml`

workflow 已按本报告改写（每处改动在文件内都有注释说明动机），此处不再重复贴全文——避免产生第二份副本后与真实文件漂移。

未落地的 4 项（lint baseline / push+PR 双跑 / actions pin SHA / dependency-graph）及其取舍见上方「处置状态」表。

---

## 7. P0 已完成的改动

### 7.1 密钥搬运

- 备份到仓库外：`C:\Users\ch6vip\.android\tailg-legacy-release.keystore`（确认 CI 正常后可删）
- 4 个 Secret 已写入仓库：`ANDROID_KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
- 工作区 + git 索引已移除 `app/release.keystore`；`.gitignore` 新增 `*.keystore / *.jks / *.p12 / *.pfx`

### 7.2 `app/build.gradle.kts`

签名配置改为**全 env 驱动 + 无兜底 + 可降级**：

- 只有当 `STORE_FILE` 指向的文件**真实存在**且三个密码非空时，才注册 `release` signingConfig
- 缺密钥时打印一行提示，**产出的 release APK 是未签名的**，构建不失败（PR 上仍然验证 R8/ProGuard）
- 删掉了硬编码的 `Q********.` / `tailg-r******e`

### 7.3 `.github/workflows/build.yml`

- 新增 "Restore release keystore"：`base64 -d` 到 `$RUNNER_TEMP`（工作区之外，不会进 artifact 或缓存），`if: github.event_name != 'pull_request'`
- 构建步骤注入 `STORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，PR 上全为空字符串

> 注意：同仓库（非 fork）的 PR 是**能读到 Secrets** 的，所以不能只靠"不给 PR 密钥"，必须在 Gradle 侧用「文件是否存在」兜底——这也是 7.2 里 `exists()` 检查存在的原因。

### 7.4 已开启仓库级防护

secret scanning + push protection 均已 enabled（此前是关闭的，所以泄露从未被 GitHub 告警）。

---

## 8. git 历史清理（已于 2026-09-01 执行完毕）

`app/release.keystore` 曾存在于 2026-08 的签名提交及之后所有提交的树中，且
`app/build.gradle.kts` 在两个提交里带有明文密码与 alias。**只删文件是不够的**：
删文件不会让历史里的 blob 消失，而明文密码在另一个文件里。所以做了历史重写。

### 8.1 实际执行的命令

```bash
# 1) 备份：必须放在仓库外，filter-repo 会重写仓库内所有 ref，
#    留在仓库里的"备份分支"同样会被重写，等于没备份。
git clone --mirror . C:/Users/ch6vip/tailg-compose-backup-20260901.git

# 2) 在全新克隆上重写。本地 clone 默认走硬链接，必须 --no-local，
#    否则 filter-repo 判定 "does not look like a fresh clone" 并拒绝执行。
git clone --no-local C:/Users/ch6vip/tailg-compose-backup-20260901.git tailg-filter
cd tailg-filter

# 3) 一次做两件事：删除 keystore 路径 + 替换明文密码/alias。
#    replace.txt 每行格式为 <原文>==><替换>：
#      Q********.==>***REMOVED***
#      tailg-r******e==>***REMOVED***
git filter-repo --invert-paths --path app/release.keystore \
  --replace-text replace.txt

# 4) 校验（三条都必须为空 / 一致）
git rev-list --objects --all | grep -i release.keystore          # 空
git grep -I -E "<密码>|<alias>" $(git rev-list --all)     # 空
git rev-parse HEAD^{tree}                                          # 应与重写前一致

# 5) filter-repo 会移除 origin，推回前需重新添加
git remote add origin https://github.com/ch6vip/tailg-compose.git
git push --force --all
```

### 8.2 结果

| 项 | 结果 |
| --- | --- |
| 提交数 | 111 → **111**（无丢失） |
| 根树对象 | `6e272e94…`，与重写前**逐字节一致**（内容零改动） |
| `app/release.keystore` | 全历史已移除 |
| 明文密码 / alias | 全历史已替换为 `***REMOVED***` |
| `git fsck` | 干净 |

### 8.3 执行时踩到的两个坑

1. **不要在工作副本上直接跑 filter-repo。** 首次在 `E:\...\tailg-compose`
   上直接跑，先报 `error: unable to update .git/info/refs`，随后 `gc` 阶段把
   `.git/objects` **清空**，HEAD 变成 `bad object`，整个仓库不可用 —— 最后是从
   `--mirror` 备份恢复的（工作区文件未受影响）。在**全新克隆**上跑完全没有这个问题。
2. **本地 clone 必须加 `--no-local`**，否则走硬链接，filter-repo 拒绝执行。

### 8.4 仍需注意

1. **所有提交的 SHA 都已改变**（111 个全部重写）。若有人 clone 过，必须重新
   clone（`git pull` 会冲突）。
2. **GitHub 仍可能通过旧 SHA 访问重写前的对象**（缓存 + PR 引用）。force push
   后可联系 GitHub Support 请求清除缓存视图。
3. 本仓库 0 fork，无需通知下游。
4. 备份保留在 `C:\Users\ch6vip\tailg-compose-backup-20260901.git`，
   确认无误后可删除。

### 8.5 关于是否轮换密钥（本次未做）

本次按决定保留原密钥。需要说清楚：**历史清理只是缩小传播面，不能让已公开的
密钥重新变私密。** 本仓库 0 tags / 0 releases / versionCode 1，没有任何存量
安装，换密钥的边际成本接近零。

若日后决定轮换（本机已有 JDK）：

```bash
keytool -genkeypair -v \
  -keystore "C:/Users/ch6vip/.android/tailg-release.p12" \
  -alias tailg-release -keyalg RSA -keysize 2048 -validity 10950 \
  -storetype PKCS12 -storepass "<新密码>" -keypass "<新密码>" \
  -dname "CN=Tailg Plus, OU=Mobile, O=Tailg, L=Shenzhen, ST=Guangdong, C=CN"

base64 -w0 "C:/Users/ch6vip/.android/tailg-release.p12" > /tmp/ks.b64
gh secret set ANDROID_KEYSTORE_BASE64 < /tmp/ks.b64
gh secret set KEYSTORE_PASSWORD --body "<新密码>"
gh secret set KEY_ALIAS       --body "tailg-release"
gh secret set KEY_PASSWORD    --body "<新密码>"
rm -f /tmp/ks.b64
```

---

## 9. 顺带的个人安全提醒

`Q********.` 这个密码已经随公开仓库暴露，而且从命名规律看像是个人通用密码。建议检查并更换所有复用过它的账号（邮箱、服务器、社交账号等）。`tailg-r******e` 这个 alias 同样泄露了用户名。
