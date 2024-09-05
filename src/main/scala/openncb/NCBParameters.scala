package cn.rismd.openncb

import org.chipsalliance.cde.config.Field
import chisel3.util.log2Up


/* 
* Enumeration of NCB AXI Master Order.
*/
sealed class EnumAXIMasterOrder(ordinal         : Int,
                                name            : String)
    extends Enum[EnumAXIMasterOrder](name, ordinal)

object EnumAXIMasterOrder {

    /*
    * Request Order.
    * * A transaction was allowed to start only after all older transactions were finished.
    * * Early 'Comp*' always available.
    */
    val Request         : EnumAXIMasterOrder    = new EnumAXIMasterOrder(0, "Request")

    /*
    * Address Order.
    * * A transaction was allowed to start only after all older transactions with overlapping
    *   address targets were finished.
    * * Early 'Comp*' always available.
    */
    val Address         : EnumAXIMasterOrder    = new EnumAXIMasterOrder(1, "Address")
    
    /*
    * Write Order.
    * * A transaction was allowed to start only after all older write transactions were
    *   finished.
    * * Early 'Comp*' always available. 
    */
    val Write           : EnumAXIMasterOrder    = new EnumAXIMasterOrder(2, "Write")

    /*
    * Write Address Order.
    * * A transaction was allowed to start only after all older write transactions with
    *   overlapping address targets were finished.
    * * Early 'Comp*' always available. 
    */
    val WriteAddress    : EnumAXIMasterOrder    = new EnumAXIMasterOrder(3, "WriteAddress")

    /*
    * None.
    * * No ordering. 
    * * Early 'Comp*' not available unless EWA = 1.
    */
    val None            : EnumAXIMasterOrder    = new EnumAXIMasterOrder(4, "None")
}


