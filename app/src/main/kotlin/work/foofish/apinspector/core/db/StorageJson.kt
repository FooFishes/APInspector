package work.foofish.apinspector.core.db

import kotlinx.serialization.json.Json

/**
 * 以 JSON 文本存入数据库的结构化列（当前参数、凭证、实际请求等）使用的编码配置：
 * 读取时忽略未知字段、缺失字段取默认值，以便模型演进时旧数据仍可读取。
 */
val StorageJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
