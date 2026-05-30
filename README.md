# 校园生活百事通智能问答知识库系统

第一周项目骨架，用于完成环境验证、后端基础接口和后续页面/数据库开发。

## 目录结构

```text
campus-qa/
  backend/    Spring Boot 后端项目
  frontend/   静态页面原型
  database/   数据库建表 SQL 和测试数据
  docs/       项目文档和截图
```

## 本地 Maven

Maven 已安装在：

```powershell
E:\code\shixun\.tools\apache-maven-3.9.16
```

后端运行命令：

```powershell
cd E:\code\shixun\campus-qa\backend
..\..\.tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
```

如果上面的相对路径不方便，也可以使用绝对路径：

```powershell
E:\code\shixun\.tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
```

## 第一周后端接口

```text
GET  /api/hello
POST /api/qa/search
```

`/api/qa/search` 当前返回模拟 FAQ 答案，用于证明项目骨架和接口流程可运行。

## 前端原型

静态页面可直接用浏览器打开：

```text
E:\code\shixun\campus-qa\frontend\index.html
```

页面包括首页、查询结果、用户贡献、管理员登录和后台管理原型。

## 数据库脚本

```text
database/schema.sql
database/sample-data.sql
```
