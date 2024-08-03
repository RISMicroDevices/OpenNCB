package cn.rismd.openncb.chi

import org.chipsalliance.cde.config.Parameters
import chisel3._


/*
* CHI (TX/RX) RSP channel signals bundle. 
*/
class CHIBundleRSP(implicit p: Parameters) extends AbstractCHIBundle {

    //  ================================================================
    val QoS             = CHIFieldUInt(paramCHI.rspQoSWidth)
    //  ----------------------------------------------------------------
    val TgtID           = CHIFieldUInt(paramCHI.rspTgtIDWidth)
    //  ----------------------------------------------------------------
    val SrcID           = CHIFieldUInt(paramCHI.rspSrcIDWidth)
    //  ----------------------------------------------------------------
    val TxnID           = CHIFieldUInt(paramCHI.rspTxnIDWidth)
    //  ----------------------------------------------------------------
    val Opcode          = CHIFieldUInt(paramCHI.rspOpcodeWidth)
    //  ----------------------------------------------------------------
    val RespErr         = CHIFieldUInt(paramCHI.rspRespErrWidth)
    //  ----------------------------------------------------------------
    val Resp            = CHIFieldUInt(paramCHI.rspRespWidth)
    //  ----------------------------------------------------------------
    val FwdState_DataPull   = CHIFieldUInt(
            paramCHI.rspFwdStateWidth 
        max paramCHI.rspDataPullWidth)

    def FwdState        = CHIFieldUInt(paramCHI.rspFwdStateWidth    - 1, 0, 0, FwdState_DataPull)
    def DataPull        = CHIFieldUInt(paramCHI.rspDataPullWidth    - 1, 0, 0, FwdState_DataPull)
    //  ----------------------------------------------------------------
    val CBusy           = CHIFieldUInt(paramCHI.rspCBusyWidth, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val DBID_PGroupID_StashGroupID_TagGroupID   = CHIFieldUInt(
            paramCHI.rspDBIDWidth
        max paramCHI.rspPGroupIDWidth
        max paramCHI.rspStashGroupIDWidth
        max paramCHI.rspTagGroupIDWidth)

    def DBID            = CHIFieldUInt(paramCHI.rspDBIDWidth        - 1, 0, 0, DBID_PGroupID_StashGroupID_TagGroupID)
    def PGroupID        = CHIFieldUInt(paramCHI.rspPGroupIDWidth    - 1, 0, 0, DBID_PGroupID_StashGroupID_TagGroupID, EnumCHIIssue.E)
    def StashGroupID    = CHIFieldUInt(paramCHI.rspStashGroupIDWidth- 1, 0, 0, DBID_PGroupID_StashGroupID_TagGroupID, EnumCHIIssue.E)
    def TagGroupID      = CHIFieldUInt(paramCHI.rspTagGroupIDWidth  - 1, 0, 0, DBID_PGroupID_StashGroupID_TagGroupID, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val PCrdType        = CHIFieldUInt(paramCHI.rspPCrdTypeWidth)
    //  ----------------------------------------------------------------
    val TagOp           = CHIFieldUInt(paramCHI.rspTagOpWidth, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val TraceTag        = CHIFieldUInt(paramCHI.rspTraceTagWidth)
    //  ================================================================
}