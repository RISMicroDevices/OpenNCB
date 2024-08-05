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
*                   {@see cn.rismd.openncb.chi.CHIConstants#CHI_MAX_REASONABLE_LINK_CREDIT_COUNT}
*/
class CHILinkCreditManagerTX(val maxCount           : Int   = CHI_MAX_REASONABLE_LINK_CREDIT_COUNT)
        extends AbstractCHILinkCreditManager {

    // local parameters
    protected def linkCreditCounterWidth: Int = log2Up(maxCount + 1)


    // variable checks
    require(maxCount > 0, s"maxCount > 0: maxCount = ${maxCount}")

    require(maxCount <= CHI_MAX_REASONABLE_LINK_CREDIT_COUNT,
        s"max maximum link credit count is ${CHI_MAX_REASONABLE_LINK_CREDIT_COUNT}, but ${maxCount} configured")


    /*
    * Module I/O:
    *
    * @io input     link_state              : Link-layer State,
    *                                         directly comes from LinkActiveManager in general.
    * 
    * @io output    link_credit_count       : Granted Link Credit Count, width depends on 'maxCount'.
    * @io output    link_credit_available   : Granted Link Credit Available.
    * @io input     link_credit_consume     : Link Credit Consume, consume one granted link credit 
    *                                         when asserted for sending a normal flit.
    * @io input     link_credit_return      : Link Credit Return, return one granted link credit
    *                                         when asserted for sending a credit return flit.
    * 
    * @io input     lcrdv                   : Link Credit Valid, connects to TX*LCRDV.
    */
    val io = IO(new Bundle {
        // implementation local link-layer state signals
        val link_state              = Input(CHILinkActiveBundle())

        // implementation local credit signals
        val link_credit_count       = Output(UInt(linkCreditCounterWidth.W))
        val link_credit_available   = Output(Bool())

        val link_credit_consume     = Input(Bool())
        val link_credit_return      = Input(Bool())

        // CHI link-layer signals
        val lcrdv                   = Input(Bool())
    })


    // input register of Link Credit Valid
    protected val lcrdv_R   = RegNext(next = io.lcrdv, init = false.B)

    // Link Credit counter
    protected val link_credit_counter_R = RegInit(init = 0.U(linkCreditCounterWidth.W))

    when (lcrdv_R && !io.link_credit_consume && !io.link_credit_return) {
        link_credit_counter_R := link_credit_counter_R + 1.U
    }.elsewhen (!lcrdv_R && (io.link_credit_consume || io.link_credit_return)) {
        link_credit_counter_R := link_credit_counter_R - 1.U
    }


    // module output
    io.link_credit_count        := link_credit_counter_R
    io.link_credit_available    := link_credit_counter_R =/= 0.U


    // assertions
    /*
    * @assertion LinkActiveStateOneHot
    *   The states from Link Active must be one-hot. 
    */
    private val linkactive_popcnt = Wire(UInt(3.W))
    linkactive_popcnt := io.link_state.stop.asUInt + io.link_state.activate.asUInt + io.link_state.run.asUInt + io.link_state.stop.asUInt
    assert(!reset.asBool || linkactive_popcnt === 1.U,
        "linkactive state must be one-hot")

    /* 
    * @assertion LinkCreditConsumeOutOfRun
    *   The consuming of a Link Credit must only occur in RUN state. 
    */
    assert(io.link_state.run || (!io.link_state.run && !io.link_credit_consume),
        "link credit consume out of RUN state")

    /* 
    * @assertion LinkCreditReturnOutOfDeactivate
    *   The returning of a Link Credit must only occur in DEACTIVATE state.
    */
    assert(io.link_state.deactivate || (!io.link_state.deactivate && !io.link_credit_return),
        "link credit return out of DEACTIVATE state")

    /*
    * @assertion LinkCreditValidOnReset
    *   The 'lcrdv' was now allowed to be asserted during reset.
    */
    assert(!reset.asBool || (reset.asBool && !io.lcrdv), 
        "unexpected 'lcrdv' during reset")

    /*
    * @assertion LinkCreditValidWhenLinkStop
    *   The 'lcrdv' was not allowed to be asserted before Link ACTIVATE (In STOP state).
    */
    assert(!io.link_state.stop || (io.link_state.stop && !io.lcrdv),
        "unexpected 'lcrdv' during link STOP")

    /*
    * @assertion LinkCreditOverflow
    *   The 'lcrdv' was not allowed to be asserted when the Link Credit received exceeded 
    *   the maximum number.
    */
    assert(link_credit_counter_R =/= maxCount.U || ((link_credit_counter_R === maxCount.U) && !io.lcrdv),
        "link credit overflow")

    /*
    * @assertion LinkCreditUnderflow
    *   The 'link_credit_consume' was not allowed to be asserted when there was no 
    *   Link Credit available in the current cycle.
    */
    assert(io.link_credit_available || (!io.link_credit_available && !io.link_credit_consume),
        "link credit underflow")
}
