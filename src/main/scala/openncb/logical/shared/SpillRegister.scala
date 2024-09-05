package cn.rismd.openncb.logical.shared

import chisel3._
import chisel3.util._


/*
* Spill Register for pipelining ready signals.
*/
object SpillRegister {

    def apply[T <: Data](gen: T) = {
        require(!gen.isInstanceOf[DecoupledIO[?]],
            "use 'applyDecoupled(...)' instead when passing in DecoupledIO")
        Module(new SpillRegister[T, T](gen))
    }

    def applyDecoupled[D <: Data](gen: DecoupledIO[D]) = {
        Module(new SpillRegister[D, DecoupledIO[D]](gen))
    }

    def attachDecoupled[D <: DecoupledIO[D]](in: DecoupledIO[D], out: DecoupledIO[D]) = {
        val uSpillRegister = Module(new SpillRegister(
            chiselTypeOf(out)
        ))
        uSpillRegister.io.in    <> in
        uSpillRegister.io.out   <> out
        uSpillRegister
    }

    def attachIn[T <: DecoupledIO[Data]](in: T): T = {
        val uSpillRegister = Module(new SpillRegister(
            chiselTypeOf(in)
        ))
        uSpillRegister.io.in    <> in
        uSpillRegister.io.out.asInstanceOf[T]
    }

    def attachOut[T <: DecoupledIO[Data]](out: T): T = {
        val uSpillRegister = Module(new SpillRegister(
            chiselTypeOf(out)
        ))
        uSpillRegister.io.out   <> out
        uSpillRegister.io.in.asInstanceOf[T]
    }
}

class SpillRegister[+D <: Data, +T <: Data](gen: T) extends Module {

    /*
    * Module I/O
    * 
    * @io   input       in  : Input Decoupled Channel, ready-valid.
    * @io   output      out : Output Decoupeld Channel, ready-valid.
    */
    val io = IO(new Bundle {
        // upstream input
        val in              : DecoupledIO[D] = {
            if (gen.isInstanceOf[DecoupledIO[Data]])
                Flipped(gen.asInstanceOf[Data]).asInstanceOf[DecoupledIO[D]]
            else
                Flipped(Decoupled(gen)).asInstanceOf[DecoupledIO[D]]
        }

        // downstream output
        val out             : DecoupledIO[D] = {
            if (gen.isInstanceOf[DecoupledIO[Data]])
                Flipped(Flipped(gen.asInstanceOf[DecoupledIO[D]]))
            else
                Flipped(Flipped(Decoupled(gen).asInstanceOf[DecoupledIO[D]]))
        }
    })

    
    //
    protected def extractDataType: Data   = chiselTypeOf(io.out.bits)

    
    // Spill Registers
    protected val reg1stValid   = RegInit(init = false.B)
    protected val reg1stData    = Reg(extractDataType)

    protected val reg2ndValid   = RegInit(init = false.B)
    protected val reg2ndData    = Reg(extractDataType)

    when (io.out.ready) {

        when (!reg2ndValid) {
            reg1stValid := false.B
        }

        reg2ndValid := false.B
    }

    when (io.in.fire) {
        reg1stValid := true.B
        reg1stData  := io.in.bits

        when (!io.out.ready) {
            reg2ndValid := reg1stValid
            reg2ndData  := reg1stData
        }
    }

    
    // output logic
    io.in.ready     := !reg2ndValid

    io.out.valid    := reg1stValid | reg2ndValid
    io.out.bits     := Mux(reg2ndValid, reg2ndData, reg1stData)
}
