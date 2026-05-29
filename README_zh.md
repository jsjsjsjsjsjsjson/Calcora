# Calcora

一款运行于 Android 的科学计算器与计算机代数系统（CAS），基于 [Giac/Xcas](https://www-fourier.ujf-grenoble.fr/~parisse/giac.html) 引擎。

[English](README.md)

## 功能

- **符号代数** — 化简、展开、因式分解、方程求解、极限、微分、积分等
- **任意精度算术** — 精确分数、大整数、高精度浮点运算
- **2D / 3D 绘图** — 支持双指缩放、拖动平移与 3D 旋转的交互式绘图
- **CAS 终端** — 直接访问 Xcas/Giac 命令行，完整引擎功能
- **脚本编辑器** — 编写、保存、加载并运行 Xcas 脚本，支持语法高亮
- **完整帮助系统** — 可搜索的离线函数参考，含描述、相关函数与示例
- **智能补全** — 输入时自动提示函数名建议
- **Monet** — 配色跟随设备壁纸（Android 12+）
- **国际化** — 中英文界面，Giac 后端语言支持

## 界面

| 计算 | 帮助 | 脚本 | 设置 |
|------|------|------|----------|
| 表达式输入、历史滚动、模式切换（自动/精确/近似/原始）、变量面板、函数面板、查看绘图 | 可搜索的函数参考，含描述、相关命令与示例 | 带语法高亮的 Xcas 脚本编辑器，支持保存/加载与输出面板 | 语言、主题、角度单位、精度、历史记录上限等 |

## 构建

### 环境要求

- Android Studio（最新稳定版）
- NDK 30.0+
- Kotlin 2.x
- JDK 17+

### 步骤

```bash
git clone git@github.com:jsjsjsjsjsjsjson/Calcora.git
cd Calcora
./gradlew assembleDebug    # Debug 构建（native -O2）
./gradlew assembleRelease  # Release 构建（native -O3，已混淆）
```

项目将 [Giac 2.0.0](https://www-fourier.ujf-grenoble.fr/~parisse/giac.html) C++ 源码树子集置于 `giac-2.0.0/` 下，通过 `app/src/main/cpp/CMakeLists.txt` 里的 CMake 编译。仅构建 `arm64-v8a`。

## 许可证

本项目原始代码基于 MIT License 授权。

Giac/Xcas © Bernard Parisse 及贡献者，基于 GPLv3 授权。详见 `giac-2.0.0/COPYING`。

---

*by [libchara-dev](https://github.com/jsjsjsjsjsjsjson)*
