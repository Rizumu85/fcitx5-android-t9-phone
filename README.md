# fcitx5-android-t9-phone

企鹅输入法魔改成支持物理九键的智能安卓机，对其他输入方式的支持可能有bug。

## 下载
在本项目的Release下载安装包。[Release](https://github.com/Rizumu85/fcitx5-android-t9-Phone/releases)
根据Release的说明下载对应的版本
接着在另一个库的Release下载相关插件相关的文件。[rime-ice-t9-phone](https://github.com/Rizumu85/rime-ice-t9-phone/releases)

### 安装方法
- 在手机上安装下载下来的APK，输入法本体和插件。
- 输入法本体，按照App显示的要求，开启输入法。
- 在输入法设置里进行一下操作：
    - 【插件】查看相应的rime插件有无被检测到。
    - 【附加组件】查看中州韵有无被勾选，需被勾选。
    - 【输入法】点右下角的添加，添加中州韵。可以按右上角的编辑删掉English和拼音输入法。
- 进入一个能输入文本的地方打开输入法-设置（三个点的那个图标）- 【< >】（两个大于小于的图标-重新部署 【必做】
- 把下载下来的压缩包，解压，并把里面的 所有文件和文件夹
- 导进 安卓手机里的 文件夹 Androids/data/org.fcitx.fcitx5.android/files/data/rime (没有的话就是忘记提前先部署了）

- 再次进入一个能输入文本的地方打开输入法-设置（三个点的那个图标）- 【< >】（两个大于小于的图标-同步
- 等待完成后，朙月拼音会变成雾凇拼音
- 点开后选择中文九键模式
- 恭喜你可以用了！


## 九键键盘使用逻辑

### 中文模式
| 按键 | 短按内容 | 长按内容 |
| :--- | :--- | :--- |
| **1键** | 常用标点符号 | 数字 1 |
| **2键** | ABC | 数字 2 |
| **3键** | DEF | 数字 3 |
| **4键** | GHI | 数字 4 |
| **5键** | JKL | 数字 5 |
| **6键** | MNO | 数字 6 |
| **7键** | PQRS | 数字 7 |
| **8键** | TUV | 数字 8 |
| **9键** | WXYZ | 数字 9 |
| ***键** | * | 无 |
| **0键** | 空格 | 数字 0 |
| **#键** | 回车 / 搜索 | 切换模式（中文/英文/数字） |

### 英文模式
| 按键 | 短按 | 长按 |
| :--- | :--- | :--- |
| **1键** | 常用标点符号 | 数字 1 |
| **2键** | ABC | 数字 2 |
| **3键** | DEF | 数字 3 |
| **4键** | GHI | 数字 4 |
| **5键** | JKL | 数字 5 |
| **6键** | MNO | 数字 6 |
| **7键** | PQRS | 数字 7 |
| **8键** | TUV | 数字 8 |
| **9键** | WXYZ | 数字 9 |
| ***键** | **Shift** | **锁定大写 (Caps Lock)** |
| **0键** | 空格 | 数字 0 |
| **#键** | 回车 / 搜索 | 切换模式 （中文/英文/数字）|

### 数字模式
| 按键 | 短按内容 | 长按内容 |
| :--- | :--- | :--- |
| **1键** | **1** | 无 |
| **2键** | **2** | 无 |
| **3键** | **3** | 无 |
| **4键** | **4** | 无 |
| **5键** | **5** | 无 |
| **6键** | **6** | 无 |
| **7键** | **7** | 无 |
| **8键** | **8** | 无 |
| **9键** | **9** | 无 |
| ***键** | * | 无 |
| **0键** | **0** | 无 |
| **#键** | 回车 / 搜索 | 切换模式（中文/英文/数字）|


## 项目进度

### 支持模式
- 中文九键模式
- 英文模式 （大小写切换）
- 数字模式
- 长按输入数字

### 添加魔改的功能

- 九键按键映射
- 屏幕键盘只保留了必要的补充功能
- 屏幕键盘在使用时常驻
- 输入拼音预测
- 拼音筛选栏 （只做了ui，功能还没有做，而且有时候会有刷新不到的小bug）

### 计划支持的功能

- 让拼音筛选栏 能用
- 长按确认键来多选文字
- 数字模式的1键长按加入常用数学符号，0键长按加入空格
- 数字模式的长按看能不能加入特殊功能
- 中文模式的*看能不能加入特殊功能
- 优化代码冗余
- 处理ui的bug

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
