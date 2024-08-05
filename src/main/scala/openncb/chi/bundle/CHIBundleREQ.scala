package cn.rismd.openncb.chi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cn.rismd.openncb.chi._
import cn.rismd.openncb.chi.field._


/*
* CHI (TX/RX) REQ channel signals bundle.
*/
class CHIBundleREQ(implicit p: Parameters) extends AbstractCHIBundle {

    //  ================================================================
    val QoS             = CHIFieldUInt(paramCHI.reqQoSWidth)
    //  ----------------------------------------------------------------
    val TgtID           = CHIFieldUInt(paramCHI.reqTgtIDWidth)
    //  ----------------------------------------------------------------
    val SrcID           = CHIFieldUInt(paramCHI.reqSrcIDWidth)
    //  ----------------------------------------------------------------
    val TxnID           = CHIFieldUInt(paramCHI.reqTxnIDWidth)
    //  ----------------------------------------------------------------
    val ReturnNID_StashNID_SLCRepHint           = CHIFieldUInt(
            paramCHI.reqReturnNIDWidth 
        max paramCHI.reqStashNIDWidth
        max paramCHI.reqSLCRepHintWidth)

    def ReturnNID       = CHIFieldUInt(paramCHI.reqReturnNIDWidth   - 1, 0, 0, ReturnNID_StashNID_SLCRepHint)
    def StashNID        = CHIFieldUInt(paramCHI.reqStashNIDWidth    - 1, 0, 0, ReturnNID_StashNID_SLCRepHint)
    def SLCRepHint      = CHIFieldUInt(paramCHI.reqSLCRepHintWidth  - 1, 0, 0, ReturnNID_StashNID_SLCRepHint, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val StashNIDValid_Endian_Deep               = CHIFieldUInt(
            paramCHI.reqStashNIDValidWidth
        max paramCHI.reqEndianWidth
        max paramCHI.reqDeepWidth)

    val StashNIDValid   = CHIFieldUInt(paramCHI.reqStashNIDValidWidth   - 1, 0, 0, StashNIDValid_Endian_Deep)
    val Endian          = CHIFieldUInt(paramCHI.reqEndianWidth          - 1, 0, 0, StashNIDValid_Endian_Deep)
    val Deep            = CHIFieldUInt(paramCHI.reqDeepWidth            - 1, 0, 0, StashNIDValid_Endian_Deep, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val ReturnTxnID_StashLPIDValid_StashLPID    = CHIFieldUInt(
            paramCHI.reqReturnTxnIDWidth
        max(paramCHI.reqStashLPIDWidth + paramCHI.reqStashLPIDValidWidth))

    def ReturnTxnID     = CHIFieldUInt(paramCHI.reqReturnTxnIDWidth     - 1, 0,                          0, ReturnTxnID_StashLPIDValid_StashLPID)
    def StashLPIDValid  = CHIFieldUInt(paramCHI.reqStashLPIDValidWidth  - 1, 0, paramCHI.reqStashLPIDWidth, ReturnTxnID_StashLPIDValid_StashLPID)
    def StashLPID       = CHIFieldUInt(paramCHI.reqStashLPIDWidth       - 1, 0,                          0, ReturnTxnID_StashLPIDValid_StashLPID)
    //  ----------------------------------------------------------------
    val Opcode          = CHIFieldUInt(paramCHI.reqOpcodeWidth)
    //  ----------------------------------------------------------------
    val Size            = CHIFieldUInt(paramCHI.reqSizeWidth)
    //  ----------------------------------------------------------------
    val Addr            = CHIFieldUInt(paramCHI.reqAddrWidth)
    //  ----------------------------------------------------------------
    val NS              = CHIFieldUInt(paramCHI.reqNSWidth)
    //  ----------------------------------------------------------------
    val LikelyShared    = CHIFieldUInt(paramCHI.reqLikelySharedWidth)
    //  ----------------------------------------------------------------
    val AllowRetry      = CHIFieldUInt(paramCHI.reqAllowRetryWidth)
    //  ----------------------------------------------------------------
    val Order           = CHIFieldUInt(paramCHI.reqOrderWidth)
    //  ----------------------------------------------------------------
    val PCrdType        = CHIFieldUInt(paramCHI.reqPCrdTypeWidth)
    //  ----------------------------------------------------------------
    val MemAttr         = CHIFieldUInt(paramCHI.reqMemAttrWidth)
    //  ----------------------------------------------------------------
    val SnpAttr_DoDWT   = CHIFieldUInt(
            paramCHI.reqSnpAttrWidth
        max paramCHI.reqDoDWTWidth)

    def SnpAttr         = CHIFieldUInt(paramCHI.reqSnpAttrWidth - 1, 0, 0, SnpAttr_DoDWT)
    def DoDWT           = CHIFieldUInt(paramCHI.reqDoDWTWidth   - 1, 0, 0, SnpAttr_DoDWT, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val LPID_PGroupID_StashGroupID_TagGroupID   = CHIFieldUInt(
            paramCHI.reqLPIDWidth
        max paramCHI.reqPGroupIDWidth
        max paramCHI.reqStashGroupIDWidth
        max paramCHI.reqTagGroupIDWidth)

    def LPID            = CHIFieldUInt(paramCHI.reqLPIDWidth         - 1, 0, 0, LPID_PGroupID_StashGroupID_TagGroupID)
    def PGroupID        = CHIFieldUInt(paramCHI.reqPGroupIDWidth     - 1, 0, 0, LPID_PGroupID_StashGroupID_TagGroupID, EnumCHIIssue.E)
    def StashGroupID    = CHIFieldUInt(paramCHI.reqStashGroupIDWidth - 1, 0, 0, LPID_PGroupID_StashGroupID_TagGroupID, EnumCHIIssue.E)
    def TagGroupID      = CHIFieldUInt(paramCHI.reqTagGroupIDWidth   - 1, 0, 0, LPID_PGroupID_StashGroupID_TagGroupID, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val Excl_SnoopMe    = CHIFieldUInt(
            paramCHI.reqExclWidth 
        max paramCHI.reqSnoopMeWidth)

    def Excl            = CHIFieldUInt(paramCHI.reqExclWidth    - 1, 0, 0, Excl_SnoopMe)
    def SnoopMe         = CHIFieldUInt(paramCHI.reqSnoopMeWidth - 1, 0, 0, Excl_SnoopMe)
    //  ----------------------------------------------------------------
    val ExpCompAck      = CHIFieldUInt(paramCHI.reqExpCompAckWidth)
    //  ----------------------------------------------------------------
    val TagOp           = CHIFieldUInt(paramCHI.reqTagOpWidth, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val TraceTag        = CHIFieldUInt(paramCHI.reqTraceTagWidth)
    //  ----------------------------------------------------------------
    val MPAM            = CHIFieldUInt(paramCHI.reqMPAMWidth, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val RSVDC           = CHIFieldUInt(paramCHI.reqRsvdcWidth)
    //  ================================================================
}
