package cn.rismd.openncb.logical.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters


/*
* CHI Link Active Manager for TX channels. 
*/
class CHILinkActiveManagerTX(implicit p: Parameters)
        extends AbstractCHILinkActiveManager {

    /*
    * Module I/O:
    *
    * @io output    link_state          : Link-layer state.
    * 
    * @io output    go_activate_valid   : Request to go to state ACTIVATE.
    * @io input     go_activate_ready   : Ready to go to state ACTIVATE.
    * 
    * @io output    go_deactivate_valid : Request to go to state DEACTIVATE.
    * @io input     go_deactivate_ready : Ready to go to state DEACTIVATE.
    * 
    * @io input     linkactive_req      : LINKACTIVEREQ.
    * @io output    linkactive_ack      : LINKACTIVEACK.
    */
    val io = IO(new Bundle {
        // implementation local link-layer state signals
        val link_state              = Output(CHILinkActiveBundle())

        // implementation local link-layer state transition signals
        val go_activate_valid       = Output(Bool())
        val go_activate_ready       = Input(Bool())

        val go_deactivate_valid     = Output(Bool())
        val go_deactivate_ready     = Input(Bool())

        // CHI link-layer signals
        val linkactive_req          = Output(Bool())
        val linkactive_ack          = Input(Bool())
    })


    // output register of LINKACTIVEREQ
    protected val linkactive_req_R  = RegInit(init = false.B)

    // input register of LINKACTIVEACK
    protected val linkactive_ack_R  = RegNext(next = io.linkactive_ack, init = false.B)

    // 
    protected val to_go_activate    = !linkactive_req_R && !linkactive_ack_R
    protected val to_go_deactivate  =  linkactive_req_R &&  linkactive_ack_R

    when (to_go_activate && io.go_activate_ready) {
        linkactive_req_R := true.B
    }.elsewhen (to_go_deactivate && io.go_deactivate_ready) {
        linkactive_req_R := false.B
    }


    // link-layer state output
    io.link_state.from(linkactive_req_R, linkactive_ack_R)

    // link-layer state transition output
    io.go_activate_valid    := to_go_activate
    io.go_deactivate_valid  := to_go_deactivate


    // CHI link-layer output
    io.linkactive_req   := linkactive_req_R
}
