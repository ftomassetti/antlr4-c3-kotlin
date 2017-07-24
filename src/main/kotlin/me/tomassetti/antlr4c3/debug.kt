// This file is released under the MIT license.
// Copyright (c) 2016-2017, Mike Lischke, Federico Tomassetti
//
// See LICENSE file for more info.

package me.tomassetti.antlr4c3

import org.antlr.v4.runtime.atn.ATNState

fun ATNState.describe(ruleNames: Array<String>) : String {
    return "[${this.stateNumber}] ${ruleNames[this.ruleIndex]} ${this.stateType} ${this.javaClass.simpleName}"
}
