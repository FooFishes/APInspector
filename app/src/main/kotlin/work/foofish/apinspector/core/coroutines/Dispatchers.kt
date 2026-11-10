package work.foofish.apinspector.core.coroutines

import javax.inject.Qualifier

/** 用于阻塞 I/O（文件、网络）的调度器。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** 用于 CPU 密集任务（解析大型文档）的调度器。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** 与应用进程同生命周期的协程作用域：离开页面也不应中断的工作（如发送请求，PRD 6）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
