// This file is released under the MIT license.
// Copyright (c) 2016-2017, Mike Lischke, Federico Tomassetti
//
// See LICENSE file for more info.

package me.tomassetti.antlr4c3.api

import me.tomassetti.antlr4c3.ByParserClassTokenProvider
import me.tomassetti.antlr4c3.CodeCompletionCore
import org.antlr.v4.runtime.*
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

data class TokenTypeImpl(val type: Int)

fun <L : Lexer, P : Parser> tokenSuggested(code: String, lexerClass: Class<L>, parserClass: Class<P>) : Set<TokenTypeImpl> {
    val lexerConstructor = lexerClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == CharStream::class.java }!!
    val charStream = ANTLRInputStream(ByteArrayInputStream(code.toByteArray(Charset.defaultCharset())))
    val lexer = lexerConstructor.newInstance(charStream) as Lexer

    val parserConstructor = parserClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == TokenStream::class.java }!!
    val parser = parserConstructor.newInstance(CommonTokenStream(lexer)) as Parser
    val codeCompletionCode = CodeCompletionCore.fromParser(parser)

    val results = codeCompletionCode.collectCandidates(parser.tokenStream, code.length)
    return results.tokens.keys.map { TokenTypeImpl(it) }.toSet()
}

fun <L : Lexer, P : Parser> tokenSuggestedWithoutSemanticPredicates(code: String, lexerClass: Class<L>, parserClass: Class<P>) : Set<TokenTypeImpl> {
    val codeCompletionCode = CodeCompletionCore.fromParserClass(parserClass)

    val results = codeCompletionCode.collectCandidates(ByParserClassTokenProvider(lexerClass, parserClass, code))
    return results.tokens.keys.map { TokenTypeImpl(it) }.toSet()
}