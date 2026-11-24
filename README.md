# 淦！小米安全访问

这是一个面向小米和 Redmi HyperOS 的 LSPosed 模块。它把部分照片、视频和文件选择请求交给 Android 原生文件选择器（DocumentsUI）。模块使用 libxposed API 102，包名为 `cn.wangyupwp.hyperospicker`。

## 软件介绍

- **照片和视频**　应用请求选图或选视频时，模块尝试打开 DocumentsUI。允许多选的请求会启用原生多选。DocumentsUI 无法沿用照片选择器指定的具体张数上限，返回的是文档 URI，因此依赖照片选择器专用 URI 的应用可能不兼容。
- **文件和文件夹**　小米文件管理收到标准的打开文件、取文件或选择文件夹请求时，模块尝试转给 DocumentsUI。创建文档和小米专用请求由原界面处理。
- **模块设置**　模块默认没有桌面图标。可从 LSPosed 的「模块设置」打开系统特殊应用权限页面，确认与等待由系统处理。

模块依赖 HyperOS 选择器和 DocumentsUI 的系统组件。不同机型或系统版本可能使用不同组件。找不到可用的 DocumentsUI 时，请求会继续走原选择流程。

## 安装与启用

1. 安装编译生成的 APK，在支持 API 102 的 LSPosed 管理器中启用模块。
2. 按需勾选作用域。`com.android.photopicker` 对应照片和视频，`com.android.fileexplorer` 对应小米文件管理。
3. 重启手机或对应应用进程，再从其他应用发起选择请求。

从旧包名 `dev.local.hyperospicker` 迁移，或同时安装过其他接管选择器的模块时，请先停用旧模块中重叠的作用域，避免同一次选择请求弹出两个界面。

## 编译方式

项目附带 Windows PowerShell 构建脚本。准备 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0。首次编译需要能访问 Maven Central，以下载编译用的 libxposed API。

在 PowerShell 里执行。请把示例路径换成自己的安装路径。

```powershell
git clone https://github.com/wangyupwp/hyperospicker.git
Set-Location hyperospicker

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = 'C:\Android\Sdk'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

生成的 APK 位于 `build/HyperOSPicker.apk`。默认使用项目本地的调试密钥签名，脚本会验证签名后输出 APK。

发布时可用自己的密钥库签名。请替换示例中的路径和别名，并在本地终端按提示输入密码。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -KeyStore 'C:\keys\release.jks' -KeyAlias 'release'
```

同一包名的后续版本要使用相同的签名密钥才能覆盖安装。请将密钥库和密码保存在仓库之外。

项目采用 [GPL-3.0](LICENSE)。
