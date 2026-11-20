# Codex 交接说明：OPPO Watch X2 mini 空鼠 + PPT 遥控

## 0. 目标

请在本仓库现有 Android 原型基础上继续开发、修复并交付可安装 APK。目标设备是中国大陆版 **OPPO Watch X2 mini**，不要假设它是标准 Google Wear OS 设备，也不要引入必须依赖 Google Play Services / Wear OS 专用库的组件。

用户最重要的目标：
1. 保留完整的隔空鼠标基本功能：手腕转动控制电脑上的真实鼠标指针，能点击/拖动。
2. 在 PPT 放映时，电脑屏幕上仍然能看到鼠标指针，并能切换到 PowerPoint 激光笔模式（目标快捷键先按 Ctrl+L 实测，必要时按目标 PowerPoint 版本修正）。
3. 用甩腕/快速手腕动作翻上一页、下一页；需要防抖、阈值校准和方向可配置。
4. 圆形 466×466 手表界面，操作必须适合小屏，不能照搬手机纵向按钮列表。
5. 不依赖 WearMouse、Drift 或 Wear OS 应用运行环境。

## 1. 设备与已知事实

- 设备：中国大陆版 OPPO Watch X2 mini
- 系统：Android 11
- ColorOS Watch：V17.0.0
- 屏幕：圆形，466 × 466
- 用户此前用 ADB 检查：
  - `adb shell pm list features | findstr /i hid` 没有输出
  - `adb shell service list | findstr /i hid` 没有输出
- WearMouse 可以安装，但启动闪退；不要再依赖 Wear OS 共享库。
- 用户有本地 Android APK 编译环境，可以在本地编译、安装和提供 Logcat。

注意：上面的 ADB 输出不能单独证明 BluetoothHidDevice API 不存在；必须在运行时检查 `BluetoothAdapter.getProfileProxy(..., BluetoothProfile.HID_DEVICE)`、`onAppStatusChanged` 和设备蓝牙栈行为。也不要假设经典 HID 一定可用。

## 2. 当前仓库

GitHub： https://github.com/YangXuanpu/linkpad-watch-standalone

主要文件：
- `app/src/main/java/com/linkpad/watch/MainActivity.java`：当前原型 UI、陀螺仪事件和快捷键入口
- `app/src/main/java/com/linkpad/watch/HidService.java`：经典 Bluetooth HID Device profile 服务
- `app/src/main/java/com/linkpad/watch/HidProtocol.java`：HID report descriptor、鼠标/键盘报告
- `app/src/main/AndroidManifest.xml`：权限、Activity、Service
- `app/build.gradle`：Android 构建配置
- `.github/workflows/build-apk.yml`：GitHub Actions APK 构建

## 3. 当前构建状态（已核实）

GitHub Actions run：
https://github.com/YangXuanpu/linkpad-watch-standalone/actions/runs/44697735930

Run ID：44697735930
Commit：e6edb2f3757e6ee4624f09f56aa8077c28f70380
状态：success

已通过步骤：
- JDK 17 初始化
- Android SDK 安装
- Gradle assembleDebug
- apksigner APK 签名验证
- Artifact 上传

已上传 Artifact：`Linkpad-Watch-debug-APK`
当前 APK：
- 文件名：`app-debug.apk`
- applicationId：`com.linkpad.watch`
- versionName：`1.0.0`
- minSdk：28
- targetSdk：32
- debug 签名
- 文件大小约 30 KB

这只证明 APK 已经成功编译并通过签名验证，**不代表已在 OPPO Watch X2 mini 上安装或验证蓝牙 HID、传感器和 PPT 行为**。

## 4. 当前原型的限制 / 必须先审查的风险

请先完整阅读源码并做 code review，不要把当前原型视为完成品。

1. 当前只实现了经典 `BluetoothHidDevice` 路径；仓库里没有完成 BLE HOGP GATT HID 外设服务。不要在说明中声称 BLE 已实现。
2. 当前连接按钮会遍历已配对设备，不是可靠的目标电脑选择器；应做已配对设备列表、连接状态、断开和重连。
3. 当前 UI 是纵向按钮列表，圆屏适配不足；按钮数量过多，可能挤压/滚动困难。必须重做圆屏布局。
4. 当前陀螺仪映射只是简单积分，缺少中位校准、死区、灵敏度、平滑、漂移控制、方向反转和暂停/锁定。
5. 当前甩腕翻页是简单阈值检测，需加入轴向/方向识别、动作冷却、回中条件、灵敏度调节和误触保护。
6. 当前空鼠默认关闭；需要明确模式状态和可视反馈。
7. 当前鼠标按钮报告和 HID descriptor 必须逐字节核对；加入单元测试或独立协议测试，避免 descriptor 与 report 长度/Report ID 不一致。
8. 当前 `onGetReport`、服务生命周期、蓝牙开关后重初始化、Activity bind/unbind、运行时权限处理需全面检查。
9. 当前没有 BLE HOGP 实现。若经典 HID Device profile 不可用，评估并实现 Android 11 上可用的 BLE GATT HID Service（HOGP：HID Service 0x1812、Report Map、Report、Protocol Mode、HID Information、必要的 CCCD/安全配对/设备信息特征），并在真实 Windows 主机测试。不能只加一个“BLE 备用”开关却没有真实 GATT 报告通路。
10. Android API/ColorOS 厂商限制必须通过设备日志确认；如果系统固件禁止第三方 HID Device 注册，明确说明限制并给出替代路线，不要虚构可用性。

## 5. 推荐开发顺序

