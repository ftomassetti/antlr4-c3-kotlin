# ANTLR4-C3-Kotlin

[![Build Status](https://travis-ci.org/ftomassetti/antlr4-c3-kotlin.svg?branch=master)](https://travis-ci.org/ftomassetti/antlr4-c3-kotlin)

_The original version of this project has been created by translating to Kotlin [antlr4-c3](https://github.com/mike-lischke/antlr4-c3), a project by Mike Lischke._

This library provides a grammar agnostic code completion engine for ANTLR4 based parsers, written in Kotlin. The c3 engine is able to provide code completion candidates useful for editors with ANTLR generated parsers, independent of the actual language/grammar used for the generation.

Being written in Kotlin the idea is to be reuse the same Kotlin on the JVM and in the browser.

## API

The high level API can be used very simply by specifying the lexer class, the parser class and the code to be completed. It will return the set of token types that are expected:

```kotlin
val expectedTokenTypes = tokenSuggested("var a = 1 +", SandyLexer::class.java, SandyParser::class.java)
```

In this case the result will be a list of token types representing:

* a left parenthesis
* the minus sign
* an ID
* an integer literal
* a decimal literal

## License

This code is released under the MIT License
