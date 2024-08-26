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
    })


    // default value and logic wires for opcode decoding
    protected val seqLogicDecoded   = Seq.fill(io.decoded.length)(Wire(Bool()))

    (0 until paramDecodedWidth).foreach(i => {
        seqLogicDecoded(i)  := false.B
    })

    seqLogicDecoded.foreach(u => {
        dontTouch(u)
    })


    // decoding supported CHI Opcodes
    paramOpcodeSupported.foreach(u => {

        if (u.applicable)
        {
            seqLogicDecoded(u.opcode)   := u.is(io.opcode)

            seqLogicDecoded(u.opcode).suggestName(s"decoded_${u.name}")
        }
    })

    // decoding (unsupported / all) CHI Opcodes
    paramOpcodeAll.foreach(u => {

        if (!paramOpcodeSupported.isEmpty)
        {
            if (u.applicable && !paramOpcodeSupported.contains(u))
            {
                seqLogicDecoded(u.opcode)   := u.is(io.opcode)

                if (paramEnableUnsupportedCheck)
                    assert(!seqLogicDecoded(u.opcode),
                        s"Unsupported CHI Opcode: ${u.name} (0x${u.opcode.toHexString})")

                seqLogicDecoded(u.opcode).suggestName(s"decoded_${u.name}_UNSUPPORTED")
            }
        }
        else
        {
            if (u.applicable)
            {
                seqLogicDecoded(u.opcode)   := u.is(io.opcode)

                seqLogicDecoded(u.opcode).suggestName(s"decoded_${u.name}")
            }
        }
    })

    // decoding unknown CHI Opcodes
    (0 until seqLogicDecoded.length).foreach(i => {
        if (!paramOpcodeAll.map(u => u.applicable && u.is(i)).reduce(_ || _))
        {
            seqLogicDecoded(i)  := io.opcode === i.U

            assert(!seqLogicDecoded(i),
                s"Unknown CHI Opcode: 0x${i.toHexString}")

            seqLogicDecoded(i).suggestName(s"decoded_${i.toHexString}_UNKNOWN")
        }
    })

    
    // decoded output
    seqLogicDecoded.zipWithIndex.foreach(u => {
        io.decoded(u._2)    := u._1
    })


    // utility functions
    def is(opcode: CHIOpcode): Bool =
        if (opcode.applicable) io.decoded(opcode.opcode) else false.B
}
