<div align="center">

# BiliClient-Fix

<a href="https://github.com/huanli233/BiliClient">上游 BiliClient</a> ｜
<a href="https://github.com/bggRGjQaUbCoE/PiliPlus">参考项目 PiliPlus</a> ｜
<a href="https://github.com/huanli233/BiliClient/issues">问题反馈</a>

<br>

<div>
<a href="https://github.com/huanli233/BiliClient/releases"><img src="https://img.shields.io/github/v/release/huanli233/BiliClient?color=76bad9&label=base%20version"></a>
<a href="https://www.android.com/"><img src="https://img.shields.io/badge/platform-Android-3DDC84.svg" alt="android"></a>
<a href="https://www.java.com/"><img src="https://img.shields.io/badge/language-Java-007396.svg" alt="java"></a>
<a href="https://developer.android.com/"><img src="https://img.shields.io/badge/minSdk-14%20%28Android%204.0.4%29-orange.svg" alt="minsdk"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="license"></a>
</div>

<br>

**轻量的第三方 B 站 Android 客户端 —— 接口风控修复与多方式登录增强版**

</div>

BiliClient-Fix 是 [huanli233/BiliClient](https://github.com/huanli233/BiliClient)（哔哩终端）的 fork 修复版，在保持原项目**轻量、极简**的前提下，从 [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) 迁移了部分接口避免风控，迁移了**密码登录 / 手机号（短信验证码）登录**能力。使用原生 `Java + XML` 开发，最低支持 Android 4.0.4。

> [!NOTE]
> 本项目是个人学习与技术研究的 fork 修复版，仅用于学习和测试。所有 API 均从官方网站收集，不提供任何破解内容。请遵守相关法律法规与平台条款，下载后 24 小时内自行删除。

## ✨ 主要修改

1. 🛡️ **接口风控修复**：补齐 `WBI` 签名、APP 端 `appSign`（appkey/appsec 签名）、`buvid` 与 `dm_img` 等风控参数，覆盖推荐、视频信息、播放地址、评论、动态、搜索、直播等主要接口，缓解因风控导致的请求失败、返回空数据或 -403/-412 等问题。
2. 🔐 **密码 / 手机号登录**：新增密码登录与短信验证码登录，支持 APP 与 WEB 两套接口，RSA 加密传输；触发图形验证码风控时提示改用扫码登录。原有扫码登录继续保留。
3. 🎬 **内置播放器稳定性修复**：默认使用软件解码，并为硬解增加探测看门狗，规避老 ijkplayer so 在 Android 高版本上 `onSelectCodec` 触发的 native SIGILL 崩溃；播放器重启加入代次校验，防止异步回调竞态。
4. 🪶 **保持轻量**：延续原项目“少依赖、优先可用与流畅”的设计，不塞入与核心体验无关的重型组件。

## 📦 下载与安装

从 [Releases](https://github.com/huanli233/BiliClient/releases) 下载 APK 直接安装；或克隆源码后本地编译。

## 🔧 从源码构建

```bash
git clone https://github.com/huanli233/BiliClient
cd BiliClient
```

使用 Android Studio 打开项目，或使用 Gradle 命令行构建：

```bash
# AGP 7.3.1 + Gradle 7.4 需使用 JDK 17
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

> [!TIP]
> 当前环境的 `JAVA_HOME` 可能指向更高版本的 JDK（如 JDK 25），与 Gradle 7.4 不兼容。请显式切换到 JDK 17 后再构建。

## 🤝 鸣谢

本项目的诞生离不开以下开源项目，在此致以诚挚谢意 ❤️

- **[huanli233/BiliClient](https://github.com/huanli233/BiliClient)** —— 本项目的上游（哔哩终端），轻量、极简的第三方 B 站客户端，本项目在其基础上进行风控修复与登录增强。
  - 原作者 [RobinNotBad/BiliClient](https://gitee.com/RobinNotBad/BiliClient)，并感谢 [WearBili](https://github.com/SpaceXC/WearBili) 与 [腕上哔哩](https://github.com/luern0313/WristBilibili) 提供的部分开源代码与 API 收集。
- **[bggRGjQaUbCoE/PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus)** —— 本项目的参考项目，密码 / 手机号登录功能迁移自其实现，其硬解有序回退与播放器生命周期管理思路也为播放器稳定性修复提供了参考。
  - 致敬 [guozhigq/pilipala](https://github.com/guozhigq/pilipala) 与原上游 [orz12/PiliPalaX](https://github.com/orz12/PiliPalaX)。

同时感谢所有为相关项目贡献代码、接口文档与问题反馈的开发者。

## ❤️ 贡献

欢迎任何 Issues / Pull Requests！发现风控相关的问题、登录方式失效或有改进建议，都欢迎反馈。新增功能建议先通过 Issue 讨论。

## 📄 许可证

本项目遵循 [GPL-3.0](LICENSE) 开源协议。

## ⭐ Star History

> [!TIP]
> 如果本项目或上游项目对你有帮助，欢迎去 [上游仓库](https://github.com/huanli233/BiliClient) 点一个 Star，这是开源维护者持续投入的动力 <3

<div align="center">

[![Star History Chart](https://api.star-history.com/svg?repos=huanli233/BiliClient&type=Date)](https://star-history.com/#huanli233/BiliClient&Date)

</div>

<div align="center">

_轻量，是为了把有限的资源用在真正重要的体验上。_

</div>
