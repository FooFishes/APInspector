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
