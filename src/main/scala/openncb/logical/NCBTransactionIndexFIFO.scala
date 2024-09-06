package cc.xiangshan.openncb.logical

import chisel3._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.NCBParameters
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.util.ParallelMux
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


/*
* NCB Transaction Index FIFO (for downstream AXI ports ID mapping).
*/
object NCBTransactionIndexFIFO {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]
}

class NCBTransactionIndexFIFO(implicit val p: Parameters)
        extends Module with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBTransactionIndexFIFO.PublicParametersKey)
        .getOrElse(new NCBTransactionIndexFIFO.PublicParameters)

    // local parameters
    protected def paramFIFOCapacity         = paramNCB.outstandingDepth


    /*
    * Port I/O: Allocate
    * 
    * @io input     en      : Allocate Enable.
    * @io input     index   : Allocate Index Value.
    */
    class AllocatePort extends Bundle {
        val en              = Input(Bool())
        val index           = Input(UInt(paramNCB.outstandingIndexWidth.W))
    }

    /*
    * Port I/O: Free
    * 
    * @io input     en      : Free Enable.
    */
    class FreePort extends Bundle {
        val en              = Input(Bool())
    }

    /*
    * Port I/O: Query
    * 
    * @io output    valid   : Query Read Valid.
    * @io output    index   : Query Read Index Value.
    */
    class QueryPort extends Bundle {
        val valid           = Output(Bool())
        val index           = Output(UInt(paramNCB.outstandingIndexWidth.W))
    }


    /*
    * Module I/O
    */
    val io = IO(new Bundle {
        // allocate port
        val allocate        = new AllocatePort

        // free port
        val free            = new FreePort

        // query port
        val query           = new QueryPort
    })


    // FIFO Pointers
    val regPointerWrite = RegInit(init = 1.U(paramNCB.outstandingDepth.W))
    val regPointerRead  = RegInit(init = 1.U(paramNCB.outstandingDepth.W))

     when (io.free.en) {
        regPointerRead  := regPointerRead.rotateLeft(1)
    }

    when (io.allocate.en) {
        regPointerWrite := regPointerWrite.rotateLeft(1)
    }

   
    // FIFO Registers
    val regValid        = RegInit(init = VecInit.fill(paramNCB.outstandingDepth)(false.B))
    val regData         = Reg(Vec(paramNCB.outstandingDepth, UInt(paramNCB.outstandingIndexWidth.W)))

    when (io.free.en) {

        regValid.zipWithIndex.map({ case (valid, i) => {
            (valid, regPointerRead(i))
        }}).foreach({ case (valid, read) => {
            when (read) {
                valid   := false.B
            }
        }})
    }

    when (io.allocate.en) {

        regValid.zip(regData).zipWithIndex.map({ case ((valid, data), i) => {
            (valid, data, regPointerWrite(i))
        }}).foreach({ case (valid, data, write) => {
            when (write) {
                valid   := true.B
                data    := io.allocate.index
            }
        }})
    }


    // output logic
    io.query.valid := ParallelMux(
        regValid.zipWithIndex.map({ case (valid, i) => {
            (regPointerRead(i), valid)
        }
    }))

    io.query.index := ParallelMux(
        regData.zipWithIndex.map({ case (data, i) => {
            (regPointerRead(i), data)
        }})
    )


    // assertions & debugs
    /* 
    * Port I/O: Debug
    */
    class DebugPort extends DebugBundle {
        val Underflow                       = Output(Bool())
        val Overflow                        = Output(Bool())
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    /*
    * @assertion Underflow
    *   FIFO underflow.
    */
    debug.Underflow := io.free.en && VecInit(ParallelMux(
        regPointerRead.asBools.zipWithIndex.map({ case (read, i) => {
            (read, !regValid(i))
        }})
    )).asUInt.orR
    assert(!debug.Underflow,
        "FIFO underflow")

    /*
    * @assertion Overflow
    *   FIFO overflow. 
    */
    debug.Overflow := io.allocate.en && VecInit(ParallelMux(
        regPointerWrite.asBools.zipWithIndex.map({ case (write, i) => {
            (write, regValid(i))
        }})
    )).asUInt.orR
    assert(!debug.Overflow,
        "FIFO overflow")
}
