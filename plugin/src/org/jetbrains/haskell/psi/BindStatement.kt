package org.jetbrains.haskell.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil

/**
 * Created by atsky on 11/21/14.
 */
public class BindStatement(node : ASTNode) : Statement(node) {

    fun getExpressions() : QNameExpression? {
        val expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, javaClass<QNameExpression>())
        return if (expressions.isEmpty()) null else expressions[0]
    }

    fun getQVar() : QVar? =
        getExpressions()?.getQVar()
}