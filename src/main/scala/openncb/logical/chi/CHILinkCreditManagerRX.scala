package cn.rismd.openncb.logical.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.CHIConstants._
import chisel3.util.log2Up
import chisel3.util.RegEnable
import cn.rismd.openncb.debug.DebugBundle
import cn.rismd.openncb.debug.DebugSignal


/* 
* CHI Link Credit Manager for RX channels.
* 
* * Supported Link-Layer states: STOP, ACT, RUN, DEACT.
* 
* @param paramMaxCount  Specify the maximum grantable link credit count, By default,
*                       {@see cn.rismd.openncb.chi.CHIConstants#CHI_MAX_REASONABLE_LINK_CREDIT_COUNT}
*                       This parameter might not be useful when {@code paramEnableMonitor} was set to 
*                       {@value false}. In general, this value was used by assertion monitors on
*                       RX sides, which only send Link Credits. And the checking logic was natural
*                       to be put in receiver logics. 
* 
* @param paramInitialCount  Specify the initial link credit count that would be sent after reset.
* 
* @param paramCycleBeforeSend   Specify the interval cycle count after Link Active before sending 
*                               the first Link Credit. The supported max value is 255.
* 
* @param paramEnableMonitor Specify whether the Link Credit assertion monitor was enabled on the
*                           sending side. 
*/
class CHILinkCreditManagerRX(val paramMaxCount          : Int       = CHI_MAX_REASONABLE_LINK_CREDIT_COUNT,
                             val paramInitialCount      : Int       = CHI_MAX_REASONABLE_LINK_CREDIT_COUNT,
                             val paramCycleBeforeSend   : Int       = 100,
                             val paramEnableMonitor     : Boolean   = false)
        extends AbstractCHILinkCreditManager {

    // local parameters
    protected def paramLinkCreditCounterWidth   : Int   = log2Up(paramMaxCount + 1)

    protected def parmaMaxCycleBeforeSend       : Int   = 255

    protected def paramMaxCycleBeforeSendWidth  : Int   = log2Up(parmaMaxCycleBeforeSend)


    // variable checks
    require(paramMaxCount           >  0, s"paramMaxCount > 0: paramMaxCount = ${paramMaxCount}")
    require(paramInitialCount       >  0, s"paramInitialCount > 0: paramInitialCount = ${paramInitialCount}")
    require(paramCycleBeforeSend    >= 0, s"paramCycleBeforeSend >= 0: paramCycleBeforeSend = ${paramCycleBeforeSend}")

    require(paramCycleBeforeSend <= parmaMaxCycleBeforeSend,
        s"maximum cycle before send is ${parmaMaxCycleBeforeSend}, but ${paramCycleBeforeSend} configured")

    require(paramMaxCount <= CHI_MAX_REASONABLE_LINK_CREDIT_COUNT,
        s"max maximum link credit count is ${CHI_MAX_REASONABLE_LINK_CREDIT_COUNT}, but ${paramMaxCount} configured")

    require(paramInitialCount <= paramMaxCount, 
        s"maximum link credit count is ${paramMaxCount}, but ${paramInitialCount} initial credit(s) configured")


    /*
    * Module I/O:
    * 
    * @io input     linkState               : Link-layer State, 
    *                                         directly comes from LinkActiveManager in general.
    * 
    * @io input     linkCreditProvide       : Provide Link Credit, this was allowed to be asserted 
    *                                         before 'linkCreditReady', but credits could only be
    *                                         accepted when 'linkCreditReady' asserted.
    * @io output    linkCreditReady         : Link Credit send ready.
    * 
    * @io input     monitorCreditConsume    : Link Credit Consume for monitor, this was only useful
    *                                         when 'paramEnableMonitor' was set to {@value true}.
    *                                         In general, connects to RX*FLITV.
    *                                         Otherwise, tie it to {@value zero} when unused.
    * @io input     monitorCreditReturn     : Link Credit Return for monitor.
    *                                         For connection {@see #monitorCreditConsume}.
    *
    * @io output    lcrdv                   : Link Credit Valid, connects to RX*LCRDV
    */
    val io = IO(new Bundle {
        // implementation local link-layer state signals
        val linkState               = Input(CHILinkActiveBundle())

        // implementation local credit signals
        val linkCreditProvide       = Input(Bool())
        val linkCreditReady         = Output(Bool())

        // monitor local signals
        val monitorCreditReturn     = Input(Bool())
        val monitorCreditConsume    = Input(Bool())

        // CHI link-layer signals
        val lcrdv                   = Output(Bool())

        // debug port
        @DebugSignal
        val debug                   = new DebugPort
    })


    // Link Credit interval cycle counter
    protected val regInitialCycleCounter    = RegInit(0.U(paramMaxCycleBeforeSendWidth.W))
    protected val logicInitialCycleEnd      = regInitialCycleCounter === paramCycleBeforeSend.U
    
    when (io.linkState.run && !logicInitialCycleEnd) {
        regInitialCycleCounter := regInitialCycleCounter + 1.U
    }.elsewhen (io.linkState.deactivate) {
        regInitialCycleCounter := 0.U
    }

    // Link Credit initial counter
    protected val regInitialCreditCounter   = RegInit(0.U(paramLinkCreditCounterWidth.W))
    protected val logicInitialCreditClear   = regInitialCreditCounter === paramInitialCount.U

    when (io.linkState.run && logicInitialCycleEnd && !logicInitialCreditClear) {
        regInitialCreditCounter     := regInitialCreditCounter + 1.U
    }.elsewhen (io.linkState.deactivate) {
        regInitialCreditCounter     := 0.U
    }

    //
    protected val logicInitialCreditDone    = logicInitialCycleEnd && logicInitialCreditClear

    // output register of Link Credit Valid
    protected val regLcrdv  = RegNext(
        init = 0.B, 
        next = Mux(logicInitialCreditDone, io.linkCreditProvide, logicInitialCycleEnd))


    // module output
    io.linkCreditReady      := logicInitialCreditDone
    io.lcrdv                := regLcrdv


    // monitor logic
    protected val debugRegMonitorCreditCounter  = RegInit(0.U(paramLinkCreditCounterWidth.W))

    if (paramEnableMonitor) {
        when (io.lcrdv && !io.monitorCreditConsume && !io.monitorCreditReturn) {
            debugRegMonitorCreditCounter := debugRegMonitorCreditCounter + 1.U
        }.elsewhen (!io.lcrdv && (io.monitorCreditConsume || io.monitorCreditReturn)) {
            debugRegMonitorCreditCounter := debugRegMonitorCreditCounter - 1.U
        }
    }

    // assertions & debugs
    /*
    * Port I/O: Debug 
    */
    class DebugPort extends DebugBundle {
        val LinkActiveStateNotOneHot            = Output(Bool())
        val LinkCreditConsumeOutOfRun           = Output(Bool())
        val LinkCreditReturnOutOfDeactivate     = Output(Bool())
        val LinkCreditOverflow                  = Output(Bool())
        val LinkCreditUnderflow                 = Output(Bool())
    }

    /*
    * @assertion LinkActiveStateNotOneHot
    *   The states from Link Active must be one-hot. 
    */
    private val debugLogicLinkactivePopcnt  = Wire(UInt(3.W))
    debugLogicLinkactivePopcnt := io.linkState.stop.asUInt + io.linkState.activate.asUInt + io.linkState.run.asUInt + io.linkState.stop.asUInt
    io.debug.LinkActiveStateNotOneHot := debugLogicLinkactivePopcnt =/= 1.U
    assert(!io.debug.LinkActiveStateNotOneHot,
        "linkactive state must be one-hot")

    /* 
    * @assertion LinkCreditConsumeOutOfRun
    *   The consuming of a Link Credit must only occur in RUN state. 
    */
    io.debug.LinkCreditConsumeOutOfRun := paramEnableMonitor.B && (!io.linkState.run && io.monitorCreditConsume)
    assert(!io.debug.LinkCreditConsumeOutOfRun,
        "link credit consume out of RUN state")

    /* 
    * @assertion LinkCreditReturnOutOfDeactivate
    *   The returning of a Link Credit must only occur in DEACTIVATE state.
    */
    io.debug.LinkCreditReturnOutOfDeactivate := paramEnableMonitor.B && (!io.linkState.deactivate && io.monitorCreditReturn)
    assert(!io.debug.LinkCreditReturnOutOfDeactivate,
        "link credit return out of DEACTIVATE state")
    
    /*
    * @assetion LinkCreditOverflow
    *   The 'lcrdv' was not allowed to be asserted when the Link Credit received exceeded 
    *   the maximum number.
    */
    io.debug.LinkCreditOverflow := paramEnableMonitor.B && (debugRegMonitorCreditCounter === paramMaxCount.U && io.lcrdv)
    assert(!io.debug.LinkCreditOverflow,
        "link credit overflow")

    /*
    * @assertion LinkCreditUnderflow
    *   The 'monitorCreditConsume' was not allowed to be asserted when there was no 
    *   Link Credit available in the current cycle.
    */
    io.debug.LinkCreditUnderflow := paramEnableMonitor.B && debugRegMonitorCreditCounter === 0.U && io.monitorCreditConsume
    assert(!io.debug.LinkCreditUnderflow,
        "link credit underflow")


    /*
    * Link Credit Provide Buffer
    * 
    * * Buffer for Link Credit Provide signal eliminating 'linkCreditReady'.
    */
    class ProvideBuffer extends Module {

        /*
        * Module I/O 
        * 
        * @io input     linkCreditProvide       : Provide Link Credit.
        * 
        * @io output    outLinkCreditProvide    : Provide Link Credit, to manager.
        * @io input     outLinkCreditReady      : Provide Link ready, to manager.
        */
        val io = IO(new Bundle {
            // link credit provide input
            val linkCreditProvide       = Input(Bool())

            // link credit provide output to manager
            val outLinkCreditProvide    = Output(Bool())
            val outLinkCreditReady      = Input(Bool())

            // debug port
            @DebugSignal
            val debug                   = new DebugPort
        })


        // increase & decrease logic
        protected val logicBufferPushReady  = io.linkCreditProvide
        protected val logicBufferPopReady   = io.outLinkCreditReady

        // Link Credit Buffering Counter
        protected val regBufferedCreditCounter    = RegInit(0.U(paramLinkCreditCounterWidth.W))

        protected val logicBufferEmpty      = regBufferedCreditCounter === 0.U

        protected val logicBufferIncrease   = ( logicBufferPushReady & !logicBufferPopReady)
        protected val logicBufferDecrease   = (!logicBufferPushReady &  logicBufferPopReady) && !logicBufferEmpty

        when (logicBufferIncrease) {
            regBufferedCreditCounter    := regBufferedCreditCounter + 1.U
        }.elsewhen (logicBufferDecrease) {
            regBufferedCreditCounter    := regBufferedCreditCounter - 1.U
        }

        // module output
        io.outLinkCreditProvide := !logicBufferEmpty


        // assertions & debugs
        /*
        * Port I/O: Debug 
        */
        class DebugPort extends DebugBundle {
            val LinkCreditBufferOverflow    = Output(Bool())
        }

        /*
        * @assertion LinkCreditBufferOverflow
        *   The 'linkCreditProvide' was not allowed to be asserted when the Link Credit provided exceeded
        *   the maximum number.
        */
        io.debug.LinkCreditBufferOverflow := logicBufferIncrease && regBufferedCreditCounter === paramMaxCount.U
        assert(!io.debug.LinkCreditBufferOverflow,
            "link credit buffer overflow")


        // utility functions
        def attach(uManager: CHILinkCreditManagerRX)    = {
            uManager.io.linkCreditProvide   := io.outLinkCreditProvide
            io.outLinkCreditReady           := uManager.io.linkCreditReady
            this
        }

        def apply(uManager: CHILinkCreditManagerRX)     = attach(uManager)
    }


    // utiltiy functions
    /*
    * Create a Link Credit ProvideBuffer module instance and connect to this manager.
    * 
    * @return ProvideBuffer instance 
    */
    def attachLinkCreditProvideBuffer(): ProvideBuffer  = Module(new ProvideBuffer).attach(this)

    /*
    * Create a Link Credit ProvideBuffer module instance and connect to this manager,
    * then extract the buffered 'linkCreditProvide' input port.
    * 
    * @return Wire of ProvideBuffer.linkCreditProvide 
    */
    def bufferedLinkCreditProvide(): Bool   = attachLinkCreditProvideBuffer().io.linkCreditProvide
}
