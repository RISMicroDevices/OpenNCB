package cn.rismd.openncb.chi.opcode

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.WithCHIParameters
import cn.rismd.openncb.chi.EnumCHIChannel
import cn.rismd.openncb.chi.opcode.CHIOpcode

/*
* CHI Opcode Decoder
* 
* @param paramChannel   Specify the targeted channel of CHI for this decoder.
* 
* @param paramOpcodeSupported   Specify all supported CHI Opcodes, all supported if empty.
* 
* @param paramOpcodeAll         Specify all CHI Opcodes.
* 
* @param paramEnableUnsupportedCheck    Whether enable assertions for unsupported CHI Opcodes.
*                                       Unsupported CHI Opcodes: CHI Opcodes exist in paramOpcodeAll
*                                                                but absent in paramOpcodeSupported.
*/
abstract class CHIOpcodeDecoder(val paramChannel                    : EnumCHIChannel,
                                val paramOpcodeSupported            : Seq[CHIOpcode],
                                val paramOpcodeAll                  : Seq[CHIOpcode],
                                val paramEnableUnsupportedCheck     : Boolean           = false)
    (implicit val p: Parameters) 
        extends Module with WithCHIParameters {

    //
    private def unknownChannel() = 
        throw new IllegalArgumentException(s"unknown or unsupported CHI Channel: ${paramChannel}")
    //

    // local parameters
    protected val paramOpcodeWidth  = paramChannel match {
        case EnumCHIChannel.REQ => paramCHI.reqOpcodeWidth
        case EnumCHIChannel.DAT => paramCHI.datOpcodeWidth
        case EnumCHIChannel.RSP => paramCHI.rspOpcodeWidth
        case EnumCHIChannel.SNP => paramCHI.snpOpcodeWidth
        case _: EnumCHIChannel  => unknownChannel
    }

    protected val paramDecodedWidth = 1 << paramOpcodeWidth


    /* 
    * Port I/O: debug
    */
    class DebugPort extends Bundle {
        val OpcodeUnsupported       = Output(Bool())
        val OpcodeUnknown           = Output(Bool())
    }
    

    /*
    * Module I/O:
    *
    * @io input     opcode      : CHI Opcode Input.
    * @io output    decoded     : CHI Decoded Onehot Output.
    */
    val io = IO(new Bundle {
        // opcode input
        val opcode      = Input(UInt(paramOpcodeWidth.W))

        // decoded output
        val decoded     = Output(Vec(paramDecodedWidth, Bool()))

        // debug port
        val debug       = new DebugPort
    })


    // default value and logic wires for opcode decoding
    protected val scalaSeqLogicDecoded   = Seq.fill(io.decoded.length)(Wire(Bool()))

    (0 until paramDecodedWidth).foreach(i => {
        scalaSeqLogicDecoded(i)  := false.B
    })

    scalaSeqLogicDecoded.foreach(u => {
        dontTouch(u)
    })


    // decoding supported CHI Opcodes
    paramOpcodeSupported.foreach(u => {

        if (u.applicable)
        {
            scalaSeqLogicDecoded(u.opcode)   := u.is(io.opcode)

            scalaSeqLogicDecoded(u.opcode).suggestName(s"decoded_${u.name}")
        }
    })

    // decoding (unsupported / all) CHI Opcodes
    protected var scalaSeqLogicUnsupported  = Seq[Bool]()

    paramOpcodeAll.foreach(u => {

        if (!paramOpcodeSupported.isEmpty)
        {
            if (u.applicable && !paramOpcodeSupported.contains(u))
            {
                scalaSeqLogicDecoded(u.opcode)   := u.is(io.opcode)

                if (paramEnableUnsupportedCheck)
                    assert(!scalaSeqLogicDecoded(u.opcode),
                        s"Unsupported CHI Opcode: ${u.name} (0x${u.opcode.toHexString})")

                scalaSeqLogicDecoded(u.opcode).suggestName(s"decoded_${u.name}_UNSUPPORTED")

                scalaSeqLogicUnsupported
                    = scalaSeqLogicUnsupported :+ scalaSeqLogicDecoded(u.opcode)
            }
        }
        else
        {
            if (u.applicable)
            {
                scalaSeqLogicDecoded(u.opcode)   := u.is(io.opcode)

                scalaSeqLogicDecoded(u.opcode).suggestName(s"decoded_${u.name}")
            }
        }
    })

    // decoding unknown CHI Opcodes
    protected var scalaSeqLogicUnknown  = Seq[Bool]()

    (0 until scalaSeqLogicDecoded.length).foreach(i => {
        if (!paramOpcodeAll.map(u => u.applicable && u.is(i)).reduce(_ || _))
        {
            scalaSeqLogicDecoded(i) := io.opcode === i.U

            assert(!scalaSeqLogicDecoded(i),
                s"Unknown CHI Opcode: 0x${i.toHexString}")

            scalaSeqLogicDecoded(i).suggestName(s"decoded_${i.toHexString}_UNKNOWN")

            scalaSeqLogicUnknown
                = scalaSeqLogicUnknown :+ scalaSeqLogicDecoded(i)
        }
    })

    
    // decoded output
    scalaSeqLogicDecoded.zipWithIndex.foreach(u => {
        io.decoded(u._2)    := u._1
    })

    
    // debug output
    if (scalaSeqLogicUnsupported.isEmpty)
        io.debug.OpcodeUnsupported  := false.B
    else
        io.debug.OpcodeUnsupported  := scalaSeqLogicUnsupported.reduce(_ || _)

    if (scalaSeqLogicUnknown.isEmpty)
        io.debug.OpcodeUnknown      := false.B
    else
        io.debug.OpcodeUnknown      := scalaSeqLogicUnknown.reduce(_ || _)


    // utility functions
    def is(opcode: CHIOpcode): Bool =
        if (opcode.applicable) io.decoded(opcode.opcode) else false.B
}
