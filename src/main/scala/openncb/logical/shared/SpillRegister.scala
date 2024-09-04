package cn.rismd.openncb.logical.shared

import chisel3._
import chisel3.util._


/*
* Spill Register for pipelining ready signals.
*/
object SpillRegister {

    def apply[T <: DecoupledIO[Data]](in: T, out: T) = {
        val uSpillRegister = Module(new SpillRegister(out))
        uSpillRegister.io.in    <> in
        uSpillRegister.io.out   <> out
        uSpillRegister
    }

    def attachIn[T <: DecoupledIO[Data]](in: T): T = {
        val uSpillRegister = Module(new SpillRegister(in))
        uSpillRegister.io.in    <> in
        uSpillRegister.io.out.asInstanceOf[T]
    }

    def attachOut[T <: DecoupledIO[Data]](out: T): T = {
        val uSpillRegister = Module(new SpillRegister(out))
        uSpillRegister.io.out   <> out
        uSpillRegister.io.in.asInstanceOf[T]
    }
}

class SpillRegister[+T <: Data](gen: T) extends Module {

    /*
    * Module I/O
    * 
    * @io   input       in  : Input Decoupled Channel, ready-valid.
    * @io   output      out : Output Decoupeld Channel, ready-valid.
    */
    val io = IO(new Bundle {
        // upstream input
        val in              = {
            if (gen.isInstanceOf[DecoupledIO[Data]])
                Flipped(gen.asInstanceOf[DecoupledIO[Data]])
            else
                Flipped(Decoupled(gen))
        }

        // downstream output
        val out             = {
            if (gen.isInstanceOf[DecoupledIO[Data]])
                gen.asInstanceOf[DecoupledIO[Data]]
            else
                Decoupled(gen)
        }
    })

    
    //
    protected def extractDataType: Data   = io.out.bits.getClass().getConstructor().newInstance()

    
    // Spill Registers
    protected val reg1stValid   = RegInit(init = false.B)
    protected val reg1stData    = Reg(extractDataType)

    protected val reg2ndValid   = RegInit(init = false.B)
    protected val reg2ndData    = Reg(extractDataType)

    when (io.in.valid & io.in.ready) {
        reg1stValid := true.B
        reg1stData  := io.in.bits

        when (!io.out.ready) {
            reg2ndValid := reg1stValid
            reg2ndData  := reg1stData
        }
    }

    when (io.out.ready) {
        reg2ndValid := false.B
    }
    

    // output logic
    io.in.ready     := !reg2ndValid

    io.out.valid    := reg1stValid | reg2ndValid
    io.out.bits     := Mux(reg2ndValid, reg2ndData, reg1stData)
}
