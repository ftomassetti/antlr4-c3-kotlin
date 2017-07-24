// This file is released under the MIT license.
// Copyright (c) 2016-2017, Mike Lischke, Federico Tomassetti
//
// See LICENSE file for more info.

package me.tomassetti.antlr4c3

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.atn.*
import org.antlr.v4.runtime.misc.IntervalSet
import java.util.*
import kotlin.collections.HashSet

typealias TokenKind = Int
typealias RuleIndex = Int
typealias TokenList = MutableList<TokenKind>
typealias RuleList = MutableList<RuleIndex>

// All the candidates which have been found. Tokens and rules are separated (both use a numeric value).
// Token entries include a list of tokens that directly follow them (see also the "following" member in the FollowSetWithPath class).
class CandidatesCollection {
    // The map keys are the lexer tokens
    // The list consists of further token ids which directly follow the given token in the grammar (if any)
    var tokens: MutableMap<Int, TokenList> = HashMap()
    // The map keys are the rule indices
    // The list represents the call stack at which the given rule was found during evaluation
    // This allows to determine a context for rules that are used in different places
    var rules: MutableMap<Int, RuleList> = HashMap()

    override fun toString(): String {
        return "CandidatesCollection(tokens=$tokens, rules=$rules)"
    }

}

// A record for a follow set along with the path at which this set was found.
// If there is only a single symbol in the interval set then we also collect and store tokens which follow
// this symbol directly in its rule (i.e. there is no intermediate rule transition). Only single label transitions
// are considered. This is useful if you have a chain of tokens which can be suggested as a whole, because there is
// a fixed sequence in the grammar.

private class FollowSetWithPath {
    var intervals : IntervalSet? = null
    var path : RuleList = LinkedList()
    var following : TokenList = LinkedList()

    override fun toString(): String {
        return "FollowSetWithPath(intervals=$intervals, path=$path, following=$following)"
    }

}

// A list of follow sets (for a given state number) + all of them combined for quick hit tests.
// This data is static in nature (because the used ATN states are part of a static struct: the ATN).
// Hence it can be shared between all C3 instances, however it dependes on the actual parser class (type).
private class FollowSetsHolder {
    var sets : MutableList<FollowSetWithPath> = LinkedList()
    var combined : IntervalSet? = null

    override fun toString(): String {
        return "FollowSetsHolder(sets=$sets, combined=$combined)"
    }

}

private typealias FollowSetsPerState = MutableMap<Int, FollowSetsHolder>

// Token stream position info after a rule was processed.
private typealias RuleEndStatus = MutableSet<Int>

private data class PipelineEntry(val state: ATNState, val tokenIndex: Int)

fun ATNState.describe(ruleNames: Array<String>) : String {
    return "[${this.stateNumber}] ${ruleNames[this.ruleIndex]} ${this.stateType} ${this.javaClass.simpleName}"
}

// The main class for doing the collection process.
class CodeCompletionCore(val parser: Parser) {

    // Debugging options. Print human readable ATN state and other info.
    private var showResult = false                 // Not dependent on showDebugOutput. Prints the collected rules + tokens to terminal.
    private var showDebugOutput = false            // Enables printing ATN state info to terminal.
    private var debugOutputWithTransitions = false // Only relevant when showDebugOutput is true. Enables transition printing for a state.
    private var showRuleStack = false              // Also depends on showDebugOutput. Enables call stack printing for each rule recursion.

    fun enableDebug() {
        showResult = true
        showDebugOutput = true
        debugOutputWithTransitions = true
        showRuleStack = true
    }

    // Tailoring of the result.
    private val ignoredTokens = HashSet<Int>()        // Tokens which should not appear in the candidates set.
    private val preferredRules = HashSet<Int>()       // Rules which replace any candidate token they contain.
    // This allows to return descriptive rules (e.g. className, instead of ID/identifier).

    // parser is in the primary constructor
    private val atn = parser.atn
    private val vocabulary = parser.vocabulary
    private val ruleNames = parser.ruleNames
    private var tokens : TokenList = LinkedList()

    private var tokenStartIndex = 0
    private var statesProcessed = 0

    // A mapping of rule index + token stream position to end token positions.
    // A rule which has been visited before with the same input position will always produce the same output positions.
    private val shortcutMap : MutableMap<Int, MutableMap<Int, RuleEndStatus>> = HashMap()
    private val candidates = CandidatesCollection()

    companion object {
        private val followSetsByATN: MutableMap<String, FollowSetsPerState> = HashMap()
    }

