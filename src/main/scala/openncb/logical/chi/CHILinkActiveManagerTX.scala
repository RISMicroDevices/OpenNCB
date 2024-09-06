package cc.xiangshan.openncb.logical.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters


/*
* CHI Link Active Manager for TX channels. 
*/
class CHILinkActiveManagerTX extends AbstractCHILinkActiveManager {

    /*
    * Module I/O:
    *
    * @io output    linkState           : Link-layer state.
    * 
    * @io output    goActivateValid     : Request to go to state ACTIVATE.
    * @io input     goActivateReady     : Ready to go to state ACTIVATE.
    * 
    * @io output    goDeactivateValid   : Request to go to state DEACTIVATE.
    * @io input     goDeactivateReady   : Ready to go to state DEACTIVATE.
    * 
    * @io input     linkactiveReq       : LINKACTIVEREQ.
    * @io output    linkactiveAck       : LINKACTIVEACK.
    */
    val io = IO(new Bundle {
        // implementation local link-layer state signals
        val linkState               = Output(CHILinkActiveBundle())

        // implementation local link-layer state transition signals
        val goActivateValid         = Output(Bool())
        val goActivateReady         = Input(Bool())

        val goDeactivateValid       = Output(Bool())
        val goDeactivateReady       = Input(Bool())

        // CHI link-layer signals
        val linkactiveReq           = Output(Bool())
        val linkactiveAck           = Input(Bool())
    })


    // output register of LINKACTIVEREQ
    protected val regLinkactiveReq  = RegInit(init = false.B)

    // input register of LINKACTIVEACK
    protected val regLinkactiveAck  = RegNext(next = io.linkactiveAck, init = false.B)

    // 
    protected val logicToGoActivate     = !regLinkactiveReq && !regLinkactiveAck
    protected val logicToGoDeactivate   =  regLinkactiveReq &&  regLinkactiveAck

    when (logicToGoActivate && io.goActivateReady) {
        regLinkactiveReq := true.B
    }.elsewhen (logicToGoDeactivate && io.goDeactivateReady) {
        regLinkactiveReq := false.B
    }


    // link-layer state output
    io.linkState.from(regLinkactiveReq, regLinkactiveAck)

    // link-layer state transition output
    io.goActivateValid      := logicToGoActivate
    io.goDeactivateValid    := logicToGoDeactivate


    // CHI link-layer output
    io.linkactiveReq   := regLinkactiveReq
}
