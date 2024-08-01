package cn.rismd.openncb.axi

import org.chipsalliance.cde.config.Field


case class AXI4ParametersReadChannel (

    /*
    * idWidth: Width of AXI4 read channel IDs.
    *          This is applied to the width of 'ARID' and 'RID'.
    * 
    * * Recommended to set to a width that able to hold configured NCB-Transaction-ID
    *   (actually the configured number of outstanding CHI transactions)
    *   if the ID of AXI4 read channel was not set to a constant value.  
    */
    idWidth         : Int   = 4,

    /*
    * addrWidth: Width of AXI4 read channel address.
    *            This is applied to the width of 'ARADDR'.
    */
    addrWidth       : Int   = 32,

    /*
    * dataWidth: Width of AXI4 read channel data.
    *            This is applied to the width of 'RDATA'.
    */
    dataWidth       : Int   = 64
){

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

object AXI4ParametersReadChannel {

    /*
    * Convert rocket-chip parameters into local type.
    */
    def fromRocketChip(p: freechips.rocketchip.amba.axi4.AXI4BundleParameters) = AXI4ParametersReadChannel(
        idWidth     = p.idBits,
        addrWidth   = p.addrBits,
        dataWidth   = p.dataBits)
}


case object AXI4ParametersReadChannelKey extends Field[AXI4ParametersReadChannel]
