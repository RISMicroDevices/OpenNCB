package cc.xiangshan.openncb.axi

import org.chipsalliance.cde.config.Field


case class AXI4Parameters (
    
    /*
    * idWidth: Width of AXI4 channel IDs.
    *          This is applied to the width of 'AWID' and 'BID'.
    * 
    * * Recommended to set to a width that able to hold configured NCB-Transaction-ID
    *   (actually the configured number of outstanding CHI transactions)
    *   if the ID of AXI4 channel was not set to a constant value.  
    */
    idWidth         : Int   = 4,

    /*
    * addrWidth: Width of AXI4 channel address.
    *            This is applied to the width of 'AWADDR'.
    */
    addrWidth       : Int   = 32,

    /*
    * dataWidth: Width of AXI4 channel data.
    *            This is applied to the width of 'WDATA'.
    */
    dataWidth       : Int   = 64
) {

    /* 
    * strbWidth: Width of AXI4 channel strobe.
    *            This is applied to the width of 'WSTRB'.
    */
    def strbWidth: Int      = dataWidth / 8


    /*
    * Convert this bundle into rocket-chip parameters type.
    * 
    * @return {@code freechips.rocketchip.amba.axi4.AXI4BundleAR}
    */
    def asRocketChip = freechips.rocketchip.amba.axi4.AXI4BundleParameters(
        addrBits    = addrWidth,
        dataBits    = dataWidth,
        idBits      = idWidth
    )
}

object AXI4Parameters {

    /*
    * Convert rocket-chip parameters into local type.
    */
    def fromRocketChip(p: freechips.rocketchip.amba.axi4.AXI4BundleParameters) = AXI4Parameters(
        idWidth     = p.idBits,
        addrWidth   = p.addrBits,
        dataWidth   = p.dataBits)
}


case object AXI4ParametersKey extends Field[AXI4Parameters]
