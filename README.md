# onHit

<div align="center">

<img src="https://raw.githubusercontent.com/0penPublic/onHit/refs/heads/main/onhit-logo.svg" alt="icon" width="150" />

![Release Download](https://img.shields.io/github/downloads/Xposed-Modules-Repo/mba.vm.onhit/total?style=flat-square)
![Release Download](https://img.shields.io/github/downloads/0penPublic/onHit/total?style=flat-square)
[![Release Version](https://img.shields.io/github/v/release/0penPublic/onHit?style=flat-square)](https://github.com/0penPublic/onHit/releases/latest)  
[![GitHub Star](https://img.shields.io/github/stars/0penPublic/onHit?style=flat-square)](https://github.com/0penPublic/onHit/stargazers)
[![GitHub Star](https://img.shields.io/github/stars/Xposed-Modules-Repo/mba.vm.onhit?style=flat-square)](https://github.com/Xposed-Modules-Repo/mba.vm.onhit/stargazers)
[![GitHub Fork](https://img.shields.io/github/forks/0penPublic/onHit?style=flat-square)](https://github.com/0penPublic/onHit/network/members)
![GitHub Repo size](https://img.shields.io/github/repo-size/0penPublic/onHit?style=flat-square&color=3cb371)
[![GitHub license](https://img.shields.io/github/license/0penPublic/onHit?style=flat-square)](LICENSE)
[![GitHub Repo Languages](https://img.shields.io/github/languages/top/0penPublic/onHit?style=flat-square)](https://github.com/0penPublic/onHit/search?l=kotlin)
<br />
[![Telegram](https://img.shields.io/badge/Telegram-Join_Chat-blue.svg?style=for-the-badge&logo=telegram&color=12b7f5)](https://t.me/on_hit)

</div>

## Introduction / 简介

onHit is an Xposed module designed to simulate NFC tag events at the system level. By injecting data into the Android NFC framework, it enables the system to parse and dispatch NDEF and protocol-level data as if a physical tag were present.

---

onHit 是一个用于在系统层面模拟 NFC 标签事件的 Xposed 模块。通过向 Android NFC 框架注入数据，它使系统能够像处理实体标签一样解析和分发 NDEF 及协议层数据。

## How to Use / 如何使用

1. **Install & Enable**: Install onHit and enable the module in your Xposed manager. Scope it to **NFC Service** (`com.android.nfc`).
2. **Setup Storage**: Open onHit and select a working directory.
3. **Capture/Import**: From the "Add" menu, choose to read from physical tags, record a trace, or import existing files.
4. **Edit**: Use the NDEF Editor to modify records if necessary.
5. **Replay**: Click a data file in the list to trigger the system-level dispatch.

---

1. **安装并启用**: 安装 onHit 并在 Xposed 管理器中启用模块，作用域勾选 **NFC 服务** (`com.android.nfc`)。
2. **设置存储**: 打开 onHit 并选择一个工作目录。
3. **采集/导入**: 在“添加”菜单中选择从实体标签读取、记录轨迹或导入已有文件。
4. **编辑**: 如有需要，使用内置编辑器修改记录。
5. **重放**: 在文件列表中点击数据文件，即可触发系统级分发。

## Demonstration Videos / 演示视频
- [［Xposed模块］在无实体卡片的情况下用系统钱包app模拟一张特定UID的卡片](https://www.bilibili.com/video/BV168zDBGEzT/)
- [onHit TagRecorder 标签记录器使用方式案例](https://www.bilibili.com/video/BV1os3M67EHS/)

## Core Features / 核心功能

- **Multi-Protocol NFC Replay**: Simulates system-level NFC touch events via Xposed. Supports NDEF, Mifare Classic (MFC), and Trace-based protocol emulation.
- **Trace Reverse Simulation**: Replays previously recorded interaction traces to emulate complex tag behaviors.
- **NDEF Management**: Read from physical tags, save as files, and write back to tags using public Android APIs.
- **NDEF Editor**: Built-in editor to create or modify NDEF records directly within the app.
- **Tag Recorder**: Captures raw data streams during NFC tag interactions for local analysis.
- **Tag Trace**: View and analyze local NFC tag trace files.
- **File Manager**: Built-in manager for organizing data files, supporting renaming, deletion, and folder categorization.
- **Personalization**: Supports custom application backgrounds with built-in cropping.
- **Quick Search**: Real-time keyword filtering for local files.
---
- **多协议 NFC 重放**: 通过 Xposed 模拟系统级 NFC 触碰事件。支持 NDEF、Mifare Classic (MFC) 以及基于轨迹的多种协议模拟。
- **轨迹反向模拟**: 通过回放之前记录的交互轨迹，模拟复杂的标签行为。
- **NDEF 管理**: 从实体标签读取数据并保存为文件，或通过 Android 官方 API 将文件写回标签。
- **NDEF 编辑器**: 内置编辑器，支持直接在应用内创建或修改 NDEF 记录。
- **Tag Recorder**: 记录 NFC 标签交互过程中的原始数据流，仅用于本地分析。
- **Tag Trace**: 支持查看和分析本地存储的 NFC 标签轨迹文件。
- **文件管理**: 内置文件管理器，支持重命名、删除及文件夹分类管理。
- **个性化**: 支持自定义应用背景，内置裁剪功能。
- **快速搜索**: 对本地文件进行实时关键词过滤。



## Technical Principles / 技术实现

### System Injection / 系统注入
The module hooks the `NfcApplication` within the `com.android.nfc` process. It retrieves internal references to the `NfcService` and its associated `Handler` to interact with the system's NFC logic.

---

模块 Hook 了 `com.android.nfc` 进程中的 `NfcApplication`，并获取了内部 `NfcService` 及其关联 `Handler` 的引用，从而实现与系统 NFC 逻辑的交互。

### Protocol Emulation / 协议仿真
- **NDEF**: Directly injects NDEF messages into the dispatch system.
- **Mifare Classic**: Emulates the storage structure and protocol interaction logic of MFC tags.
- **TagTrace**: Accurate replay of specific interaction flows by matching historical transceive commands with captured response data.

---

- **NDEF**: 直接向分发系统注入 NDEF 消息。
- **Mifare Classic**: 模拟 MFC 标签的存储结构与协议交互逻辑。
- **TagTrace**: 通过匹配历史 Transceive 指令及其响应数据，实现对特定交互流程的精准回放。

### Event Simulation / 事件模拟
Tag replay is achieved by reflectively invoking the `dispatchTagEndpoint` method on the NFC service handler. This bypasses hardware-level constraints and directly injects a custom `TagEndpoint` into the Android dispatch system.

---

标签重放通过反射调用 NFC 服务 Handler 的 `dispatchTagEndpoint` 方法实现。这绕过了硬件层面的限制，直接向 Android 分发系统注入自定义的 `TagEndpoint`。

### Data Collection / 数据采集
The **Tag Recorder** intercepts `TagEndpoint` objects before they are dispatched by the system, allowing the module to log raw interaction data. All captured data is stored locally on the device and is not uploaded to any server.

---

**Tag Recorder** 在系统分发前拦截 `TagEndpoint` 对象，从而实现对原始交互数据的记录。所有采集到的数据均仅保存在设备本地，不会上传至任何服务器。

### Compatibility & Limitations / 兼容性与局限性
- **Oplus (ColorOS)**: Specialized hooks for `NfcDispatchManager` to bypass foreground whitelist restrictions and clear the system's internal UID database cache via `DatabaseManager`.
- **System Features**: Hooks `ApplicationPackageManager` to ensure `hasSystemFeature` correctly reports NFC capabilities.
- **Hardware Dependency**: Strongly dependent on Android version and vendor NFC implementation. Some OEM frameworks may restrict NFC internals.

---

- **Oplus (ColorOS)**: 针对 `NfcDispatchManager` 进行专项 Hook，以绕过前台白名单限制，并清理系统内部 UID 数据库缓存。
- **系统特征**: Hook 了 `ApplicationPackageManager`，确保系统特征正确返回 NFC 支持状态。
- **硬件依赖**: 运行效果高度依赖 Android 版本和厂商的 NFC 实现，部分定制 ROM 可能会对 NFC 内部逻辑进行修改或限制。


## Acknowledgments / 致谢
Special thanks to the following projects:
- [LSPosed](https://github.com/LSPosed/LSPosed)
- [EzXHelper](https://github.com/KyuubiRan/EzXHelper)
- [AndroidX](https://developer.android.com/jetpack/androidx)
- [Material Symbols](https://fonts.google.com/icons)



## Legal & Ethical Notice / 法律与道德声明

This project is for **research, learning, and testing purposes only**.
The user is solely responsible for any actions taken using this software. Do not use this tool to bypass security mechanisms or violate privacy policies.

本项目仅用于**研究、学习与测试**。
用户对使用本软件所采取的任何行为承担全部责任。请勿使用本工具绕过安全机制或违反隐私政策。



## License / 许可证

This project is licensed under the **GNU General Public License v2.0 (GPLv2)**.

本项目采用 **GNU General Public License v2.0 (GPLv2)** 许可证。



## Star History

<a href="https://www.star-history.com/?repos=0penPublic%2FonHit&type=date&legend=bottom-right">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=0penPublic/onHit&type=date&theme=dark&legend=bottom-right&sealed_token=85dpLsEIerjDiBFE_JiLS6xeLKMvT3EKS7iJhuXgID1KzYA9l-yhdy0MMCfayr-rmeoXI94TKKeOr-ty51sTL5ogGTERBgUzIznZ2NytBxl9EXwascdhog" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=0penPublic/onHit&type=date&legend=bottom-right&sealed_token=85dpLsEIerjDiBFE_JiLS6xeLKMvT3EKS7iJhuXgID1KzYA9l-yhdy0MMCfayr-rmeoXI94TKKeOr-ty51sTL5ogGTERBgUzIznZ2NytBxl9EXwascdhog" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=0penPublic/onHit&type=date&legend=bottom-right&sealed_token=85dpLsEIerjDiBFE_JiLS6xeLKMvT3EKS7iJhuXgID1KzYA9l-yhdy0MMCfayr-rmeoXI94TKKeOr-ty51sTL5ogGTERBgUzIznZ2NytBxl9EXwascdhog" />
 </picture>
</a>
