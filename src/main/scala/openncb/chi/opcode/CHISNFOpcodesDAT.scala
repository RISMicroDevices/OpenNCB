package cn.rismd.openncb.chi.opcode

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi._
import cn.rismd.openncb.chi.EnumCHIIssue._
import cn.rismd.openncb.chi.EnumCHIChannel._
import cn.rismd.openncb.chi.opcode.CHIOpcodeDecoder


/* 
* CHI Opcodes permitted for DAT channel of SN-F interface
*/
trait CHISNFOpcodesDAT extends WithCHIParameters {

    //  =============================================================================
    val DataLCrdReturn          = CHIOpcode(DAT, 0x00, "DataLCrdReturn"             )
    //  -----------------------------------------------------------------------------
    val CompData                = CHIOpcode(DAT, 0x04, "CompData"                   )
    //  -----------------------------------------------------------------------------
    val DataSepResp             = CHIOpcode(DAT, 0x0B, "DataSepResp"            , E )
    //  -----------------------------------------------------------------------------
    val WriteDataCancel         = CHIOpcode(DAT, 0x07, "WriteDataCancel"            )
    //  -----------------------------------------------------------------------------
    val NonCopyBackWrData       = CHIOpcode(DAT, 0x03, "NonCopyBackWrData"          )
    //  =============================================================================


    /* 
    * Decoder for CHI Opcodes of SN-F DAT
    * 
    * @see {@code cn.rismd.openncb.chi.opcode.CHIOpcodeDecoder}
    */ 
    class Decoder(paramOpcodeSupported          : Seq[CHIOpcode]    = Seq(),
                  paramEnableUnsupportedCheck   : Boolean           = false)
        (implicit p: Parameters)
        extends CHIOpcodeDecoder(DAT, paramOpcodeSupported, Seq(
        //  ========================
            DataLCrdReturn,
        //  ------------------------
            CompData,
        //  ------------------------
            DataSepResp,
        //  ------------------------
            WriteDataCancel,
        //  ------------------------
            NonCopyBackWrData
        //  ========================
        ), paramEnableUnsupportedCheck)
}
