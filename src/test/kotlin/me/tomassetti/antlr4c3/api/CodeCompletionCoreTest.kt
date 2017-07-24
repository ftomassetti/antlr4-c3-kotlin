// This file is released under the MIT license.
// Copyright (c) 2016-2017, Mike Lischke, Federico Tomassetti
//
// See LICENSE file for more info.

package me.tomassetti.antlr4c3.api

import me.tomassetti.antlr4c3.SandyLexer
import me.tomassetti.antlr4c3.SandyParser
import kotlin.test.assertEquals
import org.junit.Test as test

class CodeCompletionCoreTest {

    fun tokenSuggested(code: String) : Set<TokenTypeImpl> {
        return me.tomassetti.antlr4c3.api.tokenSuggested(code, SandyLexer::class.java, SandyParser::class.java)
    }

    fun tokenSuggestedWSP(code: String) : Set<TokenTypeImpl> {
        return me.tomassetti.antlr4c3.api.tokenSuggestedWithoutSemanticPredicates(code, SandyLexer::class.java, SandyParser::class.java)
    }

    @test fun emptyFile() {
        val code = ""
        assertEquals(setOf(TokenTypeImpl(SandyLexer.VAR), TokenTypeImpl(SandyLexer.ID)), tokenSuggested(code))
    }

    @test fun afterVar() {
        val code = "var"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.ID)), tokenSuggested(code))
    }

    @test fun afterEquals() {
        val code = "var a ="
        assertEquals(setOf(TokenTypeImpl(SandyLexer.INTLIT), TokenTypeImpl(SandyLexer.DECLIT), TokenTypeImpl(SandyLexer.MINUS)
                , TokenTypeImpl(SandyLexer.LPAREN), TokenTypeImpl(SandyLexer.ID)), tokenSuggested(code))
    }

    @test fun afterLiteral() {
        val code = "var a = 1"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.NEWLINE), TokenTypeImpl(SandyLexer.EOF), TokenTypeImpl(SandyLexer.PLUS),
                TokenTypeImpl(SandyLexer.MINUS), TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggested(code))
    }

    @test fun incompleteAddition() {
        val code = "var a = 1 +"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.LPAREN), TokenTypeImpl(SandyLexer.ID), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.INTLIT), TokenTypeImpl(SandyLexer.DECLIT)), tokenSuggested(code))
    }

    @test fun incompleteParenthesis() {
        val code = "var a = (1"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.RPAREN), TokenTypeImpl(SandyLexer.PLUS), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggested(code))
    }

    @test fun incompleteComplexParenthesis() {
        val code = "var a = (1+1"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.RPAREN), TokenTypeImpl(SandyLexer.PLUS), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggested(code))
    }

    @test fun incompleteMoreComplexParenthesis() {
        val code = "var a = (1+1*"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.LPAREN), TokenTypeImpl(SandyLexer.ID), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.INTLIT), TokenTypeImpl(SandyLexer.DECLIT)),
                tokenSuggested(code))
    }

    @test fun startedParenthesis() {
        val code = "var a = ("
        assertEquals(setOf(TokenTypeImpl(SandyLexer.LPAREN), TokenTypeImpl(SandyLexer.INTLIT), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DECLIT), TokenTypeImpl(SandyLexer.ID)),
                tokenSuggested(code))
    }

    @test fun incompleteAnnidatedParenthesis() {
        val code = "var a = ((1+1)"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.RPAREN), TokenTypeImpl(SandyLexer.PLUS), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggested(code))
    }

    @test fun completeAnnidatedParenthesis() {
        val code = "var a = ((1))"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.NEWLINE), TokenTypeImpl(SandyLexer.EOF),
                TokenTypeImpl(SandyLexer.PLUS), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggested(code))
    }

    @test fun emptyFileWithoutSemanticPredicates() {
        val code = ""
        assertEquals(setOf(TokenTypeImpl(SandyLexer.VAR), TokenTypeImpl(SandyLexer.ID)), tokenSuggestedWSP(code))
    }

    @test fun afterVarWithoutSemanticPredicates() {
        val code = "var"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.ID)), tokenSuggestedWSP(code))
    }

    @test fun afterEqualsWithoutSemanticPredicates() {
        val code = "var a ="
        assertEquals(setOf(TokenTypeImpl(SandyLexer.INTLIT), TokenTypeImpl(SandyLexer.DECLIT), TokenTypeImpl(SandyLexer.MINUS)
                , TokenTypeImpl(SandyLexer.LPAREN), TokenTypeImpl(SandyLexer.ID)), tokenSuggestedWSP(code))
    }

    @test fun afterLiteralWithoutSemanticPredicates() {
        val code = "var a = 1"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.NEWLINE), TokenTypeImpl(SandyLexer.EOF), TokenTypeImpl(SandyLexer.PLUS),
                TokenTypeImpl(SandyLexer.MINUS), TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggestedWSP(code))
    }

    @test fun incompleteAdditionWithoutSemanticPredicates() {
        val code = "var a = 1 +"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.LPAREN), TokenTypeImpl(SandyLexer.ID), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.INTLIT), TokenTypeImpl(SandyLexer.DECLIT)), tokenSuggestedWSP(code))
    }

    @test fun incompleteParenthesisWithoutSemanticPredicates() {
        val code = "var a = (1"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.RPAREN), TokenTypeImpl(SandyLexer.PLUS), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggestedWSP(code))
    }

    @test fun incompleteComplexParenthesisWithoutSemanticPredicates() {
        val code = "var a = (1+1"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.RPAREN), TokenTypeImpl(SandyLexer.PLUS), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggestedWSP(code))
    }

    @test fun incompleteMoreComplexParenthesisWithoutSemanticPredicates() {
        val code = "var a = (1+1*"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.LPAREN), TokenTypeImpl(SandyLexer.ID), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.INTLIT), TokenTypeImpl(SandyLexer.DECLIT)),
                tokenSuggestedWSP(code))
    }

    @test fun startedParenthesisWithoutSemanticPredicates() {
        val code = "var a = ("
        assertEquals(setOf(TokenTypeImpl(SandyLexer.LPAREN), TokenTypeImpl(SandyLexer.INTLIT), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DECLIT), TokenTypeImpl(SandyLexer.ID)),
                tokenSuggestedWSP(code))
    }

    @test fun incompleteAnnidatedParenthesisWithoutSemanticPredicates() {
        val code = "var a = ((1+1)"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.RPAREN), TokenTypeImpl(SandyLexer.PLUS), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggestedWSP(code))
    }

    @test fun completeAnnidatedParenthesisWithoutSemanticPredicates() {
        val code = "var a = ((1))"
        assertEquals(setOf(TokenTypeImpl(SandyLexer.NEWLINE), TokenTypeImpl(SandyLexer.EOF),
                TokenTypeImpl(SandyLexer.PLUS), TokenTypeImpl(SandyLexer.MINUS),
                TokenTypeImpl(SandyLexer.DIVISION), TokenTypeImpl(SandyLexer.ASTERISK)),
                tokenSuggestedWSP(code))
    }
}
