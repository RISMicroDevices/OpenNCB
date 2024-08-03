package cn.rismd.openncb.chi

import org.chipsalliance.cde.config.Parameters
import chisel3._


/* 
* CHI (TX/RX) SNP channel signals bundle.
*/
class CHIBundleSNP(implicit p: Parameters) extends AbstractCHIBundle {

    //  ================================================================
    val QoS             = CHIFieldUInt(paramCHI.snpQoSWidth)
    //  ----------------------------------------------------------------
    val SrcID           = CHIFieldUInt(paramCHI.snpSrcIDWidth)
    //  ----------------------------------------------------------------
    val TxnID           = CHIFieldUInt(paramCHI.snpTxnIDWidth)
    //  ----------------------------------------------------------------
    val FwdNID          = CHIFieldUInt(paramCHI.snpFwdNIDWidth)
    //  ----------------------------------------------------------------
    val FwdTxnID_StashLPIDValid_StashLPID_VMIDExt   = CHIFieldUInt(
            paramCHI.snpFwdNIDWidth 
        max(paramCHI.snpStashLPIDValidWidth + paramCHI.snpStashLPIDWidth)
        max paramCHI.snpVMIDExtWidth)

    def FwdTxnID        = CHIFieldUInt(paramCHI.snpFwdNIDWidth          - 1, 0, 0                           , FwdTxnID_StashLPIDValid_StashLPID_VMIDExt)
    def StashLPIDValid  = CHIFieldUInt(paramCHI.snpStashLPIDValidWidth  - 1, 0, paramCHI.snpStashLPIDWidth  , FwdTxnID_StashLPIDValid_StashLPID_VMIDExt)
    def StashLPID       = CHIFieldUInt(paramCHI.snpStashLPIDWidth       - 1, 0, 0                           , FwdTxnID_StashLPIDValid_StashLPID_VMIDExt)
    def VMIDExt         = CHIFieldUInt(paramCHI.snpVMIDExtWidth         - 1, 0, 0                           , FwdTxnID_StashLPIDValid_StashLPID_VMIDExt)
    //  ----------------------------------------------------------------
    val Opcode          = CHIFieldUInt(paramCHI.snpOpcodeWidth)
    //  ----------------------------------------------------------------
    val Addr            = CHIFieldUInt(paramCHI.snpAddrWidth)
    //  ----------------------------------------------------------------
    val NS              = CHIFieldUInt(paramCHI.snpNSWidth)
    //  ----------------------------------------------------------------
    val DoNotGoToSD_DoNotDataPull   = CHIFieldUInt(
            paramCHI.snpDoNotGoToSDWidth 
        max paramCHI.snpDoNotDataPullWidth)

    def DoNotGoToSD     = CHIFieldUInt(paramCHI.snpDoNotGoToSDWidth     - 1, 0, 0, DoNotGoToSD_DoNotDataPull)
    def DoNotDataPull   = CHIFieldUInt(paramCHI.snpDoNotDataPullWidth   - 1, 0, 0, DoNotGoToSD_DoNotDataPull, EnumCHIIssue.B)
    //  ----------------------------------------------------------------
    val RetToSrc        = CHIFieldUInt(paramCHI.snpRetToSrcWidth)
    //  ----------------------------------------------------------------
    val TraceTag        = CHIFieldUInt(paramCHI.snpTraceTagWidth)
    //  ----------------------------------------------------------------
    val MPAM            = CHIFieldUInt(paramCHI.snpMPAMWidth, EnumCHIIssue.E)
    //  ================================================================
}