    /**
     * This is the main entry point. The caret token index specifies the token stream index for the token which currently
     * covers the caret (or any other position you want to get code completion candidates for).
     * Optionally you can pass in a parser rule context which limits the ATN walk to only that or called rules. This can significantly
     * speed up the retrieval process but might miss some candidates (if they are outside of the given context).
     */
    fun collectCandidates(caretTokenIndex: Int, context: ParserRuleContext? = null): CandidatesCollection {
        this.shortcutMap.clear()
        this.candidates.rules.clear()
        this.candidates.tokens.clear()
        this.statesProcessed = 0

        this.tokenStartIndex = context?.start?.tokenIndex ?: 0
        val tokenStream: TokenStream = this.parser.inputStream

        tokenStream.seek(this.tokenStartIndex)
        this.tokens = LinkedList()
        var offset = 1
        var exit = false
        while (!exit) {
            val token = tokenStream.LT(offset++)
            this.tokens.add(token.type)
            if (token.tokenIndex >= caretTokenIndex || token.type == Token.EOF) {
                exit = true
            }
        }
        val currentIndex = tokenStream.index()
        if (currentIndex == -1) {
            throw RuntimeException("CurrentIndex should be not -1")
        }
        tokenStream.seek(currentIndex)

        val callStack: MutableList<Int> = LinkedList()
        val startRule = context?.ruleIndex ?: 0
        this.processRule(this.atn.ruleToStartState[startRule], 0, callStack, "")

        if (this.showResult) {
            println("States processed: $statesProcessed")
        }

        if (this.showResult) {
            println("\n\nCollected rules:\n")
            for (rule in this.candidates.rules) {
                var path = ""
                for (token in rule.value) {
                    path += this.ruleNames[token] + " "
                }
                println(this.ruleNames[rule.key] + ", path: " + path)
            }

            var sortedTokens: MutableSet<String> = HashSet()
            for (token in this.candidates.tokens) {
                var value: String = this.vocabulary.getDisplayName(token.key)
                for (following in token.value)
                value += " " + this.vocabulary.getDisplayName(following)
                sortedTokens.add(value);
            }

            println("\n\nCollected tokens:\n")
            for (symbol in sortedTokens) {
                println(symbol)
            }
            println("\n\n")
        }

        return this.candidates
    }

    /**
     * Check if the predicate associated with the given transition evaluates to true.
     */
    private fun checkPredicate(transition: PredicateTransition): Boolean {
        return transition.predicate.eval(this.parser, ParserRuleContext.EMPTY)
    }

    private val myl = LinkedList<String>()

    /**
     * Walks the rule chain upwards to see if that matches any of the preferred rules.
     * If found, that rule is added to the collection candidates and true is returned.
     */
    private fun translateToRuleIndex(ruleStack: RuleList): Boolean {
        if (this.preferredRules.size == 0) {
            return false
        }

        // Loop over the rule stack from highest to lowest rule level. This way we properly handle the higher rule
        // if it contains a lower one that is also a preferred rule.

        for (i in ruleStack.indices) {
            if (this.preferredRules.contains(ruleStack[i])) {
                // Add the rule to our candidates list along with the current rule path,
                // but only if there isn't already an entry like that.
                var path = ruleStack.subList(0, i)
                var addNew = true
                for (rule in this.candidates.rules) {
                    if (rule.key != ruleStack[i] || rule.value.size != path.size)
                        continue
                    // Found an entry for this rule. Same path? If so don't add a new (duplicate) entry.
                    var found = false
                    path.forEachIndexed { j, v -> if (v == rule.value[j]) found = true }
                    if (found) {
                        addNew = false
                        break
                    }
                }

                if (addNew) {
                    this.candidates.rules[ruleStack[i]] = path
                    if (this.showDebugOutput) {
                        println("=====> collected: ${this.ruleNames[i]}")
                        myl.add(this.ruleNames[i])
                        if (myl.filter { it == this.ruleNames[i] }.size > 10) {
                            throw RuntimeException("LOOP")
                        }
                    }
                }
                return true
            }
        }

        return false
    }

    /**
     * This method follows the given transition and collects all symbols within the same rule that directly follow it
     * without intermediate transitions to other rules and only if there is a single symbol for a transition.
     */
    private fun getFollowingTokens(transition: Transition): TokenList {
        var result = LinkedList<Int>()

        var pipeline: MutableList<ATNState> = LinkedList<ATNState>()
        pipeline.add(transition.target)

        while (pipeline.size > 0) {
            var state = pipeline.removeAt(pipeline.size - 1)

            state.transitions
                    .filter { it.serializationType == Transition.ATOM }
                    .forEach {
                        if (!it.isEpsilon) {
                            val list = it.label().toList()
                            if (list.size == 1 && !this.ignoredTokens.contains(list[0])) {
                                result.push(list[0]);
                                pipeline.add(it.target);
                            }
                        } else {
                            pipeline.add(it.target);
                        }
                    }
        }

        return result;
    }

