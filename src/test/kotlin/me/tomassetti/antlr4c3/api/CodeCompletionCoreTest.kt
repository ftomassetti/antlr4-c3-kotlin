// This file is released under the MIT license.
// Copyright (c) 2016-2017, Mike Lischke, Federico Tomassetti
//
// See LICENSE file for more info.

package me.tomassetti.antlr4c3.api

import me.tomassetti.antlr4c3.*
import me.tomassetti.antlr4c3.SandyLexer.MINUS
import me.tomassetti.antlr4c3.SandyLexer.RPAREN
import me.tomassetti.antlr4c3.SandyParser.*
import kotlin.test.assertEquals
import org.junit.Test as test

class CodeCompletionCoreUsingSandyTest {

    fun tokenSuggested(code: String) : Set<TokenKind> {
        return me.tomassetti.antlr4c3.api.completions(code, SandyLexer::class.java, SandyParser::class.java)
    }

    fun tokenSuggestedWSP(code: String) : Set<TokenKind> {
        return me.tomassetti.antlr4c3.api.completionsIgnoringSemanticPredicates(code, SandyLexer::class.java, SandyParser::class.java)
    }

    fun tokenSuggestedWC(code: String) : CandidatesCollection {
        return me.tomassetti.antlr4c3.api.completionsWithContext(code, SandyLexer::class.java, SandyParser::class.java)
    }

    fun tokenSuggestedWSPWC(code: String) : CandidatesCollection {
        return me.tomassetti.antlr4c3.api.completionsWithContextIgnoringSemanticPredicates(code, SandyLexer::class.java, SandyParser::class.java)
    }

    @test fun emptyFile() {
        val code = ""
        assertEquals(setOf(SandyLexer.VAR, SandyLexer.ID), tokenSuggested(code))
    }

    @test fun emptyFileWC() {
        val code = ""
        val res = tokenSuggestedWSPWC(code)
        assertEquals(listOf(SandyParser.RULE_sandyFile, SandyParser.RULE_line, SandyParser.RULE_statement, SandyParser.RULE_varDeclaration), res.tokensContext[SandyLexer.VAR])
        assertEquals(listOf(SandyParser.RULE_sandyFile, SandyParser.RULE_line, SandyParser.RULE_statement, SandyParser.RULE_assignment), res.tokensContext[SandyLexer.ID])
    }

    @test fun afterVar() {
        val code = "var"
        assertEquals(setOf(SandyLexer.ID), tokenSuggested(code))
    }

    @test fun afterVarWC() {
        val code = "var"
        val res = tokenSuggestedWSPWC(code)
        assertEquals(listOf(SandyParser.RULE_sandyFile, RULE_line, RULE_statement, RULE_varDeclaration, RULE_assignment),
                res.tokensContext[SandyLexer.ID])
    }

    @test fun afterEquals() {
        val code = "var a ="
        assertEquals(setOf(SandyLexer.INTLIT, SandyLexer.DECLIT, MINUS
                , SandyLexer.LPAREN, SandyLexer.ID), tokenSuggested(code))
    }

