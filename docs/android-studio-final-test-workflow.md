# Android Studio 最终测试和分发流程

这份清单用于在 Android Studio 里本地构建、测试和准备分发包。输入法本体和 Rime 插件按两个独立 APK 分发。

## 1. 同步和构建

1. 用 Android Studio 打开项目。
2. 运行 **File > Sync Project with Gradle Files**。
3. 构建输入法本体 APK：
   - Debug 冒烟测试：Gradle task `:app:assembleDebug`
   - Release 分发包：Gradle task `:app:assembleRelease`
4. 构建 Rime 插件 APK：
   - Debug 冒烟测试：Gradle task `:plugin:rime:assembleDebug`
   - Release 分发包：Gradle task `:plugin:rime:assembleRelease`

输出路径：

- 输入法本体 APK：`app/build/outputs/apk/<debug|release>/`
- Rime 插件 APK：`plugin/rime/build/outputs/apk/<debug|release>/`

如果生成的 release APK 没有签名，正式分发前请在 Android Studio 里使用 **Build > Generate Signed App Bundle / APK** 生成签名包。

## 2. 安装顺序

1. 安装输入法本体 APK。
2. 安装 Rime 插件 APK。
3. 在 Android 系统设置里启用输入法。
4. 在输入法设置里：
   - 确认 Rime 插件已被检测到。
   - 启用中州韵 / Rime 附加组件。
   - 添加 Rime 输入法。
5. 打开任意文本输入框，进入键盘菜单，重新部署 Rime。
6. 把 T9 Rime 数据包复制到：
   `Android/data/org.fcitx.fcitx5.android/files/data/rime`
7. 再次打开键盘菜单，同步 Rime。
8. 选择中文九键方案。

## 3. 最终 T9 冒烟测试

分发前请完整跑一遍下面的测试。

### 中文 T9

- 输入 `24`，拼音行应该出现 `ai`。
- 输入 `2496` 后选择 `ai`，顶部读音应继续显示未解析后缀，例如 `ai wo`。
- 选择汉字候选后，应正常提交候选，不应把原始数字串漏到输入框。
- 选择拼音 chip 后按删除，最后一个已选拼音段应回退成原始数字。
- 分别测试屏幕删除键和物理 Back/删除键，选择后删除都不应闪退。
- 把候选预算调低后，中文词、emoji、常用英文词都应按 T9 候选预算显示。

### 英文 T9

- 长按 `#` 切到 `En`。
- 连续按 `2`，应在 `a`、`b`、`c` 之间循环。
- 输入字母前短按 `*`，下一个字母应只大写一次。
- 长按 `*`，应进入大写锁定；再次关闭前字母都应保持大写。
- 按 `0` 时，如果有未确认的 multi-tap 字母，应先提交字母再输入空格。

### 数字模式

- 长按 `#` 切到 `123`。
- 按 `0-9`，每个按键都应直接输入对应数字。
- 按 `*`，应直接输入普通 `*`。
- 长按数字键或 `*`，不应打开 Rime 候选，也不应重复输入异常文本。

### 中文/数字模式的 `*` 行为

- 中文模式下，`*` 应直接输入普通 `*`，不应打开标点候选行。
- 中文组合状态下按 `*`，应替换/清掉当前组合并输入 `*`。
- 数字模式下，`*` 也应直接输入普通 `*`。

## 4. 分发包内容

建议把下面这些文件一起打包：

- 输入法本体 APK
- Rime 插件 APK
- `rime-ice-t9-phone` 的 Rime 数据包
- 简短安装说明：先安装本体，再安装插件，然后部署并同步 Rime 数据
