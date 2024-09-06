package cc.xiangshan.openncb.logical.chi

import chisel3._


/* 
* CHI Link-Layer state bundle (one-hot).
*/
class CHILinkActiveBundle extends Bundle {

    val stop            = Bool()    // STOP
    val activate        = Bool()    // ACT
    val run             = Bool()    // RUN
    val deactivate      = Bool()    // DEACT

    /*
    * Connects to CHI Link-Layer LINKACTIVE signals.
    * 
    * @param linkactive_req LINKACTIVEREQ
    * @param linkactive_ack LINKACTIVEACK
    */
    def from(linkactive_req: Bool, linkactive_ack: Bool) = {
        stop        := !linkactive_req && !linkactive_ack
        activate    :=  linkactive_req && !linkactive_ack
        run         :=  linkactive_req &&  linkactive_ack
        deactivate  := !linkactive_req &&  linkactive_ack
    }
}


object CHILinkActiveBundle {

    def apply() = new CHILinkActiveBundle

    /*
    * Connects to CHI Link-Layer LINKACTIVE signals.
    */
    def apply(linkactive_req: Bool, linkactive_ack: Bool) = {
        val bundle = new CHILinkActiveBundle
        bundle.from(linkactive_req, linkactive_ack)
        bundle
    }
}
