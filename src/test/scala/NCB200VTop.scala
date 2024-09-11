package cc.xiangshan.openncb

import chisel3._
import chisel3.util.experimental.forceName
import circt.stage.ChiselStage
import org.chipsalliance.cde.config.Config
import cc.xiangshan.openncb.axi.AXI4Parameters
import cc.xiangshan.openncb.axi.AXI4ParametersKey
import cc.xiangshan.openncb.axi.intf.AXI4InterfaceMaster
import cc.xiangshan.openncb.chi.CHIParameters
import cc.xiangshan.openncb.chi.CHIParametersKey
import cc.xiangshan.openncb.chi.EnumCHIIssue
import cc.xiangshan.openncb.chi.intf._


/*
* NCB-200 Verilog Top.
* 
* * Recommended to be used for SystemVerilog output generation only.
* 
* @param paramRawInterface Whether generate a instance with raw flit ports.
*/
class NCB200VTop(val paramRawInterface: Boolean) extends Module {

    // Configurations
    implicit val p = new Config((_, _, _) => {

        case AXI4ParametersKey  => new AXI4Parameters(
            idWidth             = 4,
            addrWidth           = 64,
            dataWidth           = 64
        )

        case CHIParametersKey   => new CHIParameters(
            issue               = EnumCHIIssue.B,
            nodeIdWidth         = 7,
            reqAddrWidth        = 48,
            reqRsvdcWidth       = 4,
            datRsvdcWidth       = 4,
            dataWidth           = 256,
            dataCheckPresent    = false,
            poisonPresent       = false,
            mpamPresent         = false
        )

        case NCBParametersKey   => new NCBParameters(
            axiMasterOrder      = EnumAXIMasterOrder.WriteAddress 
        )
    })


    // I/O
    val io = IO(new Bundle {
        val chi                     = {
            if (paramRawInterface)
                CHISNFRawInterface()
            else
                CHISNFInterface()
        }
        val axi                     = AXI4InterfaceMaster()
    })

    
    // Module: NCB200
    val uNCB200     = Module(new NCB200)

    io.axi <> uNCB200.io.axi
    io.chi <> {
        if (paramRawInterface)
            uNCB200.io.chi.asToRaw
        else
            uNCB200.io.chi
    }


    // Debug Signals
    val debug   = IO(Output(chiselTypeOf(uNCB200.debug)))

    debug <> uNCB200.debug
}

// Generate to SystemVerilog
object NCB200VTop extends App {

    ChiselStage.emitSystemVerilogFile(
        new NCB200VTop(false),
        Array("--target-dir", "build")
    )
}
