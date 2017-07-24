import org.antlr.v4.runtime.*
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

data class TokenTypeImpl(val type: Int)

fun <L : Lexer, P : Parser> tokenSuggested(code: String, lexerClass: Class<L>, parserClass: Class<P>) : Set<TokenTypeImpl> {
    val lexerConstructor = lexerClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == CharStream::class.java }!!
    var charStream = ANTLRInputStream(ByteArrayInputStream(code.toByteArray(Charset.defaultCharset())))
    val lexer = lexerConstructor.newInstance(charStream) as Lexer

    var parserConstructor = parserClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == TokenStream::class.java }!!
    val parser = parserConstructor.newInstance(CommonTokenStream(lexer)) as Parser
    val codeCompletionCode = CodeCompletionCore(parser)

    val results = codeCompletionCode.collectCandidates(code.length, null)
    return results.tokens.keys.map { TokenTypeImpl(it) }.toSet()
}
