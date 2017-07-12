import me.tomassetti.antlr4c3.SandyLexer
import me.tomassetti.antlr4c3.SandyParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import kotlin.test.assertEquals
import org.junit.Test as test

//class MyEditorContextImpl(val code: String, val antlrLexerFactory: AntlrLexerFactory, val ast: Node? = null) : EditorContext {
//    override fun cachedAst(): Node? {
//        return ast
//    }
//
//    override fun preceedingTokens(): List<Token> {
//        val lexer = antlrLexerFactory.create(code)
//        return lexer.toList()
//    }
//}

data class TokenTypeImpl(val type: Int)

class CodeCompletionCoreTest {

//    fun tokenSuggested(code: String) = AutoCompletionContextProvider(
//            SandyParser.ruleNames, SandyParser.VOCABULARY, SandyParser._ATN, debugging = Debugging.AT_CARET)
//            .autoCompletionContext(MyEditorContextImpl(code, sandyLanguageSupport.antlrLexerFactory))
//            .proposals
//            .map { it.first }.toSet()

    fun tokenSuggested(code: String) : Set<TokenTypeImpl> {
        val lexer = SandyLexer(ANTLRInputStream(ByteArrayInputStream(code.toByteArray(Charset.defaultCharset()))))
        val parser = SandyParser(CommonTokenStream(lexer))
        val codeCompletionCode = CodeCompletionCore(parser)
        codeCompletionCode.enableDebug()
        val results = codeCompletionCode.collectCandidates(code.length, null)
        return results.tokens.keys.map { TokenTypeImpl(it) }.toSet()
    }

    @test fun emptyFile() {
        val code = ""
        assertEquals(setOf(TokenTypeImpl(SandyLexer.VAR), TokenTypeImpl(SandyLexer.ID)), tokenSuggested(code))
    }
//
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

}
