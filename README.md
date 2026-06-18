# 全球地缘政治态势感知与分析系统

基于 GDELT 数据库的 Java 桌面分析工具，涵盖数据下载、导入、查询、可视化及地缘挖掘分析。

## 环境要求

- **JDK 17** 或更高版本
- Windows 10/11 或 Linux

## 快速启动

### 方式一：JAR 直接运行（推荐，无需编译）

双击 `run-jar.bat`，或在命令行执行：

```bash
java -jar gdelt-analyzer.jar
```

### 方式二：从源码编译运行

双击 `run.bat`，或手动执行：

```bash
javac -encoding UTF-8 -cp "lib/*" -d out src/com/gdelt/**/*.java
java -Dfile.encoding=UTF-8 -cp "out;lib/*" com.gdelt.MainApp
```

## 功能说明

| 模块 | 功能 |
|------|------|
| 🌐 爬虫下载 | 多线程下载 GDELT 数据，支持按年/月/时段筛选，失败自动重试 |
| 📥 数据导入 | ZIP 流式解压，TSV 解析，批量写入 SQLite |
| 🔍 查询检索 | 日期/国家/事件类型组合查询，双边关系专查 |
| 📊 可视化 | 世界热力图、趋势折线图、柱状图、饼图、箱线图（纯 Java2D 手绘） |
| 🔮 地缘挖掘 | 双边趋势分析、K-Means 国家聚类、冲突概率预测 |

## 项目结构

| 路径 | 说明 |
|------|------|
| `src/` | Java 源码 |
| `lib/` | 依赖库（SQLite JDBC + SLF4J） |
| `data/` | 数据目录（下载和数据库自动生成） |
| `run.bat` | 源码编译启动 |
| `run-jar.bat` | JAR 直接启动 |
| `gdelt-analyzer.jar` | 预编译可执行 JAR |

## 技术栈

Java 17 · Swing · SQLite JDBC · Java2D · 零额外依赖
