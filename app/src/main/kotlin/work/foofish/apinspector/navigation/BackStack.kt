package work.foofish.apinspector.navigation

import androidx.navigation3.runtime.NavKey
import work.foofish.apinspector.spec.SpecId

// 返回栈以 [首页, API 文档?] 开头，其后是在该 API 文档中打开的页面。列表详情布局把相邻的
// "列表"（接口列表、请求记录、环境列表）与"详情"（接口详情、记录详情、环境编辑）放进同一画面。

/** 打开（或切换到）一份 API 文档，丢弃之前打开的文档及其页面。 */
fun MutableList<NavKey>.openSpec(id: SpecId) {
    retainAll { it is HomeKey }
    add(SpecKey(id))
}

/** 从接口列表打开接口详情：回到接口列表之上，替换已打开的详情而不是叠加。 */
fun MutableList<NavKey>.openOperation(key: OperationPageKey) {
    val spec = indexOfLast { it is SpecKey }
    if (spec >= 0) subList(spec + 1, size).clear()
    add(key)
}

/** 打开请求记录列表；已在请求记录列表上时替换它（例如换一个筛选）。 */
fun MutableList<NavKey>.openHistory(key: HistoryKey) {
    replaceTrailing<HistoryEntryKey>()
    replaceTrailing<HistoryKey>()
    add(key)
}

/** 打开记录详情，替换已打开的记录详情。 */
fun MutableList<NavKey>.openHistoryEntry(key: HistoryEntryKey) {
    replaceTrailing<HistoryEntryKey>()
    add(key)
}

fun MutableList<NavKey>.openEnvironments(key: EnvironmentsKey) {
    replaceTrailing<EnvironmentKey>()
    replaceTrailing<EnvironmentsKey>()
    add(key)
}

/** 打开环境编辑，替换已打开的环境编辑。 */
fun MutableList<NavKey>.openEnvironment(key: EnvironmentKey) {
    replaceTrailing<EnvironmentKey>()
    add(key)
}

/** 打开临时请求列表，丢弃之前打开的 API 文档及其页面。 */
fun MutableList<NavKey>.openScratch() {
    retainAll { it is HomeKey }
    add(ScratchKey)
}

/** 从接口列表或临时请求列表打开自定义请求：回到列表之上，替换已打开的详情。 */
fun MutableList<NavKey>.openCustomRequest(key: CustomRequestKey) {
    val list = indexOfLast { it is SpecKey || it is ScratchKey }
    if (list >= 0) subList(list + 1, size).clear()
    add(key)
}

fun MutableList<NavKey>.openSettings() {
    if (lastOrNull() != SettingsKey) add(SettingsKey)
}

/** 当前打开的 API 文档。 */
val List<NavKey>.currentSpec: SpecKey? get() = filterIsInstance<SpecKey>().lastOrNull()

/** 当前在详情中打开的接口。 */
val List<NavKey>.currentOperation: OperationPageKey? get() = lastOrNull() as? OperationPageKey

/** 当前在详情中打开的自定义请求。 */
val List<NavKey>.currentCustomRequest: CustomRequestKey? get() = lastOrNull() as? CustomRequestKey

private inline fun <reified T : NavKey> MutableList<NavKey>.replaceTrailing() {
    if (lastOrNull() is T) removeAt(lastIndex)
}
