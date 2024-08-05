package cn.rismd.openncb.logical.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.CHIConstants._
import chisel3.util.log2Up
import chisel3.util.RegEnable


/* 
* CHI Link Credit Manager for RX channels.
* 
* * Supported Link-Layer states: STOP, ACT, RUN, DEACT.
* 
* @param maxCount   Specify the maximum grantable link credit count, By default,
*                   {@see cn.rismd.openncb.chi.CHIConstants#CHI_MAX_REASONABLE_LINK_CREDIT_COUNT}
*                   This parameter might not be useful when {@code enableMonitor} was set to 
*                   {@value false}. In general, this value was used by assertion monitors on
*                   RX sides, which only send Link Credits. And the checking logic was natural
*                   to be put in receiver logics. 
* 
* @param cycleBeforeSend    Specify the interval cycle count after Link Active before sending 
*                           the first Link Credit. The supported max value is 255.
* 
* @param enableMonitor  Specify whether the Link Credit assertion monitor was enabled on the
*                       sending side. 
*/
class CHILinkCreditManagerRX(val maxCount           : Int       = CHI_MAX_REASONABLE_LINK_CREDIT_COUNT,
                             val initialCount       : Int       = CHI_MAX_REASONABLE_LINK_CREDIT_COUNT,
                             val cycleBeforeSend    : Int       = 100,
                             val enableMonitor      : Boolean   = false)
                            (implicit p: Parameters)
        extends AbstractCHILinkCreditManager {

    // local parameters
    protected def linkCreditCounterWidth    : Int   = log2Up(maxCount)

    protected def maxCycleBeforeSend        : Int   = 255

    protected def maxCycleBeforeSendWidth   : Int   = log2Up(maxCycleBeforeSend)


    // variable checks
    require(maxCount        >  0, s"maxCount > 0: maxCount = ${maxCount}")
    require(initialCount    >  0, s"initialCount > 0: initialCount = ${initialCount}")
    require(cycleBeforeSend >= 0, s"cycleBeforeSend >= 0: cycleBeforeSend = ${cycleBeforeSend}")

    require(cycleBeforeSend <= maxCycleBeforeSend,
        s"maximum cycle before send is ${maxCycleBeforeSend}, but ${cycleBeforeSend} configured")

    require(maxCount <= CHI_MAX_REASONABLE_LINK_CREDIT_COUNT,
        s"max maximum link credit count is ${CHI_MAX_REASONABLE_LINK_CREDIT_COUNT}, but ${maxCount} configured")

    require(initialCount <= maxCount, 
        s"maximum link credit count is ${maxCount}, but ${initialCount} initial credit(s) configured")


    /*
    * Module I/O:
    * 
    * @io input     link_active             : Link-layer Active, active when asserted,
    *                                         typically comes from LinkActiveManager.
    * 
    * @io input     link_credit_provide     : Provide Link Credit, this was allowed to be asserted 
    *                                         before 'link_credit_ready', but credits could only be
    *                                         accepted when 'link_credit_ready' asserted.
    * @io output    link_credit_ready       : Link Credit send ready.
    * 
    * @io input     monitor_credit_consume  : Link Credit Consume for monitor, this was only useful
    *                                         when 'enableMonitor' was set to {@value true}.
    *                                         In general, connects to RX*FLITV.
    *                                         Otherwise, you could leave this signal unconnected, or
    *                                         tie it to {@value zero}.
    * @io input     monitor_credit_return   : Link Credit Return for monitor.
    *                                         For connection {@see #monitor_credit_consume}.
    *
    * @io output    lcrdv                   : Link Credit Valid, connects to RX*LCRDV
    */
    val io = IO(new Bundle {
        // implementation local link-layer state signals
        val link_stop               = Input(Bool())
        val link_activate           = Input(Bool())
        val link_run                = Input(Bool())
        val link_deactivate         = Input(Bool())

        // implementation local credit signals
        val link_credit_provide     = Input(Bool())
        val link_credit_ready       = Output(Bool())

        // monitor local signals
        val monitor_credit_return   = Input(WireInit(init = false.B))
        val monitor_credit_consume  = Input(WireInit(init = false.B))

        // CHI link-layer signals
        val lcrdv                   = Output(Bool())
    })


    // Link Credit interval cycle counter
    protected val initial_cycle_counter_R   = RegInit(0.U(maxCycleBeforeSendWidth.W))
    protected val initial_cycle_end         = initial_cycle_counter_R === cycleBeforeSend.U
    
    when (io.link_run && !initial_cycle_end) {
        initial_cycle_counter_R := initial_cycle_counter_R + 1.U
    }.elsewhen (io.link_deactivate) {
        initial_cycle_counter_R := 0.U
    }

    // Link Credit initial counter
    protected val initial_credit_counter_R  = RegInit(0.U(linkCreditCounterWidth.W))
    protected val initial_credit_clear      = initial_credit_counter_R === initialCount.U

    when (io.link_run && initial_cycle_end && !initial_credit_clear) {
        initial_credit_counter_R    := initial_credit_counter_R + 1.U
    }.elsewhen (io.link_deactivate) {
        initial_credit_counter_R    := 0.U
    }

    //
    protected val initial_credit_done   = initial_cycle_end && initial_credit_clear

    // output register of Link Credit Valid
    protected val lcrdv_R   = RegNext(
        init = 0.B, 
        next = Mux(initial_credit_done, io.link_credit_provide, initial_cycle_end))


    // module output
    io.link_credit_ready    := initial_credit_done
    io.lcrdv                := lcrdv_R


    // monitor logic
    protected val monitor_credit_counter_next   = Wire(UInt(linkCreditCounterWidth.W))
    protected val monitor_credit_counter_R      = RegEnable(
        /*next   = */ monitor_credit_counter_next,
        /*init   = */ 0.U, 
        /*enable = */ enableMonitor.B)

    when (io.lcrdv && !io.monitor_credit_consume && !io.monitor_credit_return) {
        monitor_credit_counter_next := monitor_credit_counter_R + 1.U
    }.elsewhen (!io.lcrdv && (io.monitor_credit_consume || io.monitor_credit_return)) {
        monitor_credit_counter_next := monitor_credit_counter_R - 1.U
    }

    // assertions
    /*
    * @assertion LinkActiveStateOneHot
    *   The states from Link Active must be one-hot. 
    */
    private val linkactive_popcnt = Wire(UInt(3.W))
    linkactive_popcnt := io.link_stop.asUInt + io.link_activate.asUInt + io.link_run.asUInt + io.link_stop.asUInt
    assert(!reset.asBool || linkactive_popcnt === 1.U,
        "linkactive state must be one-hot")

    /* 
    * @assertion LinkCreditConsumeOutOfRun
    *   The consuming of a Link Credit must only occur in RUN state. 
    */
    assert(!enableMonitor.B || io.link_run || (!io.link_run && !io.monitor_credit_consume),
        "link credit consume out of RUN state")

    /* 
    * @assertion LinkCreditReturnOutOfDeactivate
    *   The returning of a Link Credit must only occur in DEACTIVATE state.
    */
    assert (!enableMonitor.B || io.link_deactivate || (!io.link_deactivate && !io.monitor_credit_return),
        "link credit return out of DEACTIVATE state")
    
    /*
    * @assetion LinkCreditOverflow
    *   The 'lcrdv' was not allowed to be asserted when the Link Credit received exceeded 
    *   the maximum number.
    */
    assert(!enableMonitor.B || (enableMonitor.B && monitor_credit_counter_R === maxCount.U && !io.lcrdv),
        "link credit overflow")

    /*
    * @assertion LinkCreditUnderflow
    *   The 'link_credit_consume' was not allowed to be asserted when there was no 
    *   Link Credit available in the current cycle.
    */
    assert (!enableMonitor.B || (enableMonitor.B && monitor_credit_counter_R === 0.U && !io.lcrdv),
        "link credit underflow")
}
