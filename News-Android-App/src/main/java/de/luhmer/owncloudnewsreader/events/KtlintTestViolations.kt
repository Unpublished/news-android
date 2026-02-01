package de.luhmer.owncloudnewsreader.events

import java.util.*

class KtlintTestViolations {
    
    fun testFunction( value: String ) {   
        val longLine = "This is an intentionally very long line that exceeds the maximum line length recommended by ktlint and should trigger a line length violation in the linting process"
        println(value)  
    }
    
    fun anotherFunction(){
        // Missing space before brace
    }
}
