// This file is released under the MIT license.
// Copyright (c) 2016-2017, Mike Lischke, Federico Tomassetti
//
// See LICENSE file for more info.

package me.tomassetti.antlr4c3.api

import me.tomassetti.antlr4c3.*
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.atn.ATN
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

fun <L : Lexer, P : Parser> completionsWithContext(code: String, lexerClass: Class<L>, parserClass: Class<P>) : CandidatesCollection {
    val lexerConstructor = lexerClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == CharStream::class.java }!!
    val charStream = ANTLRInputStream(ByteArrayInputStream(code.toByteArray(Charset.defaultCharset())))
    val lexer = lexerConstructor.newInstance(charStream) as Lexer

    val parserConstructor = parserClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == TokenStream::class.java }!!
    val parser = parserConstructor.newInstance(CommonTokenStream(lexer)) as Parser
    val codeCompletionCode = CodeCompletionCore.fromParser(parser)

    return codeCompletionCode.collectCandidates(parser.tokenStream, code.length)
}

fun <L : Lexer, P : Parser> completions(code: String, lexerClass: Class<L>, parserClass: Class<P>) : Set<TokenKind> {
    return completionsWithContext(code, lexerClass, parserClass).tokens.keys.toSet()
}

fun <L : Lexer, P : Parser> completionsWithContextIgnoringSemanticPredicates(code: String, lexerClass: Class<L>, parserClass: Class<P>) : CandidatesCollection {
    val codeCompletionCode = CodeCompletionCore.fromParserClass(parserClass)

    return codeCompletionCode.collectCandidates(ByParserClassTokenProvider(lexerClass, parserClass, code))
}

fun completionsWithContextIgnoringSemanticPredicates(tokens: List<TokenKind>, atn: ATN, vocabulary: Vocabulary, ruleNames: Array<String>,
                                                     languageName : String) : CandidatesCollection {
    val codeCompletionCode = CodeCompletionCore(atn, vocabulary, ruleNames, languageName)

    return codeCompletionCode.collectCandidates(ByListTokenProvider(tokens))
}

fun <L : Lexer, P : Parser> completionsIgnoringSemanticPredicates(code: String, lexerClass: Class<L>, parserClass: Class<P>) : Set<TokenKind> {
    return completionsWithContextIgnoringSemanticPredicates(code, lexerClass, parserClass).tokens.keys
}
