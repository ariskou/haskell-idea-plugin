package org.jetbrains.haskell.debugger

import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.XSourcePosition
import com.intellij.execution.process.ProcessHandler

/**
 * Created by vlad on 7/10/14.
 */

public class GHCiDebugProcess(session: XDebugSession,
                              ghciProcess: Process,
                              val executionConsole: ExecutionConsole,
                              val myProcessHandler: ProcessHandler) : XDebugProcess(session) {

    private val debuggerEditorsProvider: XDebuggerEditorsProvider
    private val debugger: ProcessDebugger

    {
        debuggerEditorsProvider = HaskellDebuggerEditorsProvider()
        debugger = GHCiDebugger(this, ghciProcess)
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return debuggerEditorsProvider
    }

    override fun doGetProcessHandler(): ProcessHandler? {
        return myProcessHandler
    }

    override fun startStepOver() {
        throw UnsupportedOperationException()
    }

    override fun startStepInto() {
        throw UnsupportedOperationException()
    }

    override fun startStepOut() {
        throw UnsupportedOperationException()
    }

    override fun stop() {
    }

    override fun resume() {
        throw UnsupportedOperationException()
    }

    override fun runToPosition(position: XSourcePosition) {
        throw UnsupportedOperationException()
    }


}