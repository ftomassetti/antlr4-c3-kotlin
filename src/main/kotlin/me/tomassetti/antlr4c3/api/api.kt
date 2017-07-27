// This file is released under the MIT license.
// Copyright (c) 2016-2017, Mike Lischke, Federico Tomassetti
//
// See LICENSE file for more info.

package me.tomassetti.antlr4c3.api

import me.tomassetti.antlr4c3.ByParserClassTokenProvider
import me.tomassetti.antlr4c3.CandidatesCollection
import me.tomassetti.antlr4c3.CodeCompletionCore
import org.antlr.v4.runtime.*
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

data class TokenTypeImpl(val type: Int)

fun <L : Lexer, P : Parser> tokensSuggestedWithContext(code: String, lexerClass: Class<L>, parserClass: Class<P>) : CandidatesCollection {
    val lexerConstructor = lexerClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == CharStream::class.java }!!
    val charStream = ANTLRInputStream(ByteArrayInputStream(code.toByteArray(Charset.defaultCharset())))
    val lexer = lexerConstructor.newInstance(charStream) as Lexer

    val parserConstructor = parserClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == TokenStream::class.java }!!
    val parser = parserConstructor.newInstance(CommonTokenStream(lexer)) as Parser
    val codeCompletionCode = CodeCompletionCore.fromParser(parser)

    return codeCompletionCode.collectCandidates(parser.tokenStream, code.length)
}

fun <L : Lexer, P : Parser> tokensSuggested(code: String, lexerClass: Class<L>, parserClass: Class<P>) : Set<TokenTypeImpl> {
    return tokensSuggestedWithContext(code, lexerClass, parserClass).tokens.keys.map { TokenTypeImpl(it) }.toSet()
}

fun <L : Lexer, P : Parser> tokenSuggestedWithoutSemanticPredicatesWithContext(code: String, lexerClass: Class<L>, parserClass: Class<P>) : CandidatesCollection {
    val codeCompletionCode = CodeCompletionCore.fromParserClass(parserClass)

    return codeCompletionCode.collectCandidates(ByParserClassTokenProvider(lexerClass, parserClass, code))
}

fun <L : Lexer, P : Parser> tokenSuggestedWithoutSemanticPredicates(code: String, lexerClass: Class<L>, parserClass: Class<P>) : Set<TokenTypeImpl> {
    return tokenSuggestedWithoutSemanticPredicatesWithContext(code, lexerClass, parserClass).tokens.keys.map { TokenTypeImpl(it) }.toSet()
}