    /**
     * Entry point for the recursive follow set collection function.
     */
    private fun determineFollowSets(start: ATNState, stop: ATNState): MutableList<FollowSetWithPath> {
        val result: MutableList<FollowSetWithPath> = LinkedList()
        val seen: MutableSet<ATNState> = HashSet()
        val ruleStack: MutableList<Int> = LinkedList()
        this.collectFollowSets(start, stop, result, seen, ruleStack)

        return result
    }

    /**
     * Collects possible tokens which could be matched following the given ATN state. This is essentially the same
     * algorithm as used in the LL1Analyzer class, but here we consider predicates also and use no parser rule context.
     */
    private fun collectFollowSets(s: ATNState, stopState: ATNState, followSets: MutableList<FollowSetWithPath>, seen: MutableSet<ATNState>, ruleStack: MutableList<Int>) {

        if (seen.contains(s)) {
            return
        }

        seen.add(s)

        if (s == stopState || s.stateType == ATNState.RULE_STOP) {
            val set = FollowSetWithPath()
            set.intervals = IntervalSet.of(Token.EPSILON)
            set.path = LinkedList(ruleStack)
            followSets.add(set)
            return
        }

        for (transition in s.transitions) {
            if (transition.serializationType == Transition.RULE) {
                val ruleTransition: RuleTransition = transition as RuleTransition
                if (ruleStack.indexOf(ruleTransition.target.ruleIndex) != -1) {
                    continue
                }

                ruleStack.add(ruleTransition.target.ruleIndex);
                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
                ruleStack.pop()

            } else if (transition.serializationType == Transition.PREDICATE) {
                if (this.checkPredicate(transition as PredicateTransition))
                    this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
            } else if (transition.isEpsilon) {
                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
            } else if (transition.serializationType == Transition.WILDCARD) {
                val set = FollowSetWithPath()
                set.intervals = IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType);
                set.path = LinkedList(ruleStack)
                followSets.add(set)
            } else {
                var label = transition.label()
                if (label.intervals.isNotEmpty()) {
                    if (transition.serializationType == Transition.NOT_SET) {
                        label = label.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                    }
                    val set = FollowSetWithPath()
                    set.intervals = label;
                    set.path = LinkedList(ruleStack)
                    set.following = this.getFollowingTokens(transition)
                    followSets.add(set)
                }
            }
        }
    }

    /**
     * Walks the ATN for a single rule only. It returns the token stream position for each path that could be matched in this rule.
     * The result can be empty in case we hit only non-epsilon transitions that didn't match the current input or if we
     * hit the caret position.
     */
    private fun processRule(startState: ATNState, tokenIndex: Int, callStack: MutableList<Int>, _indentation: String): RuleEndStatus {
        var indentation : String = _indentation

        // Start with rule specific handling before going into the ATN walk.

        // Check first if we've taken this path with the same input before.
        var positionMap = this.shortcutMap[startState.ruleIndex]
        if (positionMap == null) {
            positionMap = HashMap()
            this.shortcutMap[startState.ruleIndex] = positionMap
        } else {
            if (positionMap.contains(tokenIndex)) {
                if (this.showDebugOutput) {
                    println("=====> shortcut")
                }
                return positionMap[tokenIndex]!!
            }
        }

        var result: RuleEndStatus = HashSet()

        // For rule start states we determine and cache the follow set, which gives us 3 advantages:
        // 1) We can quickly check if a symbol would be matched when we follow that rule. We can so check in advance
        //    and can save us all the intermediate steps if there is no match.
        // 2) We'll have all symbols that are collectable already together when we are at the caret when entering a rule.
        // 3) We get this lookup for free with any 2nd or further visit of the same rule, which often happens
        //    in non trivial grammars, especially with (recursive) expressions and of course when invoking code completion
        //    multiple times.
        var setsPerState = CodeCompletionCore.followSetsByATN[this.parser.javaClass.simpleName]
        if (setsPerState == null) {
            setsPerState = HashMap()
            CodeCompletionCore.followSetsByATN[this.parser.javaClass.simpleName] = setsPerState
        }

        var followSets = setsPerState[startState.stateNumber]
        if (followSets == null) {
            followSets = FollowSetsHolder();
            setsPerState.set(startState.stateNumber, followSets)
            val stop = this.atn.ruleToStopState[startState.ruleIndex]
            followSets.sets = this.determineFollowSets(startState, stop)

            // Sets are split by path to allow translating them to preferred rules. But for quick hit tests
            // it is also useful to have a set with all symbols combined.
            val combined = IntervalSet()
            for (set in followSets.sets)
            combined.addAll(set.intervals)
            followSets.combined = combined
        }

        callStack.push(startState.ruleIndex)
        var currentSymbol = this.tokens[tokenIndex]

        if (tokenIndex >= this.tokens.size - 1) { // At caret?
            if (this.preferredRules.contains(startState.ruleIndex)) {
                // No need to go deeper when collecting entries and we reach a rule that we want to collect anyway.
                this.translateToRuleIndex(callStack)
            } else {
                // Convert all follow sets to either single symbols or their associated preferred rule and add
                // the result to our candidates list.
                for (set in followSets.sets) {
                    var fullPath = LinkedList(callStack)
                    fullPath.addAll(set.path)
                    if (!this.translateToRuleIndex(fullPath)) {
                        for (symbol in set.intervals!!.toList())
                        if (!this.ignoredTokens.contains(symbol)) {
                            if (this.showDebugOutput) {
                                println("=====> collected: ${this.vocabulary.getDisplayName(symbol)}")
                            }
                            if (!this.candidates.tokens.contains(symbol)) {
                                this.candidates.tokens[symbol] = set.following; // Following is empty if there is more than one entry in the set.
                            } else {
                                // More than one following list for the same symbol.
                                if (this.candidates.tokens[symbol] != set.following) {
                                    this.candidates.tokens[symbol] = LinkedList()
                                }
                            }
                        }
                    }
                }
            }

            callStack.pop()
            return result

        } else {
            // Process the rule if we either could pass it without consuming anything (epsilon transition)
            // or if the current input symbol will be matched somewhere after this entry point.
            // Otherwise stop here.
            if (!followSets.combined!!.contains(Token.EPSILON) && !followSets.combined!!.contains(currentSymbol)) {
                callStack.pop()
                return result
            }
        }

        // The current state execution pipeline contains all yet-to-be-processed ATN states in this rule.
        // For each such state we store the token index + a list of rules that lead to it.
        val statePipeline = LinkedList<PipelineEntry>()
        var currentEntry : PipelineEntry?

        // Bootstrap the pipeline.
        statePipeline.push(PipelineEntry(startState, tokenIndex ))

        val processed = LinkedList<PipelineEntry>()
        pipelineLoop@ while (statePipeline.size > 0) {
            if (statePipeline.size > 1000) {
                throw RuntimeException("State pipeline way too big")
            }
            currentEntry = statePipeline.pop()
            if (processed.contains(currentEntry)) {
                continue
            } else {
                processed.add(currentEntry)
            }
            ++this.statesProcessed

            currentSymbol = this.tokens[currentEntry.tokenIndex]

            val atCaret = currentEntry!!.tokenIndex >= this.tokens.size - 1
            if (this.showDebugOutput) {
                this.printDescription(indentation, currentEntry.state, this.generateBaseDescription(currentEntry.state), currentEntry.tokenIndex)
                if (this.showRuleStack)
                    this.printRuleState(callStack)
            }

            when (currentEntry.state.stateType) {
                ATNState.RULE_START -> { // Happens only for the first state in this rule, not subrules.
                    indentation += "  "
                }
                ATNState.RULE_STOP -> {
                    // Record the token index we are at, to report it to the caller.
                    result.add(currentEntry.tokenIndex)
                    continue@pipelineLoop
                }
            }

            val transitions = currentEntry.state.transitions
            myFor@ for (transition in transitions) {
                when (transition.serializationType) {
                    Transition.RULE -> {
                        val endStatus = this.processRule(transition.target, currentEntry.tokenIndex, callStack, indentation)
                        for (position in endStatus) {
                           statePipeline.push(PipelineEntry((transition as RuleTransition).followState, position ))
                        }
                    }

                    Transition.PREDICATE -> {
                        if (this.checkPredicate(transition as PredicateTransition)) {
                            statePipeline.push(PipelineEntry(transition.target, currentEntry.tokenIndex ))
                        }
                    }

                    Transition.WILDCARD -> {
                        if (atCaret) {
                            if (!this.translateToRuleIndex(callStack)) {
                                IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType).toList()
                                        .filterNot { this.ignoredTokens.contains(it) }
                                        .forEach { this.candidates.tokens[it] = LinkedList() }
                            }
                        } else {
                            statePipeline.push(PipelineEntry(transition.target, currentEntry.tokenIndex + 1 ))
                        }
                    }

                    else -> {
                        if (transition.isEpsilon) {
                            // Jump over simple states with a single outgoing epsilon transition.
                            statePipeline.push(PipelineEntry(transition.target, currentEntry.tokenIndex))
                            continue@myFor
                        }

                        var set = transition.label()
                        if (set.intervals.isNotEmpty()) {
                            if (transition.serializationType == Transition.NOT_SET) {
                                set = set.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                            }
                            if (atCaret) {
                                if (!this.translateToRuleIndex(callStack)) {
                                    val list = set.toList()
                                    val addFollowing = list.size == 1
                                    for (symbol in list)
                                    if (!this.ignoredTokens.contains(symbol)) {
                                        if (this.showDebugOutput)
                                            println("=====> collected: ${this.vocabulary.getDisplayName(symbol)}")

                                        if (addFollowing) {
                                            this.candidates.tokens[symbol] = this.getFollowingTokens(transition)
                                        } else {
                                            this.candidates.tokens[symbol] = LinkedList()
                                        }
                                    }
                                }
                            } else {
                                if (set.contains(currentSymbol)) {
                                    if (this.showDebugOutput) {
                                        println("=====> consumed: ${this.vocabulary.getDisplayName(currentSymbol)}")
                                    }
                                    statePipeline.push(PipelineEntry(transition.target, currentEntry.tokenIndex + 1 ))
                                }
                            }
                        }
                    }
                }
            }
        }

        callStack.pop()

        // Cache the result, for later lookup to avoid duplicate walks.
        positionMap.set(tokenIndex, result)

        return result
    }

    private val atnStateTypeMap: List<String> = listOf(
        "invalid",
        "basic",
        "rule start",
        "block start",
        "plus block start",
        "star block start",
        "token start",
        "rule stop",
        "block end",
        "star loop back",
        "star loop entry",
        "plus loop back",
        "loop end"
    )

    private fun generateBaseDescription(state: ATNState): String {
        var stateValue = if (state.stateNumber == ATNState.INVALID_STATE_NUMBER) "Invalid" else state.stateNumber;
        return "[" + stateValue + " " + this.atnStateTypeMap[state.stateType] + "] in " + this.ruleNames[state.ruleIndex]
    }

    private fun printDescription(currentIndent: String, state: ATNState, baseDescription: String, tokenIndex: Int) {
        var output = currentIndent

        var transitionDescription = ""
        if (this.debugOutputWithTransitions) {
            for (transition in state.transitions) {
                var labels = ""
                var symbols: List<Int> = transition.label()?.toList() ?: emptyList()
                if (symbols.size > 2) {
                    // Only print start and end symbols to avoid large lists in debug output.
                    labels = this.vocabulary.getDisplayName(symbols[0]) + "src/test " + this.vocabulary.getDisplayName(symbols[symbols.size - 1])
                } else {
                    for (symbol in symbols) {
                        if (labels.isNotEmpty()) {
                            labels += ", "
                        }
                        labels += this.vocabulary.getDisplayName(symbol)
                    }
                }
                if (labels.isEmpty()) {
                    labels = "ε"
                }
                transitionDescription += "\n" + currentIndent + "\t(" + labels + ") " + "[" + transition.target.stateNumber + " " +
                        this.atnStateTypeMap[transition.target.stateType] + "] in " + this.ruleNames[transition.target.ruleIndex]
            }
        }

        if (tokenIndex >= this.tokens.size - 1) {
            output += "<<" + this.tokenStartIndex + tokenIndex + ">> "
        } else {
            output += "<" + this.tokenStartIndex + tokenIndex + "> "
        }
        println(output + "Current state: " + baseDescription + transitionDescription)
    }

    private fun printRuleState(stack: List<Int>) {
        if (stack.isEmpty()) {
            println("<empty stack>")
            return
        }

        for (rule in stack) {
            println(this.ruleNames[rule])
        }
    }

}

//
// Utils
//

private fun <E> MutableList<E>.push(element: E) {
    this.add(element)
}

private fun <E> MutableList<E>.pop() : E {
    val initialSize = this.size
    val el = this.removeAt(this.size - 1)
    val afterSize = this.size
    if (afterSize != (initialSize - 1)) {
        throw RuntimeException()
    }
    return el
}
