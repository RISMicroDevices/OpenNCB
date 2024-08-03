package cn.rismd.openncb.chi

import chisel3._
import cn.rismd.openncb.chi.EnumCHIIssue._
import cn.rismd.openncb.chi.EnumCHIChannel._


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
}
