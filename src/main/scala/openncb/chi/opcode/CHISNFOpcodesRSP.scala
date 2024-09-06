package cc.xiangshan.openncb.chi.opcode

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.chi._
import cc.xiangshan.openncb.chi.EnumCHIIssue._
import cc.xiangshan.openncb.chi.EnumCHIChannel._
import cc.xiangshan.openncb.chi.opcode.CHIOpcodeDecoder


/* 
* CHI Opcodes permitted for RSP channel of SN-F interface
*/
trait CHISNFOpcodesRSP extends WithCHIParameters {

    //  =============================================================================
    val RespLCrdReturn          = CHIOpcode(RSP, 0x00, "RespLCrdReturn"             )
    //  -----------------------------------------------------------------------------
    val RetryAck                = CHIOpcode(RSP, 0x03, "RetryAck"                   )
    val PCrdGrant               = CHIOpcode(RSP, 0x07, "PCrdGrant"                  )
    val Comp                    = CHIOpcode(RSP, 0x04, "Comp"                       )
    val CompDBIDResp            = CHIOpcode(RSP, 0x05, "CompDBIDResp"               )
    //  -----------------------------------------------------------------------------
    val CompCMO                 = CHIOpcode(RSP, 0x14, "CompCMO"                , E )
    val ReadReceipt             = CHIOpcode(RSP, 0x08, "ReadReceipt"                )
    //  -----------------------------------------------------------------------------
    val DBIDResp                = CHIOpcode(RSP, 0x06, "DBIDResp"                   )
    //  -----------------------------------------------------------------------------
    val TagMatch                = CHIOpcode(RSP, 0x0A, "TagMatch"               , E )
    //  -----------------------------------------------------------------------------
    val Persist                 = CHIOpcode(RSP, 0x0C, "Persist"                , E )
    //  -----------------------------------------------------------------------------
    val CompPersist             = CHIOpcode(RSP, 0x0D, "CompPersist"            , E )
    //  =============================================================================


    /*
    * Decoder for CHI Opcodes of SN-F RSP 
    * 
    * @see {@code cc.xiangshan.openncb.chi.opcode.CHIOpcodeDecoder}
    */
    class Decoder(paramOpcodeSupported          : Seq[CHIOpcode]    = Seq(),
                  paramEnableUnsupportedCheck   : Boolean           = false)
        (implicit p: Parameters)
        extends CHIOpcodeDecoder(RSP, paramOpcodeSupported, Seq(
        //  ========================
            RespLCrdReturn,
        //  ------------------------
            RetryAck,
            PCrdGrant,
            Comp,
            CompDBIDResp,
        //  ------------------------
            CompCMO,
            ReadReceipt,
        //  ------------------------
            DBIDResp,
        //  ------------------------
            TagMatch,
        //  ------------------------
            Persist,
        //  ------------------------
            CompPersist
        //  ========================
        ), paramEnableUnsupportedCheck)
}
