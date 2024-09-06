package cc.xiangshan.openncb.logical.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters


/*
* CHI Link Active Manager for RX channels. 
*/
class CHILinkActiveManagerRX extends AbstractCHILinkActiveManager {

    /*
    * Module I/O:
    *
    * @io output    linkState       : Link-layer state.
    * 
    * @io output    goRunValid      : Request to go to state RUN.
    * @io input     goRunReady      : Ready to go to state RUN.
    * 
    * @io output    goStopValid     : Request to go to state STOP.
    * @io input     goStopReady     : Ready to go to state STOP.
    * 
    * @io input     linkactiveReq  : LINKACTIVEREQ.
    * @io output    linkactiveAck  : LINKACTIVEACK.
    */
    val io = IO(new Bundle {
        // implementation local link-layer state signals
        val linkState               = Output(CHILinkActiveBundle())

        // implementation local link-layer state transition signals
        val goRunValid              = Output(Bool())
        val goRunReady              = Input(Bool())

        val goStopValid             = Output(Bool())
        val goStopReady             = Input(Bool())

        // CHI link-layer signals
        val linkactiveReq           = Input(Bool())
        val linkactiveAck           = Output(Bool())
    })

    
    // input register of LINKACTIVEREQ
    protected val regLinkactiveReq = RegNext(next = io.linkactiveReq, init = false.B)

    // output register of LINKACTIVEACK
    protected val regLinkactiveAck  = RegInit(init = false.B)

    // 
    protected val logicToGoRun  =  regLinkactiveReq && !regLinkactiveAck
    protected val logicToGoStop = !regLinkactiveReq &&  regLinkactiveAck

    when (logicToGoRun && io.goRunReady) {
        regLinkactiveAck := true.B
    }.elsewhen (logicToGoStop && io.goStopReady) {
        regLinkactiveAck := false.B
    }


    // link-layer state output
    io.linkState.from(regLinkactiveReq, regLinkactiveAck)

    // link-layer state transition output
    io.goRunValid     := logicToGoRun
    io.goStopValid    := logicToGoStop


    // CHI link-layer output
    io.linkactiveAck   := regLinkactiveAck
}
