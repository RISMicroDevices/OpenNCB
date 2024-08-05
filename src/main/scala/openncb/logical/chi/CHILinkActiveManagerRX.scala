package cn.rismd.openncb.logical.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters


/*
* CHI Link Active Manager for RX channels. 
*/
class CHILinkActiveManagerRX(implicit p: Parameters)
        extends AbstractCHILinkActiveManager {

    /*
    * Module I/O:
    *
    * @io output    link_state      : Link-layer state.
    * 
    * @io output    go_run_valid    : Request to go to state RUN.
    * @io input     go_run_ready    : Ready to go to state RUN.
    * 
    * @io output    go_stop_valid   : Request to go to state STOP.
    * @io input     go_stop_ready   : Ready to go to state STOP.
    * 
    * @io input     linkactive_req  : LINKACTIVEREQ.
    * @io output    linkactive_ack  : LINKACTIVEACK.
    */
    val io = IO(new Bundle {
        // implementation local link-layer state signals
        val link_state              = Output(CHILinkActiveBundle())

        // implementation local link-layer state transition signals
        val go_run_valid            = Output(Bool())
        val go_run_ready            = Input(Bool())

        val go_stop_valid           = Output(Bool())
        val go_stop_ready           = Input(Bool())

        // CHI link-layer signals
        val linkactive_req          = Input(Bool())
        val linkactive_ack          = Output(Bool())
    })

    
    // input register of LINKACTIVEREQ
    protected val linkactive_req_R  = RegNext(next = io.linkactive_req, init = false.B)

    // output register of LINKACTIVEACK
    protected val linkactive_ack_R  = RegInit(init = false.B)

    // 
    protected val to_go_run     =  linkactive_req_R && !linkactive_ack_R
    protected val to_go_stop    = !linkactive_req_R &&  linkactive_ack_R

    when (to_go_run && io.go_run_ready) {
        linkactive_ack_R := true.B
    }.elsewhen (to_go_stop && io.go_stop_ready) {
        linkactive_ack_R := false.B
    }


    // link-layer state output
    io.link_state.from(linkactive_req_R, linkactive_ack_R)

    // link-layer state transition output
    io.go_run_valid     := to_go_run
    io.go_stop_valid    := to_go_stop


    // CHI link-layer output
    io.linkactive_ack   := linkactive_ack_R
}
