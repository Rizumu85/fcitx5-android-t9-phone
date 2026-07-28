## 修复与优化

- 修复选择拼音筛选项后，焦点画面仍停在拼音行、方向键却已经操作汉字候选的问题。
- 即时焦点移动现在和候选 UI 的差分刷新共用同一份状态，避免视觉焦点与实际按键行为不同步。

## 安装包

本次 Release 提供输入法本体和 Rime 插件，并分别提供 32 位与 64 位版本：

- 32 位手机下载 `armeabi-v7a` 版本。
- 64 位手机下载 `arm64-v8a` 版本。

同一架构的输入法本体和 Rime 插件两个 APK 都需要更新。例如 64 位手机需要安装：

- `org.fcitx.fcitx5.android-4.6.6-arm64-v8a-release.apk`
- `org.fcitx.fcitx5.android.plugin.rime-4.6.6-arm64-v8a-release.apk`

覆盖安装会保留设置和用户词库。

## Rime 版本对应关系

- Rime 插件：`4.6.6`，请与输入法本体一起更新。
- 九键 Rime 配置：[rime-ice-t9-phone v3.2.2](https://github.com/Rizumu85/rime-ice-t9-phone/releases/tag/v3.2.2)。

本次没有修改九键 Rime 配置。已经部署 v3.2.2 的用户无需重新下载或重新部署；首次安装时输入法仍会自动下载、校验并部署该版本。
