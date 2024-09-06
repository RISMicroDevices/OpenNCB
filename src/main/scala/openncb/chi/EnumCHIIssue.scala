package cc.xiangshan.openncb.chi


/* 
* Enumeration of CHI Issue.
*/
sealed class EnumCHIIssue(ordinal           : Int, 
                         name               : String,
                         val canonicalName  : String) 
       extends Enum[EnumCHIIssue](name, ordinal)

object EnumCHIIssue {

    // Issue B
    val B: EnumCHIIssue     = new EnumCHIIssue(0, "B", "Issue B")

    // Issue E (implied to E.b)
    val E: EnumCHIIssue     = new EnumCHIIssue(0, "E", "Issue E.b")
}
