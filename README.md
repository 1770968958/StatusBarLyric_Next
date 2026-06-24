# StatusBarLyric_Next

![ic\_home\_background.webp](ic_home_background.webp)

<div align="center">

![Release Download](https://img.shields.io/github/downloads/1770968958/StatusBarLyric_Next/total?style=flat-square)
[![Release Version](https://img.shields.io/github/v/release/1770968958/StatusBarLyric_Next?style=flat-square)](https://github.com/1770968958/StatusBarLyric_Next/releases/latest)
[![GitHub Star](https://img.shields.io/github/stars/1770968958/StatusBarLyric_Next?style=flat-square)](https://github.com/1770968958/StatusBarLyric_Next/stargazers)
[![GitHub Fork](https://img.shields.io/github/forks/1770968958/StatusBarLyric_Next?style=flat-square)](https://github.com/1770968958/StatusBarLyric_Next/network/members)
![GitHub Repo size](https://img.shields.io/github/repo-size/1770968958/StatusBarLyric_Next?style=flat-square\&color=3cb371)
[![GitHub license](https://img.shields.io/github/license/1770968958/StatusBarLyric_Next?style=flat-square)](LICENSE)

[![Android CI for main](https://github.com/1770968958/StatusBarLyric_Next/actions/workflows/ci_main.yml/badge.svg)](https://github.com/1770968958/StatusBarLyric_Next/actions/workflows/ci_main.yml)

</div>

## 这是什么？

StatusBarLyric_Next 是一个用于在 Android 状态栏显示歌词的 LSPosed / Xposed 模块。

本项目 fork 自原项目，并尝试在原有基础上继续更新、修复和适配新系统环境。

* 原仓库：[577fkj/StatusBarLyric](https://github.com/577fkj/StatusBarLyric)
* 当前仓库：[1770968958/StatusBarLyric_Next](https://github.com/1770968958/StatusBarLyric_Next)

## 主要功能

* 在状态栏显示当前播放歌词
* 支持歌词文字、颜色、大小、位置等样式设置
* 支持歌词滚动、动态速度和图标显示
* 支持导入、导出配置
* 针对 SystemUI 场景优化刷新和渲染性能

## 使用前必读

本模块本身负责“显示歌词”，歌词数据需要由支持歌词接口的音乐播放器或相关插件提供。

歌词获取依赖项目：

* [SuperLyricApi](https://github.com/HChenX/SuperLyricApi)

如果安装后状态栏没有歌词，请先确认当前音乐软件或歌词插件是否支持 SuperLyricApi，否则模块无法获得歌词内容。

## 使用要求

* Android 设备
* 已安装并启用 LSPosed
* 已在 LSPosed 中勾选本模块作用域
* 音乐软件或歌词插件需要支持 SuperLyricApi

## 下载

* [正式版 Releases](https://github.com/1770968958/StatusBarLyric_Next/releases)

## 安装方式

1. 下载 APK 并安装。
2. 在 LSPosed 中启用模块。
3. 勾选 SystemUI 相关作用域。
4. 重启 SystemUI 或重启手机。
5. 打开模块应用，根据需要调整歌词样式。
6. 播放音乐并确认歌词来源支持 SuperLyricApi。

## 说明

StatusBarLyric_Next 是个人维护版本，目标是尽量延续原项目体验，同时进行兼容性、性能和代码维护更新。

不同系统、ROM、SystemUI 修改程度不同，显示效果和兼容性可能存在差异。

## 相关链接

* 原仓库：[577fkj/StatusBarLyric](https://github.com/577fkj/StatusBarLyric)
* 歌词 API：[HChenX/SuperLyricApi](https://github.com/HChenX/SuperLyricApi)
* 许可证：[GNU General Public License v3.0](LICENSE)
* EULA：[doc/EULA.md](doc/EULA.md)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=1770968958/StatusBarLyric_Next\&type=Date)](https://star-history.com/#1770968958/StatusBarLyric_Next&Date)
