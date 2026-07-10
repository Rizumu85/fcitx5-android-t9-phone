# fcitx5-android-t9-phone

安卓企鹅输入法魔改成支持物理九键键盘的智能安卓机/老人机，对其他输入方式的支持可能有bug。

## 下载
- 在本项目的[Release](https://github.com/Rizumu85/fcitx5-android-t9-Phone/releases)下载安装包。根据Release的说明下载对应的版本
- 接着在另一个库的Release下载相关插件相关的文件。[rime-ice-t9-phone](https://github.com/Rizumu85/rime-ice-t9-phone/releases)

### 安装方法
- 在手机上安装下载下来的APK，输入法本体和插件。
- 输入法本体，按照App显示的要求，开启输入法。
- 在输入法设置里进行一下操作：
    - 【插件】查看相应的rime插件有无被检测到。
    - 【附加组件】查看中州韵有无被勾选，需被勾选。
    - 【输入法】点右下角的添加，添加中州韵。可以按右上角的编辑删掉English和拼音输入法。
- 进入一个能输入文本的地方打开输入法，点输入法上方的【⋯】打开快捷设置。
- 在快捷设置的下排找到常驻显示的【词库切换】。
- 点开【词库切换】，选择【重新部署】。【必做】这一步会创建后面要导入文件的 rime 数据目录；菜单会高亮当前正在使用的 Rime 方案。


- 把下载下来的压缩包，解压，并把里面的 所有文件和文件夹
- 导进安卓手机里的文件夹 Android/data/org.fcitx.fcitx5.android/files/data/rime（如果没有这个文件夹，通常是忘记先做一次【重新部署】）
![CleanShot 2026-03-18 at 23 45 05](https://github.com/user-attachments/assets/5763a980-c058-4f96-9ece-ff0492fd5059)


- 再次进入一个能输入文本的地方打开输入法，点【⋯】打开快捷设置，点开同一个【词库切换】，选择【同步】。
- 如果快捷设置里看不到词库切换/Rime 状态项，先回到输入法设置检查【插件】是否检测到 rime 插件、【附加组件】里中州韵是否勾选、【输入法】里是否已经添加中州韵。
- 等待同步完成。
- 点开后选择拼音九键模式
- 恭喜你可以用了！

## 九键键盘使用逻辑

### 中文模式（拼音九键 / 五笔画九键 / 注音九键）

可以在设置首页【中文输入方案】里勾选需要保留的方案。默认只启用拼音九键；启用多个方案后，长按 **\*键** 会按“拼音 -> 五笔画 -> 注音”的顺序快速切换。完整的 Rime 方案仍可从快捷设置【词库切换】进入。

| 按键 | 拼音九键 | 五笔画九键 | 注音九键 |
| :--- | :--- | :--- | :--- |
| **1键** | 输入中作为拼音分词符号 `'` | 横 `一` | ㄅㄆㄇㄈ |
| **2键** | ABC | 竖 `丨` | ㄉㄊㄋㄌ |
| **3键** | DEF | 撇 `丿` | ㄍㄎㄏ |
| **4键** | GHI | 点/捺 `丶` | ㄐㄑㄒ |
| **5键** | JKL | 折 `乛` | ㄓㄔㄕㄖ |
| **6键** | MNO | 未知笔画 `？` | ㄗㄘㄙ |
| **7键** | PQRS | 不输入笔画 | ㄚㄛㄜㄝ |
| **8键** | TUV | 不输入笔画 | ㄞㄟㄠㄡ |
| **9键** | WXYZ | 不输入笔画 | ㄢㄣㄤㄥㄦ |
| **0键** | 有候选时确认；空闲时空格 | 有候选时确认；空闲时空格 | ㄧㄨㄩ |

中文模式规则：
- 短按 * 打开符号输入， 再短按 * 切换中英符号；长按 * 切换中文的拼音->五笔画->注音模式。
- 无输入时 长按相关数字 输入数字，输入时 长按相关数字 输入候选汉字。

### 英文模式

普通英文九键使用 multi-tap 输入。需要用联想英文单词时，可以在输入法上方【⋯】快捷设置里切换到【联想英语】。

联想英语规则：

联想英语学习词库的使用方法：
- 先在快捷设置里关闭【联想英语】，回到普通英文九键。
- 用普通英文九键打出一个连续英文单词，再按空格、标点或回车结束这个词；例如打完 `rizum` 后按空格，它会被记录到本地学习词库。
- 再切回【联想英语】，输入这个词对应的 T9 数字序列，它就可以作为候选出现。
- 只会学习 2 个字母以上的纯英文字母词；密码输入框、声明不允许个性化学习的输入框不会记录。
- 如果误学了验证码、错词或不想保留的词，可以到设置首页【词库管理】->【联想英语学习词库】里添加、编辑或删除。

| 按键 | 短按 | 长按 |
| :--- | :--- | :--- |
| **1键** | 切换大小写 | 数字 1 / 快捷选择第 1 个候选 |
| **2键** | ABC | 数字 2 / 快捷选择第 2 个候选 |
| **3键** | DEF | 数字 3 / 快捷选择第 3 个候选 |
| **4键** | GHI | 数字 4 / 快捷选择第 4 个候选 |
| **5键** | JKL | 数字 5 / 快捷选择第 5 个候选 |
| **6键** | MNO | 数字 6 / 快捷选择第 6 个候选 |
| **7键** | PQRS | 数字 7 / 快捷选择第 7 个候选 |
| **8键** | TUV | 数字 8 / 快捷选择第 8 个候选 |
| **9键** | WXYZ | 数字 9 / 快捷选择第 9 个候选 |
| **\*键** | 常用标点符号 | `*` |
| **0键** | 空格 | 数字 0 / 快捷选择第 0 个候选 |
| **#键** | 提交当前英文后回车 / 搜索 | 切换模式（中文/英文/数字）|

### 数字模式
| 按键 | 短按内容 | 长按内容 |
| :--- | :--- | :--- |
| **1键** | **1** | `-` |
| **2键** | **2** | `+` |
| **3键** | **3** | `=` |
| **4键** | **4** | `π` |
| **5键** | **5** | `/` |
| **6键** | **6** | `≈` |
| **7键** | **7** | `(` |
| **8键** | **8** | `%` |
| **9键** | **9** | `)` |
| **\*键** | * | 显示运算符快捷提示 |
| **0键** | **0** | `.` |
| **#键** | 回车 / 搜索 | 切换模式（中文/英文/数字）|

数字模式补充：
- 输入一段算式后长按 **3键** 输入 `=`，如果能算出结果，会出现“确认”提示；按 OK/回车可以把结果补在 `=` 后面。
- 长按 **6键** 输入 `≈` 也可以显示结果，结果最多保留两位小数。
- 长按 **\*键** 会显示数字键对应的运算符提示；提示出现时可以直接短按对应数字键输入符号，按返回/删除/#/OK 可退出提示。

### 选区模式
| 操作 | 功能 |
| :--- | :--- |
| 长按 **OK/确认键** | 进入选区模式 |
| 选区模式下按方向键 | 扩展选区 |
| 选好后按 **OK/确认键** | 打开选区操作面板 |
| 面板中按 **上** | 复制 |
| 面板中按 **左** | 剪切 |
| 面板中按 **右** | 粘贴 |
| 面板中按 **下** | 删除 |
| 面板中按 **返回/删除** | 取消选区 |

### 密码模式
- 输入法现在能利用触屏机的优势，在输入密码时自动弹出26键全键盘，方便英文数字和符号混合的密码。
- 也可以手动在快捷设置【...】里开启，方便在不是输入密码的时候唤起。
- 有窥探图标可以快速收起全键盘，方便看到被盖住的验证码。
- 输入的所有文本会有预览框同步显示，方便看到被盖住的文本框，此设定可以在设置-虚拟键盘里取消。

### 按键音
- 默认提供一套内置备用按键音。
- 可使用百度输入法皮肤里的按键音，需要自己准备百度输入法 Android `.bds` 皮肤文件，在输入法设置的按键音包管理里手动导入。
- 导入时会先打开系统文件管理器选择 `.bds` 文件，再按包名自动填入名称；你可以在确认前修改名称。导入成功后音频会复制到输入法自己的存储里，原来的下载文件可以删除。
- 已导入的按键音包可以切换、改名和删除。加密或不包含可提取按键音的 `.bds` 皮肤无法使用。

## 项目进度

### 支持模式
- 拼音九键、五笔画九键、注音九键
- 英文模式（multi-tap 输入、Shift、Caps Lock）
- 联想英语模式（英文 T9 预测、词库候选、普通英文九键学习新词、学习词库管理）
- 数字模式（数字输入、常用运算符、简单计算结果）
- 物理按键选区模式
- 中文/英文模式长按输入数字
- 原本输入法1️已支持繁体切换

### 添加魔改的功能

- 九键按键映射
- 屏幕键盘在使用时常驻，只保留了必要的补充功能
- 添加 InkBlack、InkPink 主题和对应深色版本
- 支持从字体文件夹读取自定义字体，输入法界面可以使用用户放入的字体
- 长按 `#` 切换中文/英文/数字模式
- 中文模式->可在设置中保留需要的拼音、五笔画、注音方案，空闲时长按 `*` 快速切换
- 中文模式->输入中短按 `#` 提交上方显示的拼音、笔画或注音编码
- 拼音九键->支持拼音预测和拼音筛选栏
- 拼音九键->支持候选栏按设置的候选字数显示，拼音筛选后也会按这个设置显示
- 中文模式->支持按 `*` 打开符号候选，并在符号候选中切换中英文符号
- 中文模式->有拼音时按 `1` 插入拼音分词符号
- 中文模式->长按 `1`-`9`、`0` 可选择第 1-10 个汉字候选
- 符号候选->支持左右选择、OK/点选确认、上下翻页；第一页/最后一页继续按上下不会移动文本光标
- 中文模式->补全更多拼音候选，比如 `jiang`、`liang`、`kuan`、`kuang` 等
- 中文模式->选择汉字后，上方拼音预览会跟着已选内容更新
- 英文模式->支持大小写
- 联想英语模式->支持英文 T9 预测、更完整的英文词库、普通英文九键学习新词和学习词库管理
- 非文本输入场景->支持物理按键透传，避免游戏 / 模拟器键位映射被输入法拦截
- 实体按键->优化长按判断，减少卡顿场景下短按误触发长按候选或数字
- 数字模式->支持长按数字输入常用运算符和简单计算结果
- 长按 OK/确认键进入选区模式，可用物理方向键选择文本并复制、剪切、粘贴、删除
- 物理返回按键可删除文本
- 密码模式支持全键盘、数字行、输入预览和按住窥探
- 支持屏幕键和实体键按键音、内置默认音、百度输入法 Android `.bds` 皮肤按键音手动导入、按键音包改名/切换/删除和按键音预览
- 输入面板顶部圆角、候选浮窗阴影和快捷设置显示经过适配

### 后续计划
- 兼容百度输入法的皮肤。

## 截图
<img width="640" height="960" alt="Screenshot_20260318-123721_Keep 记事" src="https://github.com/user-attachments/assets/3ea558d4-c52d-4ba7-82c1-24b43b08f855" />


## Build （原本源项目的方法）

### Dependencies

- Android SDK Platform & Build-Tools 35.
- Android NDK (Side by side) 25 & CMake 3.22.1, they can be installed using SDK Manager in Android Studio or `sdkmanager` command line.
- [KDE/extra-cmake-modules](https://github.com/KDE/extra-cmake-modules)
- GNU Gettext >= 0.20 (for `msgfmt` binary; or install `appstream` if you really have to use gettext <= 0.19.)

### How to set up development environment

<details>
<summary>Prerequisites for Windows</summary>

- Enable [Developer Mode](https://learn.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) so that symlinks can be created without administrator privilege.

- Enable symlink support for `git`:

    ```shell
    git config --global core.symlinks true
    ```

</details>

First, clone this repository and fetch all submodules:

```shell
git clone git@github.com:fcitx5-android/fcitx5-android.git
git submodule update --init --recursive
```

Install `extra-cmake-modules` and `gettext` with your system package manager:

```shell
# For Arch Linux (Arch has gettext in it's base meta package)
sudo pacman -S extra-cmake-modules

# For Debian/Ubuntu
sudo apt install extra-cmake-modules gettext

# For macOS
brew install extra-cmake-modules gettext

# For Windows, install MSYS2 and execute in its shell (UCRT64)
pacman -S mingw-w64-ucrt-x86_64-extra-cmake-modules mingw-w64-ucrt-x86_64-gettext
# then add C:\msys64\ucrt64\bin to PATH
```

Install Android SDK Platform, Android SDK Build-Tools, Android NDK and cmake via SDK Manager in Android Studio:

<details>
<summary>Detailed steps (screenshots)</summary>

**Note:** These screenshots are for references and the versions in them may be out of date.
The current recommended versions are recorded in [Versions.kt](build-logic/convention/src/main/kotlin/Versions.kt) file.

![Open SDK Manager](https://user-images.githubusercontent.com/13914967/202184493-3ee1546b-0a83-4cc9-9e41-d20b0904a0cf.png)

![Install SDK Platform](https://user-images.githubusercontent.com/13914967/202184534-340a9e7c-7c42-49bd-9cf5-1ec9dcafcf32.png)

![Install SDK Build-Tools](https://user-images.githubusercontent.com/13914967/202185945-0c7a9f39-1fcc-4018-9c81-b3d2bf1c2d3f.png)

![Install NDK](https://user-images.githubusercontent.com/13914967/202185601-0cf877ea-e148-4b88-bd2f-70533189b3d4.png)

![Install CMake](https://user-images.githubusercontent.com/13914967/202184655-3c1ab47c-432f-4bd7-a508-92096482de50.png)

</details>

### Trouble-shooting

- Android Studio indexing takes forever to complete and cosumes a lot of memory.

    Switch to "Project" view in the "Project" tool window (namely the file tree side bar), right click `lib/fcitx5/src/main/cpp/prebuilt` directory, then select "Mark Directory as > Excluded". You may also need to restart the IDE to interrupt ongoing indexing process.

- Gradle error: "No variants found for ':app'. Check build files to ensure at least one variant exists." or "[CXX1210] <whatever>/CMakeLists.txt debug|arm64-v8a : No compatible library found"

    Examine if there are environment variables set such as `_JAVA_OPTIONS` or `JAVA_TOOL_OPTIONS`. You might want to clear them (maybe in the startup script `studio.sh` of Android Studio), as some gradle plugin treats anything in stderr as errors and aborts.

## Nix

Appropriate Android SDK with NDK is available in the development shell.  The `gradlew` should work out-of-the-box, so you can install the app to your phone with `./gradlew installDebug` after applying the patch mentioned above. For development, you may want to install the unstable version of Android Studio, and point the project SDK path to `$ANDROID_SDK_ROOT` defined in the shell. Notice that Android Studio may generate wrong `local.properties` which sets the SDK location to `~/Android/SDK` (installed by SDK Manager). In such case, you need specify `sdk.dir` as the project SDK in that file manually, in case Android Studio sticks to the wrong global SDK.