### P0：让现有 APK 在设备上可诊断
- 添加完整状态页：蓝牙开关、权限、经典 HID profile 是否取得、registerApp 结果、连接状态、传感器可用性。
- 增加可复制的诊断日志（不记录敏感数据）。
- 避免 service crash；处理蓝牙关闭/打开、进程恢复、权限拒绝、profile proxy 断开。
- 添加真正的设备选择器，不要连接所有 bond devices。

### P1：先完成可靠的鼠标
- 触屏板模式：触摸区域移动光标，轻点左键，双指/独立按钮右键，长按拖动。
- 空鼠模式：陀螺仪控制鼠标，中心校准、死区、灵敏度档位、暂停锁定。
- 发送标准 HID mouse report；按下/释放必须成对，移动/点击/拖动都要测。
- PC 端指针必须真实移动，不是只在手表 UI 里移动。

### P2：PPT / 激光笔
- 进入 PPT 模式时，保留空鼠指针移动。
- 提供“激光笔模式”开关；发送 Ctrl+L（或实测对应快捷键），并持续发送鼠标移动，使电脑上的激光点跟随手腕移动。
- 明确上一页/下一页动作方向；支持左右甩腕，带冷却时间与灵敏度设置。
- 手表上提供明显的模式/连接提示，避免误翻页。
- 验证 PowerPoint 桌面版的实际快捷键、激光笔行为和退出方式；不要仅凭快捷键名称推断。

### P3：圆屏 UI
- 目标画布：466×466，考虑圆角裁切安全区。
- 首页只保留 3–4 个主入口：连接、空鼠、PPT、设置。
- 鼠标页：大触控板 + 左右键 + 校准/暂停。
- PPT 页：激光笔、上一页、下一页、开始/退出放映、手势开关。
- 使用大按钮、少文字、明确连接状态；不要用密集手机表单。
- 适配不同系统字体缩放和屏幕安全区。

### P4：BLE HOGP（若经典 HID 不可用）
- 做运行时能力检测后再选择传输。
- 实现完整 GATT HID peripheral，确保 PC 能发现、配对、订阅 report characteristic，并接收 mouse/keyboard report。
- 提供 Windows 10/11 配对和断连恢复测试步骤。
- 如果 ColorOS Watch 不允许应用以 Peripheral/GATT Server 方式广播或不允许 HID profile，记录实测证据和不可行边界。

## 6. 手势建议

优先做陀螺仪可稳定识别的动作：
- 短促左右甩腕：上一页 / 下一页
- 双击屏幕：暂停/恢复空鼠（可选）
- 抬腕/翻腕：进入/退出激光笔（必须防误触，可选）

“握拳/捏合”通常需要额外传感器或手势识别硬件，不能仅凭普通手表陀螺仪可靠识别。先检测设备实际可用传感器，再决定是否支持；不要将不可实现的手势作为已交付功能。

## 7. 编译与交付

- 保持可复现构建，建议加入 Gradle Wrapper（`gradlew`、`gradle/wrapper`）。
- 使用 JDK 17、Android Gradle Plugin 8.6.1、Gradle 8.9 或兼容组合。
- 当前 CI workflow 已能在 GitHub Actions 构建并上传 APK。
- 本地构建命令（有 wrapper 后）：
  `./gradlew clean assembleDebug`
- APK 预期位置：
  `app/build/outputs/apk/debug/app-debug.apk`
- 每次改动先构建成功，再交付 APK。
- 交付时同时给出：APK、commit hash、构建日志链接、版本号、已验证设备/功能、未验证项。
- 不要声称“已在手表可用”，除非用户实机测试通过。

## 8. 实机测试清单（用户可协助）

请按步骤指导用户并收集结果：
1. `adb install -r app-debug.apk`
2. 手表开启蓝牙并授予运行时权限。
3. 打开 App，记录“经典 HID 注册状态”。
4. Windows 蓝牙设置中查找/配对手表。
5. 测鼠标移动、左键、右键、拖动。
6. 开启空鼠，校准后测光标移动、漂移、延迟。
7. 打开 PowerPoint 放映，测激光笔开关与移动。
8. 测甩腕翻页误触率、方向和连续动作。
9. 若连接失败，采集：
   - `adb logcat -d -s BluetoothAdapter BluetoothHidDevice BluetoothGatt BluetoothManager AndroidRuntime`
   - `adb shell dumpsys bluetooth_manager`
   - App 内诊断日志。

## 9. 给 Codex 的执行要求

请直接在本仓库里工作：
1. 先检查所有源文件、Manifest、Gradle 和 CI，不要只根据本说明盲改。
2. 做一次技术审查，列出可复现的问题和优先级。
3. 先修复 P0，再完成 P1 鼠标，再完成 P2 PPT，再优化 P3 圆屏 UI。
4. 若经典 HID 不可用，评估并实现真实 BLE HOGP；不能把未实现的方案写成已完成。
5. 增加测试，尤其是 HID report descriptor / report byte layout / 手势状态机。
6. 每个阶段都构建 APK；最终交付完整可安装 APK 和安装说明。
7. 不要仅交付伪代码、设计稿或源码片段。

## 10. 交接时的关键事实

- 用户要的是能控制 Windows 电脑的“真实鼠标”，PPT 上需要显示光标/激光笔点。
- 空鼠不能因为加入 PPT 功能而被删除；两种模式要能切换，PPT 模式最好仍允许空鼠移动。
- 设备是圆形小屏，不是方形 Wear OS 手表。
- 用户本地有 Android APK 编译能力，可以协助本地编译和 ADB 实机测试。
