# 淦！小米安全访问

面向 Android 16 HyperOS 的 LSPosed 模块，使用现代 libxposed API 102。安装 `build/HyperOSPicker.apk` 后，在 LSPosed 管理器中启用模块，勾选 `com.android.photopicker` 和 `com.android.fileexplorer`，再重启对应进程。

1.5 起包名为 `cn.wangyupwp.hyperospicker`。Android 会把它当作新应用安装。请先在 LSPosed 停用旧包 `dev.local.hyperospicker`，避免两个模块同时处理选择请求。

- 进入目标照片选择器的请求会尝试转给 Android 原生 DocumentsUI，并限制为照片或视频。调用方允许多选时会启用原生多选。原生文件选择器无法按照片选择器的参数限制具体张数，返回的也是文档 URI。
- 小米文件选择界面收到的标准打开文件、取文件和选择文件夹请求会尝试转给 DocumentsUI。转发副本只设置结果回传标志，原请求里的 URI 授权标志会被清除。创建文档和厂商专用请求由原界面处理。
- 模块没有桌面入口。可从 LSPosed 的模块设置打开系统特殊应用权限页面，由系统处理确认和等待。

转发优先使用系统的 `com.android.documentsui`，也支持系统的 `com.google.android.documentsui`。已经由 DocumentsUI 处理的请求直接继续。模块在启动 DocumentsUI 前先调用原界面的父类初始化，让 AndroidX 恢复组件状态，转发后关闭原界面。找不到目标时保留原选择流程。如果初始化完成后系统拒绝启动所有目标，则结束此次选择。其他 HyperOS 构建可能使用不同的源组件。

从旧版升级后，重启照片选择器和小米文件管理进程。如果原版「不安全访问」也已启用，先在 LSPosed 关闭它的照片选择器作用域，避免两个模块同时处理同一次请求。

在 Windows 上安装 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0，配置 `JAVA_HOME` 和 `ANDROID_HOME`，然后运行 `powershell -ExecutionPolicy Bypass -File .\build.ps1`。脚本会从 Maven Central 取得编译用的 libxposed API，使用项目本地调试密钥签名 APK，并校验签名。
