package work.foofish.apinspector.network

import javax.inject.Qualifier

/** 发送请求时自动附加的 `User-Agent` 值。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserAgent
