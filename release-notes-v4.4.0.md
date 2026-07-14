## 新功能

- 新增应用内更新：进入设置时每天最多自动检查一次，也可在【关于】中手动检查；输入法本体、Rime 插件和九键 Rime 配置会分别判断和更新。
- 中文和英文符号面板第一页末尾新增 `↵`。在微信、QQ、飞书、抖音、小红书等受支持的聊天输入框中会发送消息，在普通多行文本框中仍会换行。

## 体验与修复

- 恢复输入法未处于文字输入状态时的物理按键音。
- 改进 `↵` 的候选和预览显示：尺寸、位置、转角和线宽会适配输入法字号及自定义字体粗细。
- 修复符号翻页后 `↵` 位置不稳定、候选预览缺失或被裁剪的问题。
- 自动检查遇到 GitHub 网络不可用时保持安静；手动检查会给出明确提示。

## 安装包

请根据手机架构同时安装输入法本体和 Rime 插件：

- 64 位：`org.fcitx.fcitx5.android-4.4.0-arm64-v8a-release.apk` 与 `org.fcitx.fcitx5.android.plugin.rime-4.4.0-arm64-v8a-release.apk`
- 32 位：`org.fcitx.fcitx5.android-4.4.0-armeabi-v7a-release.apk` 与 `org.fcitx.fcitx5.android.plugin.rime-4.4.0-armeabi-v7a-release.apk`

## Rime 版本对应关系

- Rime 插件：`4.4.0`，请与输入法本体一起更新。
- 九键 Rime 配置：[rime-ice-t9-phone v3.0.0](https://github.com/Rizumu85/rime-ice-t9-phone/releases/tag/v3.0.0)。本次配置内容没有更新，已经使用 v3.0.0 的用户不需要重新导入或重新部署。

首次安装仍需下载 `rime-ice-t9-phone-main.zip` 并按 README 完成配置；以后可通过【关于】->【检查更新】统一管理三类更新。
