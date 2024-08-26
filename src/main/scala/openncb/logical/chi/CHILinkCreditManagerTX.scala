package cn.rismd.openncb.logical.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.CHIConstants._
import chisel3.util.log2Up
import chisel3.util.RegEnable


/* 
* CHI Link Credit Manager for TX channels. 
* 
* * Supported Link-Layer states: STOP, ACT, RUN, DEACT.
* 
* @param maxCount   Specify the maximum grantable link credit count. By default,
*                   {@see cn.rismd.openncb.chi.CHIConstants#CHI_MAX_REASONABLE_LINK_CREIDT_COUNT}
*/
class CHILinkCreditManagerTX(val paramMaxCount      : Int   = CHI_MAX_REASONABLE_LINK_CREDIT_COUNT)
        extends AbstractCHILinkCreditManager {

    // local parameters
    protected def paramLinkCreditCounterWidth: Int = log2Up(paramMaxCount + 1)


    // variable checks
    require(paramMaxCount > 0, s"maxCount > 0: maxCount = ${paramMaxCount}")

    require(paramMaxCount <= CHI_MAX_REASONABLE_LINK_CREDIT_COUNT,
        s"max maximum link credit count is ${CHI_MAX_REASONABLE_LINK_CREDIT_COUNT}, but ${paramMaxCount} configured")


    /*
    * Module I/O:
    *
    * @io input     linkState               : Link-layer State,
    *                                         directly comes from LinkActiveManager in general.
    * 
    * @io output    linkCreditCount         : Granted Link Credit Count, width depends on 'maxCount'.
    * @io output    linkCreditAvailable     : Granted Link Credit Available.
    * @io input     linkCreditConsume       : Link Credit Consume, consume one granted link credit 
    *                                         when asserted for sending a normal flit.
    * @io input     linkCreditReturn        : Link Credit Return, return one granted link credit
    *                                         when asserted for sending a credit return flit.
    * 
    * @io input     lcrdv                   : Link Credit Valid, connects to TX*LCRDV.
    */
    val io = IO(new Bundle {
        // implementation local link-layer state signals
        val linkState               = Input(CHILinkActiveBundle())

        // implementation local credit signals
        val linkCreditCount         = Output(UInt(paramLinkCreditCounterWidth.W))
        val linkCreditAvailable     = Output(Bool())

        val linkCreditConsume       = Input(Bool())
        val linkCreditReturn        = Input(Bool())

        // CHI link-layer signals
        val lcrdv                   = Input(Bool())

        // debug port
        val debug                   = new DebugPort
    })


    // input register of Link Credit Valid
    protected val regLcrdv  = RegNext(next = io.lcrdv, init = false.B)

    // Link Credit counter
    protected val regLinkCreditCounter  = RegInit(init = 0.U(paramLinkCreditCounterWidth.W))

    when (regLcrdv && !io.linkCreditConsume && !io.linkCreditReturn) {
        regLinkCreditCounter := regLinkCreditCounter + 1.U
    }.elsewhen (!regLcrdv && (io.linkCreditConsume || io.linkCreditReturn)) {
        regLinkCreditCounter := regLinkCreditCounter - 1.U
    }


    // module output
    io.linkCreditCount      := regLinkCreditCounter
    io.linkCreditAvailable  := regLinkCreditCounter =/= 0.U


    // assertions & debugs
    /*
    * Port I/O: Debug 
    */
    class DebugPort extends Bundle {
        val LinkActiveStateNotOneHot            = Output(Bool())
        val LinkCreditConsumeOutOfRun           = Output(Bool())
        val LinkCreditReturnOutOfDeactivate     = Output(Bool())
        val LinkCreditValidWhenLinkStop         = Output(Bool())
        val LinkCreditOverflow                  = Output(Bool())
        val LinkCreditUnderflow                 = Output(Bool())
    }

    /*
    * @assertion LinkActiveStateNotOneHot
    *   The states from Link Active must be one-hot. 
    */
    private val debugLogicLinkactivePopcnt = Wire(UInt(3.W))
    debugLogicLinkactivePopcnt := io.linkState.stop.asUInt + io.linkState.activate.asUInt + io.linkState.run.asUInt + io.linkState.stop.asUInt
    io.debug.LinkActiveStateNotOneHot := debugLogicLinkactivePopcnt =/= 1.U
    assert(!io.debug.LinkActiveStateNotOneHot,
        "linkactive state must be one-hot")

    /* 
    * @assertion LinkCreditConsumeOutOfRun
    *   The consuming of a Link Credit must only occur in RUN state. 
    */
    io.debug.LinkCreditConsumeOutOfRun := !io.linkState.run && io.linkCreditConsume
    assert(!io.debug.LinkCreditConsumeOutOfRun,
        "link credit consume out of RUN state")

    /* 
    * @assertion LinkCreditReturnOutOfDeactivate
    *   The returning of a Link Credit must only occur in DEACTIVATE state.
    */
    io.debug.LinkCreditReturnOutOfDeactivate := !io.linkState.deactivate && io.linkCreditReturn
    assert(!io.debug.LinkCreditReturnOutOfDeactivate,
        "link credit return out of DEACTIVATE state")
    
    /*
    * @assertion LinkCreditValidWhenLinkStop
    *   The 'lcrdv' was not allowed to be asserted before Link ACTIVATE (In STOP state).
    */
    io.debug.LinkCreditValidWhenLinkStop := io.linkState.stop && io.lcrdv
    assert(!io.debug.LinkCreditValidWhenLinkStop,
        "unexpected 'lcrdv' during link STOP")

    /*
    * @assertion LinkCreditOverflow
    *   The 'lcrdv' was not allowed to be asserted when the Link Credit received exceeded 
    *   the maximum number.
    */
    io.debug.LinkCreditOverflow := regLinkCreditCounter === paramMaxCount.U && io.lcrdv
    assert(!io.debug.LinkCreditOverflow,
        "link credit overflow")

    /*
    * @assertion LinkCreditUnderflow
    *   The 'linkCreditConsume' was not allowed to be asserted when there was no 
    *   Link Credit available in the current cycle.
    */
    io.debug.LinkCreditUnderflow := regLinkCreditCounter === 0.U && io.linkCreditConsume
    assert(!io.debug.LinkCreditUnderflow,
        "link credit underflow")
}
