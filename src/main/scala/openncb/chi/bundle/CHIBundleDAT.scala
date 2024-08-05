package cn.rismd.openncb.chi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cn.rismd.openncb.chi._
import cn.rismd.openncb.chi.field._


/*
* CHI (TX/RX) DAT channel signals bundle.
*/
class CHIBundleDAT(implicit p: Parameters) extends AbstractCHIBundle {

    //  ================================================================
    val QoS             = CHIFieldUInt(paramCHI.datQoSWidth)
    //  ----------------------------------------------------------------
    val TgtID           = CHIFieldUInt(paramCHI.datTgtIDWidth)
    //  ----------------------------------------------------------------
    val SrcID           = CHIFieldUInt(paramCHI.datSrcIDWidth)
    //  ----------------------------------------------------------------
    val TxnID           = CHIFieldUInt(paramCHI.datTxnIDWidth)
    //  ----------------------------------------------------------------
    val HomeNID         = CHIFieldUInt(paramCHI.datHomeNIDWidth)
    //  ----------------------------------------------------------------
    val Opcode          = CHIFieldUInt(paramCHI.datOpcodeWidth)
    //  ----------------------------------------------------------------
    val RespErr         = CHIFieldUInt(paramCHI.datRespErrWidth)
    //  ----------------------------------------------------------------
    val Resp            = CHIFieldUInt(paramCHI.datRespWidth)
    //  ----------------------------------------------------------------
    val FwdState_DataPull_DataSource    = CHIFieldUInt(
            paramCHI.datFwdStateWidth 
        max paramCHI.datDataPullWidth 
        max paramCHI.datDataSourceWidth)

    def FwdState        = CHIFieldUInt(paramCHI.datFwdStateWidth    - 1, 0, 0, FwdState_DataPull_DataSource)
    def DataPull        = CHIFieldUInt(paramCHI.datDataPullWidth    - 1, 0, 0, FwdState_DataPull_DataSource)
    def DataSource      = CHIFieldUInt(paramCHI.datDataSourceWidth  - 1, 0, 0, FwdState_DataPull_DataSource)
    //  ----------------------------------------------------------------
    val CBusy           = CHIFieldUInt(paramCHI.datCBusyWidth, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val DBID            = CHIFieldUInt(paramCHI.datDBIDWidth)
    //  ----------------------------------------------------------------
    val CCID            = CHIFieldUInt(paramCHI.datCCIDWidth)
    //  ----------------------------------------------------------------
    val DataID          = CHIFieldUInt(paramCHI.datDataIDWidth)
    //  ----------------------------------------------------------------
    val TagOp           = CHIFieldUInt(paramCHI.datTagOpWidth, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val Tag             = CHIFieldUInt(paramCHI.datTagWidth, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val TU              = CHIFieldUInt(paramCHI.datTUWidth, EnumCHIIssue.E)
    //  ----------------------------------------------------------------
    val TraceTag        = CHIFieldUInt(paramCHI.datTraceTagWidth)
    //  ----------------------------------------------------------------
    val RSVDC           = CHIFieldUInt(paramCHI.datRsvdcWidth)
    //  ----------------------------------------------------------------
    val BE              = CHIFieldUInt(paramCHI.datBEWidth)
    //  ----------------------------------------------------------------
    val Data            = CHIFieldUInt(paramCHI.datDataWidth)
    //  ----------------------------------------------------------------
    val DataCheck       = CHIFieldUInt(paramCHI.datDataCheckWidth)
    //  ----------------------------------------------------------------
    val Poison          = CHIFieldUInt(paramCHI.datPoisonWidth)
    //  ================================================================
}
