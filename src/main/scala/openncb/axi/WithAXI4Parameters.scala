package cc.xiangshan.openncb.axi

import org.chipsalliance.cde.config.Parameters


trait WithAXI4Parameters {

    implicit val p: Parameters

    val paramAXI4 = p(AXI4ParametersKey)
}