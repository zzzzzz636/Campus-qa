# 校园生活百事通智能问答知识库系统

第二周 V1 版本已完成 Spring Boot 后端、SQLite 数据库接入、基础 FAQ 查询和静态前端页面联调。

详细运行说明见：

```text
docs/系统V1运行说明.md
```

## 当前已实现

- 后端连接 SQLite 数据库。
- FAQ 列表查询：`GET /api/faq/list`
- 问答检索：`GET /api/qa/search?question=图书馆`
- 查询日志记录。
- 用户贡献提交：`POST /api/contribution/add`
- 前端页面通过 Spring Boot 静态资源同源访问。

## 当前未实现

- 管理员真实登录鉴权。
- FAQ 新增、修改、删除完整 CRUD。
- 贡献审核通过/拒绝入库。
- 知识导入。
- 大模型问答。
- 官网爬虫。

## 启动

```powershell
cd E:\code\shixun\campus-qa\backend
..\..\.tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
```

启动后访问：

```text
http://localhost:8080/
http://localhost:8080/admin.html
http://localhost:8080/result.html?q=图书馆
http://localhost:8080/api/faq/list
```

## 目录结构

```text
campus-qa/
  backend/    Spring Boot 后端项目和同源静态页面
  frontend/   前端静态页面源文件
  database/   SQLite 建表 SQL 和测试数据 SQL
  docs/       项目说明文档
```

## 数据库

数据库使用 SQLite：

```text
database/campusqa.db
```

初始化脚本：

```text
database/schema.sql
database/sample-data.sql
```

初始化命令：

```powershell
python database\init_sqlite.py
```

`campusqa.db` 是本地生成文件，已通过 `.gitignore` 忽略，不建议提交。

当前 `spring.sql.init.mode` 设置为 `never`，后端启动时不会重建数据库，运行中的用户贡献、查询日志和浏览次数可以保留。