/* 
* NCB Parameters.
*/
case class NCBParameters (

    // NCB Parameters
    /* 
    * outstandingDepth: Configure the outstanding depth of CHI transactions of NCB.
    * 
    * * By default, {@code outstandingDepth} is set to the maximum value, 
    *   which is {@value 15}.
    * 
    * * The legal value for {@code outstandingDepth} was 1 to 15, determined by the
    *   AMBA CHI specification. The outstanding status are controlled by CHI Link Credit.
    */
    outstandingDepth            : Int                   = 15,

    /*
    * axiMasterOrder: Configure the AXI Master Order of NCB.
    * @see EnumAXIMasterOrder
    * 
    * * By default, {@code axiMasterOrder} is set to {@value None}.
    */
    axiMasterOrder              : EnumAXIMasterOrder    = EnumAXIMasterOrder.None,

    /*
    * writeCancelable: Configure whether the CHI write transactions could be cancelled.
    * 
    * * By default, {@code writeCancelable} is set to {@value true}.
    * 
    * * When the {@code writeCancelable} was set to {@false}, 'WriteDataCancel'
    *   would not be accepted.
    * 
    * * The 'WriteDataCancel' was only allowed for WriteNoSnpPtl.
    */
    writeCancelable             : Boolean               = true,

    /* 
    * writeCompPreferSeperate: Configure seperate 'DBIDResp' and 'Comp' prefering.
    * 
    * * By default, {@code writeCompPreferSeperate} is set to {@value false}.
    * 
    * * No 'CompDBIDResp' would be sent when {@code writeCompPreferSeperate} set to {@value true}.
    */
    writeCompPreferSeperate     : Boolean               = false,

    /*
    * writeNoError: Configure whether the AXI errors were received.
    * 
    * * By default, {@code writeNoError} is set to {@value false}.
    * 
    * * When {@code writeNoError} set to {@value true}, 'RespErr' would always be Normal Okay.
    *   When {@code writeNoError} set to {@value false}, 'RespErr' is mapped from 'BRESP' as:
    *       - AXI: OKAY     -> CHI: OK
    *       - AXI: EXOKAY   -> CHI: EXOK
    *       - AXI: SLVERR   -> CHI: NDERR
    *       - AXI: DECERR   -> CHI: NDERR
    */
    writeNoError                : Boolean               = false,

    /*
    * readReceiptAfterAcception: Configure whether sending ReadReceipt only after
    *                            AXI AR Channel accepted.
    * 
    * * By default, {@code readReceiptAfterAcception} is set to {@value false}.
    */
    readReceiptAfterAcception   : Boolean           = false,

    /*
    * readCompDMT: Configure whether DMT enabled.
    * 
    * * By default, {@code readCompDMT} is set to {@value true}.
    */
    readCompDMT                 : Boolean           = true,

    /*
    * readCompHomeNID: Configure the HomeNID on DMT disabled.
    * 
    * * By default, {@code readCompHomeNID} is set to {@value 0}. 
    */
    readCompHomeNID             : Int               = 0,


    // AXI side - AW channel
    /* 
    * axiConstantAWID: Configure whether the AXI4 write channel IDs
    *                  were set to a constant value.
    *
    * * By default, {@code axiConstantAWID} is set to {@value false}.
    * 
    * * When {@code axiConstantAWID} was set to {@value true}, on the AXI4 write channel
    *   side, the output value of 'AWID' was always tied to {@code axiConstantAWIDValue}.
    *   Otherwise, the 'AWID' was tied to the NCB-Transaction-ID (inner-NCB) of the on-going
    *   transaction, whose maximum value is decided by the configured outstanding count of
    *   CHI transactions.
    */
    axiConstantAWID             : Boolean               = false,

    /*
    * axiConstantAWIDValue: Specify the fixed value of AXI write channel ID
    *                       when {@code axiConstantAWID} set to {@value true}.
    * *  When {@code axiConstantAWID} is set to {@value true}, on the AXI4 write channel
    *    side, the output value of 'AWID' is always tied to {@code axiConstantAWIDValue}.
    */
    axiConstantAWIDValue        : Int                   = 0,

    /*
    * axiConstantAWQoS: Configure whether the AXI4 write channel QoS
    *                   was set to a constant value.
    * 
    * * By default, {@code axiConstantAWQoS} is set to {@value false}.
    * 
    * * When {@code axiConstantAWQoS} was set to {@value true}, on the AXI4 write channel
    *   side, the output value of 'AWQOS' was always tied to {@code axiConstantAWQoSValue}.
    *   Otherwise, the 'AWQOS' came from CHI side.
    */
    axiConstantAWQoS            : Boolean               = false,

    /*
    * axiConstantAWQoSValue: Specify the fixed value of AXI write channel QoS
    *                        when {@code axiConstantAWQoS} set to {@value true}.
    * 
    * * By default, {@code axiConstantAWQosValue} is set to {@value 0}.
    * 
    * * When {@code axiConstantAWQoS} was set to {@value true}, on the AXI4 write channel
    *   side, the output value of 'AWQOS' was always tied to {@code axiConstantAWQoSValue}.
    */
    axiConstantAWQoSValue       : Int                   = 0,

    /*
    * axiAWRegionValue: Configure the REGION field value of AXI AW channel.
    * 
    * * By default, {@code axiAWRegionValue} is set to {@value 0}.
    */
    axiAWRegionValue            : Int                   = 0,

    /*
    * axiAWBufferable: Configure whether the AXI write channel Cache Attribute
    *                  was set with Bufferable attribute.
    * 
    * * By default, {@code axiAWBufferable} is set to {@value false}.
    * 
    * * When {@code axiAWBufferable} was set to {@value true}, on the AXI4 write channel
    *   side, the output value of 'AWCACHE' was always tied to {@value 0b0011}, indicating
    *   Normal Non-cacheable Bufferable Memory.
    *   Otherwise, the 'AWCACHE' was tied to {@value 0b0010}, indicating
    *   Normal Non-cacheable Non-bufferable Memory.
    */
    axiAWBufferable             : Boolean               = false,

    /*
    * axiAWAfterFirstData: Configure whether the downstream AXI write address (AW) channel
    *                      transactions were only sent after upstream CHI channel had 
    *                      received first write back data or cancel.
    * 
    * * By default, {@code axiAWAfterFirstData} is set to {@value false}.
    * 
    * * Might be useful for AXI data write bandwidth control. Since the write interleaving
    *   support was removed in AXI-4, the order of data transmission was determined by the
    *   order of write address channel sequence:
    *   In some circumstances or system designs, data might arrive in a long time after 
    *   the arrival of CHI write request in RXREQ, occupying the in-order write issuing
    *   channel, leading to inability of sending later write transactions with data ready.
    *   On the other side, with {@code axiAWAfterFirstData} enabled, this would result 
    *   in a slightly longer transaction completion time, and losing some overall optimization
    *   through early write address requests.
    *   (e.g. in some DDR controllers, early write address could be performed as a prefetch
    *         or a earlier pre-charge triggering hint)
    */
    axiAWAfterFirstData         : Boolean               = false,


    // AXI side - AR channel
    /* 
    * axiConstantARID: Configure whether the AXI4 read channel IDs
    *                  were set to a constant value.
    *
    * * By default, {@code axiConstantARID} is set to {@value false}.
    *  
    * * When {@code axiConstantARID} was set to {@value true}, on the AXI4 read channel
    *   side, the output value of 'ARID' was always tied to {@code axiConstantARIDValue}.
    *   Otherwise, the 'ARID' was tied to the NCB-Transaction-ID (inner-NCB) of the on-going
    *   transaction, whose maximum value is decided by the configured outstanding count of
    *   CHI transactions.
    */
    axiConstantARID             : Boolean               = false,

    /*
    * axiConstantARIDValue: Specify the fixed value of AXI read channel ID
    *                       when {@code axiConstantARID} set to {@value true}.
    * *  When {@code axiConstantARID} is set to {@value true}, on the AXI4 read channel
    *    side, the output value of 'ARID' is always tied to {@code axiConstantARIDValue}.
    */
    axiConstantARIDValue        : Int                   = 0,

    /*
    * axiConstantARQoS: Configure whether the AXI4 read channel QoS
    *                   was set to a constant value.
    * 
    * * By default, {@code axiConstantARQoS} is set to {@value false}.
    * 
    * * When {@code axiConstantARQoS} was set to {@value true}, on the AXI4 read channel
    *   side, the output value of 'ARQOS' was always tied to {@code axiConstantARQoSValue}.
    *   Otherwise, the 'ARQOS' came from CHI side.
    */
    axiConstantARQoS            : Boolean               = false,

    /*
    * axiConstantARQoSValue: Specify the fixed value of AXI read channel QoS
    *                        when {@code axiConstantARQoS} set to {@value true}.
    * 
    * * By default, {@code axiConstantARQosValue} is set to {@value 0}.
    * 
    * * When {@code axiConstantARQoS} was set to {@value true}, on the AXI4 read channel
    *   side, the output value of 'ARQOS' was always tied to {@code axiConstantARQoSValue}.
    */
    axiConstantARQoSValue       : Int                   = 0,

    /*
    * axiARRegionValue: Configure the REGION field value of AXI AR channel.
    * 
    * * By default, {@code axiARRegionValue} is set to {@value 0}.
    */
    axiARRegionValue            : Int                   = 0,

    /*
    * axiARBufferable: Configure whether the AXI read channel Cache Attribute
    *                  was set with Bufferable attribute.
    * 
    * * By default, {@code axiARBufferable} is set to {@value false}.
    * 
    * * When {@code axiARBufferable} was set to {@value true}, on the AXI4 read channel
    *   side, the output value of 'ARCACHE' was always tied to {@value 0b0011}, indicating
    *   Normal Non-cacheable Bufferable Memory.
    *   Otherwise, the 'ARCACHE' was tied to {@value 0b0010}, indicating
    *   Normal Non-cacheable Non-bufferable Memory.
    */
    axiARBufferable             : Boolean               = false
) {

    require(outstandingDepth >= 1 && outstandingDepth <= 15, 
        s"The legal value for 'outstandingDepth' was 1 to 15, by the AMBA CHI specification: " +
        s"outstandingDepth = ${outstandingDepth}")

    //
    def outstandingIndexWidth: Int  = log2Up(outstandingDepth)
}


case object NCBParametersKey extends Field[NCBParameters]
