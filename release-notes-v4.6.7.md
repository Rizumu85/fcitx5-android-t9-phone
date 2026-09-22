## 修复与优化

- 重整拼音输入与筛选状态：继续输入、退格和分段提交时保留仍有效的拼音选择，避免候选、预览与已选拼音不对应。
- 修复注音筛选后继续输入时，已选读音被重置的问题；部分提交后，剩余读音选择也会保留。
- 修复筛选候选时只读取前 80 项，导致部分有效候选被遗漏或候选列表为空的问题。改为分批读取，并在输入变化时取消旧请求。
- 修复收起输入面板后，实体返回键仍被当作退格键的问题。面板显示时保留原有退格行为，收起后恢复应用的正常返回。
- 修复密码模式受到中文候选与 Rime 准备状态干扰的问题。
- 联想英文的下一词预测不再被标点或回车自动接受；真正输入中的英文候选仍会在标点或回车前提交，不额外加空格。

## 安装包

本次 Release 提供输入法本体和 Rime 插件，并分别提供 32 位与 64 位版本：

- 32 位手机下载 `armeabi-v7a` 版本。
- 64 位手机下载 `arm64-v8a` 版本。

同一架构的输入法本体和 Rime 插件两个 APK 都需要更新。例如 64 位手机需要安装：

- `org.fcitx.fcitx5.android-4.6.7-arm64-v8a-release.apk`
- `org.fcitx.fcitx5.android.plugin.rime-4.6.7-arm64-v8a-release.apk`

32 位手机对应安装：

- `org.fcitx.fcitx5.android-4.6.7-armeabi-v7a-release.apk`
- `org.fcitx.fcitx5.android.plugin.rime-4.6.7-armeabi-v7a-release.apk`

覆盖安装会保留设置和用户词库。

## Rime 版本对应关系

- Rime 插件：`4.6.7`，请与输入法本体一起更新。
- 九键 Rime 配置：[rime-ice-t9-phone v3.2.2](https://github.com/Rizumu85/rime-ice-t9-phone/releases/tag/v3.2.2)。

本次没有修改九键 Rime 配置。已经部署 v3.2.2 的用户无需重新下载或重新部署；首次安装时输入法仍会自动下载、校验并部署该版本。
