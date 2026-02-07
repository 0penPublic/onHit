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
[![Telegram](https://img.shields.io/badge/Telegram-on_hit-blue.svg?style=flat-square&color=12b7f5)](https://t.me/on_hit)

</div>

## 什么是 onHit ?

这是一个 **Xposed 模块**，用于在 **系统内部模拟 NFC 标签触碰事件**，从而触发系统对 **NDEF 数据** 的解析与分发流程，使目标应用能够像接收到真实 NFC 标签一样处理 NDEF 内容。

模块内置了一个 **简易的文件管理器**，用于：

 - 从实体 NFC 标签中读取并保存 **NDEF 数据（原始字节流）**

 - 将已保存的 **NDEF 文件**写入实体 NFC 标签

NDEF 的 读写过程完全基于 Android 官方公开 API 实现，理论上在不使用 Xposed 的情况下也可独立运行；

Xposed 仅用于实现 **“无实体标签的 NDEF 重放”**，即在系统层直接注入 NFC 触碰事件，这是普通应用无法完成的部分。

## What is onHit?

onHit is an **Xposed module** that simulates **NFC tag touch events at the system level**, allowing the Android system to parse **NDEF data** and dispatch NFC events as if a real NFC tag were presented.

The module includes a **simple built-in file manager** that can:

- Extract and store **NDEF data (raw byte streams)** from physical NFC tags
- Write previously saved **NDEF files** back to physical NFC tags

All NDEF read and write operations are implemented using **Android’s public system APIs** and can function independently without Xposed.

Xposed is only required to enable **tagless NDEF replay**, which injects NFC tag events directly into the system — a capability not available to regular applications.

## Requirements / 使用需求

- `Rooted Android device` 

  `已有 Root 权限的 Android 设备`
- `Dreamland` or `LSPosed` etc. Environment
  
  `Dreamland` 或者 `LSPosed` 之类的环境
- Android system with AOSP-like NFC framework (vendor ROMs may vary) 

  AOSP 类似的 NFC 框架 (部分手机厂商私有的实现可能会无法使用)

## How to Use / 如何使用

 1. Install the onHit application. 
    
    安装 onHit 应用程序。
 2. **Enable Module**: If you have an *Xposed environment*, enable `onHit` and scope it to **NFC Service** (`com.android.nfc`). 
 
    **启用模块**：在 *Xposed 环境*中激活 `onHit` 并勾选 **NFC 服务** (`com.android.nfc`)。
 3. **Setup Storage**: Open `onHit` and choose a directory to store your NDEF files.

    **设置存储**：打开 `onHit` 并选择一个用于存放 NDEF 文件的文件夹。
 4. **Import**: *Import NDEF data* from a physical NFC tag or local files.

    **导入**：从实体 NFC 标签或本地文件*导入 NDEF 数据*

 5. **Replay (Xposed Required)**: Click an NDEF file in the list. The module will triggering the Android NFC dispatch system as if a real tag were detected.

    **(需要 Xposed) 重放/模拟**：在文件列表中点击 NDEF 文件。模块将触发 Android 系统的 NFC 分发流程。
 
## Acknowledgments
 Special thanks to the following projects for their invaluable contributions to the community:
 - [LSPosed](https://github.com/LSPosed/LSPosed)
 - [ExXHelper](https://github.com/KyuubiRan/EzXHelper)
 - [AndroidX](https://developer.android.com/jetpack/androidx)
 
    ...

## Limitations

- Strongly dependent on Android version and vendor NFC implementation
- Some OEM frameworks may modify or restrict NFC internals
- Not intended for production use
- No guarantee of compatibility across devices or ROMs

## Legal & Ethical Notice

This project is intended for **research, learning, and testing purposes only**.

Do NOT use this project to:
- Bypass security mechanisms without authorization
- Attack or impersonate real-world NFC systems
- Violate laws, terms of service, or privacy policies

You are solely responsible for how you use this software.

## License

This project is licensed under the **GNU General Public License v2.0 (GPLv2)**.

You may use, modify, and redistribute this software under the terms of GPLv2.
Any derivative work must also be distributed under the same license.

See the `LICENSE` file for full license text.

## Star History

<a href="https://www.star-history.com/#0penPublic/onHit&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=0penPublic/onHit&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=0penPublic/onHit&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=0penPublic/onHit&type=date&legend=top-left" />
 </picture>
</a>
