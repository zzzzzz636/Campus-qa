# 第四周 V3 离线爬虫工具说明

本工具用于课程实训中的校园官网公开资料采集，生成知识资料 CSV 文件。它只作为离线辅助工具，不参与用户实时问答流程。

## 使用范围

- 只采集公开网页。
- 不访问登录页面、统一身份认证、教务系统、个人信息页面、验证码页面。
- 不做全站递归爬取。
- 不高频请求网页。

## 文件说明

```text
tools/crawler.py        离线爬虫脚本
tools/seed_urls.txt     待采集 URL 列表
output/campus_docs.csv  生成的 CSV 文件
```

## 配置 URL

编辑 `tools/seed_urls.txt`，每行填写一个公开网页 URL。

示例：

```text
https://www.scut.edu.cn/
https://www.lib.scut.edu.cn/
```

如果需要采集华南理工大学更多资料，请优先使用学校官网、图书馆官网、后勤服务官网等公开栏目中的具体文章页。

## 运行方式

在项目根目录执行：

```powershell
python tools\crawler.py
```

脚本会读取：

```text
tools/seed_urls.txt
```

并生成：

```text
output/campus_docs.csv
```

## CSV 字段

生成文件包含以下字段：

```text
title,content,category,source_url,source_type
```

其中：

- `category` 默认是 `校园资料`
- `source_type` 固定是 `官网爬虫`

## 导入系统

当前爬虫生成的是知识资料 CSV，用于后续知识资料库导入功能。若需要临时导入 FAQ，需要先人工整理为 FAQ CSV 格式：

```text
question,answer,category,source
```

然后调用 FAQ CSV 导入接口：

```powershell
curl.exe -X POST http://localhost:8080/api/import/faq-csv -F "file=@faq-import.csv;type=text/csv"
```

## 注意事项

- 运行前确认 URL 是公开页面。
- 如果某个 URL 访问失败，程序会输出错误并继续处理下一个 URL。
- 默认每次请求间隔 1.5 秒，避免频繁访问。
- 本脚本仅使用 Python 标准库，不需要安装第三方依赖。
