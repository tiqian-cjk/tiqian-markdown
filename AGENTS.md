# AGENTS.md

tiqian-markdown 是提椠（Tiqian）套件的 Markdown 渲染层：消费 `org.tiqian:tiqian-compose`
与 math 套件的已发布 Maven 制品，把宿主适配的 Markdown 文档渲染为 Compose 界面。
套件级的事实来源与完整约束见主仓库 [tiqian/AGENTS.md](../tiqian/AGENTS.md)；
排版决策（断行、标点、行长网格等）必须发生在 tiqian 引擎侧，本仓库只做块级组织、
延迟渲染与预排调度，不得在渲染层复制布局真值。

## Build 与验证

```shell
./gradlew jvmTest compileAndroidMain
```

所有 Android Gradle 任务需要 `ANDROID_HOME`。上游依赖版本由根目录
`.tiqian-local.properties`（`scripts/enable-local-suite.sh` 生成）或
`-PtiqianDependencyVersion` 决定；本仓库自身发布用
`./gradlew publishToMavenLocal -PtiqianVersion=<ver>-SNAPSHOT`。

## 代码组织

与套件约定一致（约定而非 lint 强制，不要引入 ktlint 之类的工具）：

- 单个源文件尽量保持在 1000 行以下；新代码按功能簇分文件，超标文件按
  主仓库文档记录的机械等价手段拆分，并以 `jvmTest` 全绿作为行为不变证据。
- 主入口（`TiqianMarkdown.kt`、`TiqianMarkdownSurface.kt`）只做入口与接线，
  实现放在按功能簇命名的文件里（`MarkdownDeferredLayout.kt`、`MarkdownPrelayout.kt`、
  `MarkdownBlockRendering.kt` 等）。
- 每个 heuristic 必须命名并注释其目的（如 `ScrollAheadPrelayout`、
  `AnchoredHeightCorrection`、`TableFluidFill`），与 tiqian 的可解释性原则一致。

## 工作区与提交

工作区可能同时存在其他任务的改动。不要还原、格式化或提交无关文件；同一文件已有
并行改动时，先理解并在其上继续。提交沿用 `type(scope): subject` 单行标题，不加 body
与 trailer。