    @test fun afterLiteral() {
        val code = "var a = 1"
        assertEquals(setOf(SandyLexer.NEWLINE, SandyLexer.EOF, SandyLexer.PLUS,
                MINUS, SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggested(code))
    }

    @test fun incompleteAddition() {
        val code = "var a = 1 +"
        assertEquals(setOf(SandyLexer.LPAREN, SandyLexer.ID, MINUS,
                SandyLexer.INTLIT, SandyLexer.DECLIT), tokenSuggested(code))
    }

    @test fun incompleteParenthesis() {
        val code = "var a = (1"
        assertEquals(setOf(RPAREN, SandyLexer.PLUS, MINUS,
                SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggested(code))
    }

    @test fun incompleteParenthesisWC() {
        val code = "var a = (1"
        val res = tokenSuggestedWSPWC(code)
        assertEquals(listOf(SandyParser.RULE_sandyFile, RULE_line, RULE_statement, RULE_varDeclaration, RULE_assignment,
                RULE_expression, RULE_expression),
                res.tokensContext[RPAREN])
        assertEquals(listOf(SandyParser.RULE_sandyFile, RULE_line, RULE_statement, RULE_varDeclaration, RULE_assignment,
                RULE_expression, RULE_expression),
                res.tokensContext[SandyLexer.PLUS])
        assertEquals(listOf(SandyParser.RULE_sandyFile, RULE_line, RULE_statement, RULE_varDeclaration, RULE_assignment,
                RULE_expression, RULE_expression),
                res.tokensContext[MINUS])
        assertEquals(listOf(SandyParser.RULE_sandyFile, RULE_line, RULE_statement, RULE_varDeclaration, RULE_assignment,
                RULE_expression, RULE_expression),
                res.tokensContext[SandyLexer.DIVISION])
        assertEquals(listOf(SandyParser.RULE_sandyFile, RULE_line, RULE_statement, RULE_varDeclaration, RULE_assignment,
                RULE_expression, RULE_expression),
                res.tokensContext[SandyLexer.ASTERISK])
    }

    @test fun incompleteComplexParenthesis() {
        val code = "var a = (1+1"
        assertEquals(setOf(RPAREN, SandyLexer.PLUS, MINUS,
                SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggested(code))
    }

    @test fun incompleteMoreComplexParenthesis() {
        val code = "var a = (1+1*"
        assertEquals(setOf(SandyLexer.LPAREN, SandyLexer.ID, MINUS,
                SandyLexer.INTLIT, SandyLexer.DECLIT),
                tokenSuggested(code))
    }

    @test fun startedParenthesis() {
        val code = "var a = ("
        assertEquals(setOf(SandyLexer.LPAREN, SandyLexer.INTLIT, MINUS,
                SandyLexer.DECLIT, SandyLexer.ID),
                tokenSuggested(code))
    }

    @test fun incompleteAnnidatedParenthesis() {
        val code = "var a = ((1+1)"
        assertEquals(setOf(RPAREN, SandyLexer.PLUS, MINUS,
                SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggested(code))
    }

    @test fun completeAnnidatedParenthesis() {
        val code = "var a = ((1))"
        assertEquals(setOf(SandyLexer.NEWLINE, SandyLexer.EOF,
                SandyLexer.PLUS, MINUS,
                SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggested(code))
    }

    @test fun emptyFileWithoutSemanticPredicates() {
        val code = ""
        assertEquals(setOf(SandyLexer.VAR, SandyLexer.ID), tokenSuggestedWSP(code))
    }

    @test fun afterVarWithoutSemanticPredicates() {
        val code = "var"
        assertEquals(setOf(SandyLexer.ID), tokenSuggestedWSP(code))
    }

    @test fun afterEqualsWithoutSemanticPredicates() {
        val code = "var a ="
        assertEquals(setOf(SandyLexer.INTLIT, SandyLexer.DECLIT, MINUS
                , SandyLexer.LPAREN, SandyLexer.ID), tokenSuggestedWSP(code))
    }

    @test fun afterLiteralWithoutSemanticPredicates() {
        val code = "var a = 1"
        assertEquals(setOf(SandyLexer.NEWLINE, SandyLexer.EOF, SandyLexer.PLUS,
                MINUS, SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggestedWSP(code))
    }

    @test fun incompleteAdditionWithoutSemanticPredicates() {
        val code = "var a = 1 +"
        assertEquals(setOf(SandyLexer.LPAREN, SandyLexer.ID, MINUS,
                SandyLexer.INTLIT, SandyLexer.DECLIT), tokenSuggestedWSP(code))
    }

    @test fun incompleteParenthesisWithoutSemanticPredicates() {
        val code = "var a = (1"
        assertEquals(setOf(RPAREN, SandyLexer.PLUS, MINUS,
                SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggestedWSP(code))
    }

    @test fun incompleteComplexParenthesisWithoutSemanticPredicates() {
        val code = "var a = (1+1"
        assertEquals(setOf(RPAREN, SandyLexer.PLUS, MINUS,
                SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggestedWSP(code))
    }

    @test fun incompleteMoreComplexParenthesisWithoutSemanticPredicates() {
        val code = "var a = (1+1*"
        assertEquals(setOf(SandyLexer.LPAREN, SandyLexer.ID, MINUS,
                SandyLexer.INTLIT, SandyLexer.DECLIT),
                tokenSuggestedWSP(code))
    }

    @test fun startedParenthesisWithoutSemanticPredicates() {
        val code = "var a = ("
        assertEquals(setOf(SandyLexer.LPAREN, SandyLexer.INTLIT, MINUS,
                SandyLexer.DECLIT, SandyLexer.ID),
                tokenSuggestedWSP(code))
    }

    @test fun incompleteAnnidatedParenthesisWithoutSemanticPredicates() {
        val code = "var a = ((1+1)"
        assertEquals(setOf(RPAREN, SandyLexer.PLUS, MINUS,
                SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggestedWSP(code))
    }

    @test fun completeAnnidatedParenthesisWithoutSemanticPredicates() {
        val code = "var a = ((1))"
        assertEquals(setOf(SandyLexer.NEWLINE, SandyLexer.EOF,
                SandyLexer.PLUS, MINUS,
                SandyLexer.DIVISION, SandyLexer.ASTERISK),
                tokenSuggestedWSP(code))
    }
}

class CodeCompletionCoreUsingStaMacTest {

    fun tokenSuggestedWC(code: String) : CandidatesCollection {
        return me.tomassetti.antlr4c3.api.completionsWithContext(code, StaMacLexer::class.java, StaMacParser::class.java)
    }

    fun tokenSuggestedWSPWC(code: String) : CandidatesCollection {
        return me.tomassetti.antlr4c3.api.completionsWithContextIgnoringSemanticPredicates(code, StaMacLexer::class.java, StaMacParser::class.java)
    }

    @test fun emptyFileWC() {
        val code = ""
        val completion = tokenSuggestedWSPWC(code)
        assertEquals(setOf(StaMacLexer.SM), completion.tokens.keys)
        assertEquals(listOf(StaMacParser.RULE_stateMachine, StaMacParser.RULE_preamble),
                completion.tokensContext[StaMacLexer.SM])
    }

}
