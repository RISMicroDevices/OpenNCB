package cn.rismd.openncb.chi

import chisel3._
import cn.rismd.openncb.chi.EnumCHIIssue._
import cn.rismd.openncb.chi.EnumCHIChannel._


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
}
