package work.foofish.apinspector.navigation

import androidx.navigation3.runtime.NavKey
import work.foofish.apinspector.spec.SpecId

// 返回栈始终形如 [首页, API 文档?, 接口文档?]：列表详情布局据此把相邻的列表与详情放进同一画面。

/** 打开（或切换到）一份 API 文档，丢弃之前打开的文档与接口。 */
fun MutableList<NavKey>.openSpec(id: SpecId) {
    retainAll { it is HomeKey }
    add(SpecKey(id))
}

/** 打开接口文档；已有打开的接口文档时替换它，而不是叠加。 */
fun MutableList<NavKey>.openOperation(key: OperationDocKey) {
    if (lastOrNull() is OperationDocKey) removeAt(lastIndex)
    add(key)
}

/** 当前打开的 API 文档。 */
val List<NavKey>.currentSpec: SpecKey? get() = filterIsInstance<SpecKey>().lastOrNull()

/** 当前在详情中打开的接口文档。 */
val List<NavKey>.currentOperation: OperationDocKey? get() = lastOrNull() as? OperationDocKey
